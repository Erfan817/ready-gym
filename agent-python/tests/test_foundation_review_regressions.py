"""Third-review behavioral ASGI regressions; isolated transports, no live calls."""
import asyncio
import copy
import unittest

import httpx
import test_foundation_routes as routes
from test_foundation import tool_completion

PROFILE, DRAFT = routes.PROFILE, routes.DRAFT


class FoundationReviewRegressions(unittest.IsolatedAsyncioTestCase):
    asyncSetUp = routes.FoundationRoutes.asyncSetUp

    async def test_enabled_foundation_rejects_legacy_messages_before_risk_or_profile_mutation(self):
        attempts = self.session.attempts
        before = copy.deepcopy(self.business.state)
        for message in ("我现在膝盖疼", "年龄撤回，仍待核实", "当前不适还不确定"):
            with self.subTest(message=message):
                reply = await self.client.post(f"/api/intake/sessions/{self.sid}/messages",
                    headers=self.headers, json={"message": message})
                self.assertEqual(reply.status_code, 409, reply.text)
                self.assertEqual(reply.json()["error"]["code"], "foundation_required")
                self.assertEqual(self.session.profile, PROFILE)
                self.assertTrue(self.session.confirmed)
                self.assertEqual(self.session.attempts, attempts)
                self.assertEqual(self.business.state, before)
                self.assertEqual(self.business.calls, [])
        self.assertEqual(self.agent_calls, [])

    async def test_enabled_foundation_rejects_legacy_confirm_after_profile_revocation(self):
        # A revoked Python profile must not restore confirmation over an old Java draft.
        self.session.confirmed = False
        before = copy.deepcopy(self.business.state)
        reply = await self.client.post(f"/api/intake/sessions/{self.sid}/confirm", headers=self.headers,
            json={"confirmed": True, "expected_revision": self.session.revision})
        self.assertEqual(reply.status_code, 409, reply.text)
        self.assertEqual(reply.json()["error"]["code"], "foundation_required")
        self.assertFalse(self.session.confirmed)
        self.assertEqual(self.business.state, before)
        self.assertEqual(self.business.calls, [])
        confirm_plan = await self.client.post(self.root + "/confirm-plan", headers=self.headers,
            json={"confirmed": True, "draft_id": DRAFT["id"], "expected_revision": 1})
        self.assertEqual(confirm_plan.status_code, 409)
        self.assertEqual(self.business.state, before)
        self.assertEqual(self.agent_calls, [])

    async def test_nonfoundation_session_keeps_legacy_message_and_confirm_behavior(self):
        self.session.foundation_enabled = False
        self.session.confirmed = False
        reply = await self.client.post(f"/api/intake/sessions/{self.sid}/messages", headers=self.headers,
            json={"message": "查看我的资料"})
        self.assertEqual(reply.status_code, 200, reply.text)
        self.assertEqual(reply.json()["status"], "ready")
        confirm = await self.client.post(f"/api/intake/sessions/{self.sid}/confirm", headers=self.headers,
            json={"confirmed": True, "expected_revision": reply.json()["revision"]})
        self.assertEqual(confirm.status_code, 200, confirm.text)
        self.assertTrue(confirm.json()["confirmed"])
        self.assertEqual(self.business.calls, [])
        self.assertEqual(self.agent_calls, [])

    async def test_message_lock_survives_snapshot_until_final_readback(self):
        entered, release = asyncio.Event(), asyncio.Event()
        observed = []
        original = self.business.call
        snapshots = 0
        async def blocking_business(path, payload=None, method="POST"):
            nonlocal snapshots
            observed.append((path, self.session.in_flight))
            if path == "/internal/tools/get_profile":
                snapshots += 1
                if snapshots == 1:
                    entered.set()
                    await release.wait()
            return await original(path, payload, method)
        self.business.call = blocking_business
        first = asyncio.create_task(self.client.post(self.root + "/messages", headers=self.headers,
            json={"message": "周三没空，改成四天"}))
        try:
            await asyncio.wait_for(entered.wait(), timeout=2)
            locked_during_snapshot = self.session.in_flight
            concurrent = await self.client.post(self.root + "/messages", headers=self.headers,
                json={"message": "我现在胸痛"})
        finally:
            release.set()
            completed = await asyncio.wait_for(first, timeout=2)
        self.assertTrue(locked_during_snapshot, "Lock must not drop between cancel and Agent snapshot")
        self.assertEqual(concurrent.status_code, 409, concurrent.text)
        self.assertEqual(concurrent.json()["error"]["code"], "session_busy")
        self.assertEqual(completed.status_code, 200, completed.text)
        self.assertTrue(all(locked for _, locked in observed), observed)
        self.assertFalse(self.session.in_flight)
        self.assertEqual(len(self.agent_calls), 1)

    async def test_latest_business_stop_blocks_provider_and_preserves_active(self):
        self.business.state["active_plan"] = {**DRAFT, "status": "confirmed"}
        active = copy.deepcopy(self.business.state["active_plan"])
        self.session.foundation_state = copy.deepcopy(self.business.state)
        original = self.business.call
        async def safety_at_snapshot(path, payload=None, method="POST"):
            if path == "/internal/tools/get_profile":
                # Risk is persisted by business while the Python read awaits.
                self.business.state["safety_route"] = "stop_and_seek_care"
                self.business.state["plan_proposal"] = None
            return await original(path, payload, method)
        self.business.call = safety_at_snapshot
        attempts = self.session.attempts
        reply = await self.client.post(self.root + "/messages", headers=self.headers,
            json={"message": "周三没空，改成四天"})
        self.assertEqual(self.agent_calls, [], "Snapshot STOP must be checked before any paid provider call")
        self.assertEqual(reply.status_code, 409, reply.text)
        self.assertEqual(reply.json()["error"]["code"], "safety_blocked")
        self.assertEqual(self.session.attempts, attempts)
        self.assertFalse(self.session.in_flight)
        reread = await self.client.get(self.root, headers=self.headers)
        self.assertEqual(reread.json()["safety_route"], "stop_and_seek_care")
        self.assertIsNone(reread.json()["active_plan"])
        self.assertIsNone(reread.json()["plan_proposal"])
        self.assertEqual(self.business.state["active_plan"], active)
        self.assertNotIn("/internal/tools/propose_revision", [c[0] for c in self.business.calls])

    async def test_provider_rounds_and_tool_execution_hold_lock_and_recheck_risk(self):
        active = {**DRAFT, "status": "confirmed"}
        self.business.state["active_plan"] = active
        self.session.foundation_state = copy.deepcopy(self.business.state)
        observations = []
        async def provider(request):
            observations.append(self.session.in_flight)
            self.agent_calls.append(request)
            return httpx.Response(200, json=tool_completion("get_profile", {}))
        self.pa._transport = httpx.MockTransport(provider)
        original = self.business.call
        snapshots = 0
        async def business(path, payload=None, method="POST"):
            nonlocal snapshots
            observations.append(self.session.in_flight)
            if path == "/internal/tools/get_profile":
                snapshots += 1
                if snapshots == 2:
                    self.business.state["safety_route"] = "professional_review"
                    self.business.state["plan_proposal"] = None
            return await original(path, payload, method)
        self.business.call = business
        attempts = self.session.attempts
        reply = await self.client.post(self.root + "/messages", headers=self.headers,
            json={"message": "查看已确认档案"})
        self.assertEqual(len(self.agent_calls), 1, "Risk returned by a read tool must stop the next model round")
        self.assertEqual(reply.status_code, 409, reply.text)
        self.assertEqual(reply.json()["error"]["code"], "safety_blocked")
        self.assertTrue(all(observations), observations)
        self.assertEqual(self.session.attempts, attempts + 1)
        self.assertFalse(self.session.in_flight)
        self.assertEqual(self.business.state["active_plan"], active)

    def malformed_replies(self):
        malformed_json = tool_completion("propose_plan", {})
        malformed_json["choices"][0]["message"]["tool_calls"][0]["function"]["arguments"] = "{"
        return (
            ("incomplete_tool_json", malformed_json),
            ("unhashable_equipment", tool_completion("propose_revision", {"expected_revision": 1, "equipment": [{}]})),
            ("unhashable_days", tool_completion("propose_revision", {"expected_revision": 1, "unavailable_days": [[]]})),
            ("boolean_revision", tool_completion("propose_revision", {"expected_revision": True, "days_per_week": 4})),
            ("duplicate_equipment", tool_completion("propose_revision", {"expected_revision": 1, "equipment": ["dumbbell", "dumbbell"]})),
            ("wrong_muscle_type", tool_completion("search_exercises", {"muscle": {}})),
            ("unsupported_muscle", tool_completion("search_exercises", {"muscle": "forehead"})),
            ("missing_revision", tool_completion("propose_revision", {"days_per_week": 4})),
            ("incomplete_body", "{"),
        )

    async def assert_invalid_reply_policy(self, *, has_active):
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=self.app, raise_app_exceptions=False),
                                    base_url="http://fixture") as client:
            for label, body in self.malformed_replies():
                with self.subTest(case=label, active=has_active):
                    active = {**DRAFT, "status": "confirmed"} if has_active else None
                    self.business.state = {"profile": copy.deepcopy(PROFILE), "profile_revision": 1,
                        "active_plan": active, "plan_proposal": copy.deepcopy(DRAFT), "safety_route": None}
                    self.session.foundation_state = copy.deepcopy(self.business.state)
                    self.session.attempts = 1
                    self.session.usage = {"prompt_tokens": 20, "completion_tokens": 10, "total_tokens": 30}
                    self.business.calls.clear()
                    self.agent_calls.clear()
                    async def malformed(request):
                        self.agent_calls.append(request)
                        return httpx.Response(200, content=body) if type(body) is str else httpx.Response(200, json=body)
                    self.pa._transport = httpx.MockTransport(malformed)
                    reply = await client.post(self.root + "/messages", headers=self.headers,
                        json={"message": "改成四天，只有哑铃，周三没空"})
                    self.assertEqual(reply.status_code, 200, f"{label}: {reply.text}")
                    data = reply.json()
                    if has_active:
                        self.assertEqual(data["agent_source"], "server_policy")
                        self.assertIsNone(data["plan_proposal"])
                        self.assertEqual(data["active_plan"], active)
                        self.assertIn("未修改当前计划", data["agent_notice"])
                    else:
                        self.assertEqual(data["agent_source"], "rule_fallback")
                        self.assertEqual(data["plan_proposal"]["source"], "rule_fallback")
                        self.assertIn("未经专业审核", data["agent_notice"])
                        self.assertIsNone(data["active_plan"])
                    self.assertFalse(self.session.in_flight)
                    self.assertEqual(len(self.agent_calls), 1, "No automatic paid retry after invalid responses")
                    self.assertEqual(self.business.state["active_plan"], active)
                    proposed = [c for c in self.business.calls if c[0].startswith("/internal/tools/")
                                and c[0] != "/internal/tools/get_profile"]
                    self.assertEqual(proposed, [] if has_active else [
                        ("/internal/tools/propose_plan", {"session_id": self.sid, "source": "rule_fallback"}, "POST")])
                    self.assertNotIn("/internal/plan/confirm", [c[0] for c in self.business.calls])
                    reread = await client.get(self.root, headers=self.headers)
                    self.assertEqual(reread.json()["active_plan"], data["active_plan"])
                    self.assertEqual(reread.json()["plan_proposal"], data["plan_proposal"])

    async def test_intake_correction_retains_outer_message_lock(self):
        original = self.app.state.process_intake_turn
        observed = []
        async def intake_with_readback(session, message):
            await original(session, message)
            observed.append(session.in_flight)
            await asyncio.sleep(0)
        self.app.state.process_intake_turn = intake_with_readback
        reply = await self.client.post(self.root + "/messages", headers=self.headers,
            json={"message": "年龄撤回，仍待核实"})
        self.assertEqual(reply.status_code, 200, reply.text)
        self.assertEqual(observed, [True], "Nested intake must not release its Foundation caller's lock")
        self.assertFalse(reply.json()["confirmed"])
        self.assertIsNone(reply.json()["validated_profile"]["age"])
        self.assertIsNone(self.business.state["plan_proposal"])
        self.assertFalse(self.session.in_flight)

    async def test_invalid_tool_arguments_fallback_without_active_plan(self):
        await self.assert_invalid_reply_policy(has_active=False)

    async def test_invalid_tool_arguments_preserve_active_and_revoke_old_pending(self):
        await self.assert_invalid_reply_policy(has_active=True)
