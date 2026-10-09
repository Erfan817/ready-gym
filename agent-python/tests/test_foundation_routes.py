"""ASGI public endpoints with explicit in-memory business/provider fixtures, not live proof."""
import copy
import unittest

import httpx
from ready_gym_foundation import FoundationProvider
from ready_gym_intake import create_app, DeepSeekAdapter
from test_foundation import tool_completion

PROFILE = {"age": 21, "height_cm": 175.0, "weight_kg": 70.0, "gender": None,
    "goal": "muscle", "location": "home", "equipment": ["dumbbell"], "days_per_week": 3,
    "minutes_per_session": 30, "experience": "beginner", "current_discomfort": "none", "medical_restrictions": "none"}
DRAFT = {"id": "fixture_plan_1", "revision": 1, "status": "draft"}


class FixtureBusiness:
    def __init__(self):
        self.state = {"profile": copy.deepcopy(PROFILE), "profile_revision": 1, "active_plan": None,
                      "plan_proposal": copy.deepcopy(DRAFT), "safety_route": None}
        self.calls = []
    async def call(self, path, payload=None, method="POST"):
        self.calls.append((path, copy.deepcopy(payload), method))
        if path == "/internal/plan/confirm":
            self.state["active_plan"] = {**self.state["plan_proposal"], "status": "confirmed"}
            self.state["plan_proposal"] = None
        if path == "/internal/proposal/cancel":
            self.state["plan_proposal"] = None
        if path == "/internal/tools/propose_plan":
            self.state["plan_proposal"] = {**DRAFT, "source": payload.get("source", "agent_rule_tool")}
            return copy.deepcopy(self.state["plan_proposal"])
        if path == "/internal/safety":
            self.state["safety_route"] = payload["route"]
            self.state["plan_proposal"] = None
        if path == "/internal/tools/propose_revision":
            self.state["plan_proposal"] = {**DRAFT, "id": "fixture_plan_2", "revision": 2, "based_on_revision": 1}
            return copy.deepcopy(self.state["plan_proposal"])
        return copy.deepcopy(self.state)


class FoundationRoutes(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.business = FixtureBusiness()
        async def intake(request):
            return httpx.Response(200, json={"choices": [{"finish_reason": "stop", "message": {
                "content": '{"assistant_text":"请输入资料","profile_patch":{}}'}}],
                "usage": {"prompt_tokens": 20, "completion_tokens": 10, "total_tokens": 30}})
        self.agent_calls = []
        async def agent(request):
            self.agent_calls.append(request)
            return httpx.Response(200, json=tool_completion("propose_revision",
                {"expected_revision": 1, "days_per_week": 4, "unavailable_days": [3]}))
        self.pi = httpx.AsyncClient(transport=httpx.MockTransport(intake))
        self.pa = httpx.AsyncClient(transport=httpx.MockTransport(agent))
        self.addAsyncCleanup(self.pi.aclose)
        self.addAsyncCleanup(self.pa.aclose)
        self.app = create_app(provider=DeepSeekAdapter("fixture-only", client=self.pi), business=self.business,
            agent_provider=FoundationProvider("fixture-only", client=self.pa))
        self.client = httpx.AsyncClient(transport=httpx.ASGITransport(app=self.app), base_url="http://fixture")
        self.addAsyncCleanup(self.client.aclose)
        started = (await self.client.post("/api/intake/sessions", json={"consent": True})).json()
        self.sid = started["session_id"]
        self.headers = {"Authorization": "Bearer " + started["session_token"]}
        self.root = f"/api/intake/sessions/{self.sid}/foundation"
        self.session = self.app.state.sessions[self.sid]
        self.session.profile, self.session.revision = copy.deepcopy(PROFILE), 1
        self.session.confirmed, self.session.foundation_enabled = True, True
        self.session.foundation_state = copy.deepcopy(self.business.state)

    async def test_new_uncertain_turn_invalidates_old_pending_before_model_or_quota(self):
        self.session.attempts = 12
        reply = await self.client.post(self.root + "/messages", headers=self.headers,
            json={"message": "每周四天只是朋友的安排，不代表我，先不要采用"})
        self.assertEqual(reply.status_code, 429)
        reread = await self.client.get(self.root, headers=self.headers)
        self.assertIsNone(reread.json()["plan_proposal"], "A failed uncertain modification must revoke stale pending approval")
        self.assertTrue(any(c[0] == "/internal/proposal/cancel" for c in self.business.calls))
        confirm = await self.client.post(self.root + "/confirm-plan", headers=self.headers,
            json={"confirmed": True, "draft_id": DRAFT["id"], "expected_revision": 1})
        self.assertEqual(confirm.status_code, 409)
        self.assertEqual(self.agent_calls, [])

    async def test_provider_timeout_creates_clearly_labelled_rule_fallback_without_confirm(self):
        import inspect
        from ready_gym_foundation import install_foundation_routes
        self.business.state["active_plan"], self.business.state["plan_proposal"] = None, None
        self.session.foundation_state = copy.deepcopy(self.business.state)
        async def fail(request):
            raise httpx.ReadTimeout("fixture timeout")
        self.pa._transport = httpx.MockTransport(fail)
        reply = await self.client.post(self.root + "/confirm-profile", headers=self.headers,
            json={"confirmed": True, "expected_revision": 1})
        self.assertEqual(reply.status_code, 200, "Complete confirmed profile may get a labelled rule fallback, not pretend model success")
        self.assertEqual(reply.json()["agent_source"], "rule_fallback")
        self.assertIsNone(reply.json()["active_plan"])
        self.assertNotIn("/internal/plan/confirm", [c[0] for c in self.business.calls])

    async def test_urgent_message_precedes_quota_and_blocks_old_plan_confirmation(self):
        self.business.state["active_plan"] = {**DRAFT, "status": "confirmed"}
        self.session.foundation_state = copy.deepcopy(self.business.state)
        self.session.attempts = 12
        reply = await self.client.post(self.root + "/messages", headers=self.headers,
            json={"message": "我现在胸痛，继续按这个计划练吧"})
        self.assertEqual(reply.status_code, 200)
        self.assertEqual(reply.json()["safety_route"], "stop_and_seek_care")
        self.assertIsNone(reply.json()["plan_proposal"])
        self.assertIsNone(reply.json()["active_plan"], "A stored plan cannot render as usable while safety blocks training")
        self.assertEqual(self.agent_calls, [])
        confirm = await self.client.post(self.root + "/confirm-plan", headers=self.headers,
            json={"confirmed": True, "draft_id": DRAFT["id"], "expected_revision": 1})
        self.assertEqual(confirm.status_code, 409)
        self.assertEqual(self.business.state["safety_route"], "stop_and_seek_care")

    async def test_natural_language_revision_is_pending_until_fresh_explicit_confirm(self):
        self.business.state["active_plan"] = {**DRAFT, "status": "confirmed"}
        self.business.state["plan_proposal"] = None
        self.session.foundation_state = copy.deepcopy(self.business.state)
        reply = await self.client.post(self.root + "/messages", headers=self.headers,
            json={"message": "周三没空，改成四天"})
        self.assertEqual(reply.status_code, 200, "Revision must be part of the chat, not a separate mandatory form")
        self.assertEqual(reply.json()["plan_proposal"]["revision"], 2)
        self.assertEqual(reply.json()["active_plan"]["revision"], 1)
        self.assertNotIn("/internal/plan/confirm", [c[0] for c in self.business.calls])

    async def test_only_explicit_confirmation_writes_and_get_reads_exact_active_version(self):
        denied = await self.client.post(self.root + "/confirm-plan", headers=self.headers,
            json={"confirmed": False, "draft_id": DRAFT["id"], "expected_revision": 1})
        self.assertEqual(denied.status_code, 403, "No model/tool/body default can confer user confirmation")
        self.assertEqual(len(self.business.calls), 0)
        accepted = await self.client.post(self.root + "/confirm-plan", headers=self.headers,
            json={"confirmed": True, "draft_id": DRAFT["id"], "expected_revision": 1})
        self.assertEqual(accepted.status_code, 200)
        self.assertEqual(accepted.json()["active_plan"]["status"], "confirmed")
        reread = await self.client.get(self.root, headers=self.headers)
        self.assertEqual(reread.json()["active_plan"], accepted.json()["active_plan"])
        self.assertIsNone(reread.json()["plan_proposal"])
        self.assertEqual(self.agent_calls, [], "Confirm and readback must not incur an extra provider call")
