"""Fixture transport tests exercise real bounded Agent code, not live DeepSeek."""
import json
import unittest
import httpx

try:
    from ready_gym_foundation import FoundationAgent, FoundationProvider
except ModuleNotFoundError:
    FoundationAgent = FoundationProvider = None


def tool_completion(name, arguments):
    return {"id": "fixture-agent-1", "choices": [{"finish_reason": "tool_calls", "message": {
        "role": "assistant", "content": None, "tool_calls": [{"id": "call_fixture_1", "type": "function",
        "function": {"name": name, "arguments": json.dumps(arguments)}}]
    }}], "usage": {"prompt_tokens": 80, "completion_tokens": 20, "total_tokens": 100}}


class FoundationAgentContract(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        import asyncio
        asyncio.get_running_loop().slow_callback_duration = 1

    async def test_confirm_profile_holds_session_lock_before_first_business_await(self):
        import asyncio
        from ready_gym_intake import create_app, DeepSeekAdapter, Session
        from ready_gym_foundation import FoundationProvider
        import hashlib, time
        entered, release = asyncio.Event(), asyncio.Event()
        class BlockingBusiness:
            async def call(self, path, payload=None, method="POST"):
                if path == "/internal/profile/confirm":
                    entered.set()
                    await release.wait()
                if path == "/internal/tools/propose_plan":
                    return {"id": "fixture-lock-draft", "revision": 1, "status": "draft"}
                return {"profile": {}, "active_plan": None, "plan_proposal": None, "safety_route": None}
        async def upstream(request):
            return httpx.Response(200, json=tool_completion("propose_plan", {}))
        async with httpx.AsyncClient(transport=httpx.MockTransport(upstream)) as agent_client:
            app = create_app(provider=DeepSeekAdapter("fixture-only"), business=BlockingBusiness(),
                agent_provider=FoundationProvider("fixture-only", client=agent_client))
            sid, token = "concurrent_fixture", "concurrent-fixture-token"
            app.state.sessions[sid] = Session(token_digest=hashlib.sha256(token.encode()).hexdigest(),
                profile={"age":21,"height_cm":175.,"weight_kg":70.,"goal":"muscle","location":"home",
                    "equipment":["dumbbell"],"days_per_week":3,"minutes_per_session":30,"experience":"beginner",
                    "current_discomfort":"none","medical_restrictions":"none","gender":None}, revision=1,expires_at=time.monotonic()+60)
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://fixture") as client:
                path=f"/api/intake/sessions/{sid}/foundation/confirm-profile"
                headers={"Authorization":"Bearer "+token};payload={"confirmed":True,"expected_revision":1}
                first=asyncio.create_task(client.post(path, headers=headers, json=payload))
                await entered.wait()
                self.assertTrue(app.state.sessions[sid].in_flight,"Concurrent confirmation must be blocked before a Java/model request starts")
                try:
                    two=await asyncio.wait_for(client.post(path, headers=headers, json=payload), timeout=1)
                    self.assertEqual(two.status_code,409)
                finally:
                    release.set()
                one=await first

    async def test_unknown_demo_access_code_is_rejected_before_a_paid_request(self):
        import importlib.util
        self.assertIsNotNone(importlib.util.find_spec("ready_gym_demo"), "Private Demo access gate/runtime factory is missing")
        from ready_gym_demo import secure_demo_app
        from ready_gym_intake import create_app, DeepSeekAdapter
        calls = []
        async def respond(request):
            calls.append(request)
            return httpx.Response(500)
        async with httpx.AsyncClient(transport=httpx.MockTransport(respond)) as upstream:
            app = secure_demo_app(create_app(provider=DeepSeekAdapter("fixture-only", client=upstream)), "fixture-demo-code-01234567890123456789")
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://fixture") as client:
                for headers in ({}, {"X-Ready-Gym-Demo": "wrong"}):
                    reply = await client.post("/api/intake/sessions", json={"consent": True}, headers=headers)
                    self.assertEqual(reply.status_code, 401)
        self.assertEqual(calls, [], "An internet visitor must not spend the supplied model budget")

    async def test_initial_unknown_field_clarification_is_not_invented_profile(self):
        from ready_gym_intake import create_app, DeepSeekAdapter
        async def upstream(request):
            envelope = {"assistant_text": "请先告诉我年龄", "profile_patch": {}, "clarification_fields": ["age"]}
            return httpx.Response(200, json={"choices": [{"finish_reason": "stop", "message": {
                "content": json.dumps(envelope)}}], "usage": {"prompt_tokens": 20, "completion_tokens": 20, "total_tokens": 40}})
        async with httpx.AsyncClient(transport=httpx.MockTransport(upstream)) as provider_client:
            app = create_app(provider=DeepSeekAdapter("fixture-only-not-secret", client=provider_client))
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://fixture") as client:
                reply = await client.post("/api/intake/sessions", json={"consent": True})
        self.assertEqual(reply.status_code, 201, "A model asking about a genuinely absent field must not reject the first question")
        self.assertIsNone(reply.json()["validated_profile"]["age"])
        self.assertEqual(reply.json()["status"], "collecting")

    async def test_unproven_revision_arguments_never_reach_java(self):
        self.assertIsNotNone(FoundationAgent)
        async def upstream(request):
            return httpx.Response(200, json=tool_completion("propose_revision", {"expected_revision": 1, "days_per_week": 4}))
        calls = []
        async def execute(name, arguments):
            calls.append((name, arguments))
            return {"id": "revision-fixture", "revision": 2, "status": "draft"}
        async with httpx.AsyncClient(transport=httpx.MockTransport(upstream)) as client:
            result = await FoundationAgent(FoundationProvider("fixture-only", client=client), execute).run(
                "session_fixture_1", "我想看看计划", {"active_plan": {"revision": 1}})
        self.assertEqual(calls, [], "The provider cannot invent a four-day change absent user evidence")
        self.assertIsNone(result.proposal)
        self.assertEqual(result.trace, [{"name": "propose_revision", "status": "rejected"}])

    async def test_confirm_profile_runs_real_registry_and_returns_unconfirmed_plan_inline(self):
        from ready_gym_intake import create_app, DeepSeekAdapter
        profile = {"age": 21, "height_cm": 175, "weight_kg": 70, "gender": None,
            "goal": "muscle", "location": "home", "equipment": ["dumbbell"], "days_per_week": 3,
            "minutes_per_session": 30, "experience": "beginner", "current_discomfort": "none", "medical_restrictions": "none"}
        business_calls = []
        proposal = {"id": "draft-fixture-1", "revision": 1, "status": "draft"}
        class FixtureBusiness:
            async def call(self, path, payload=None, method="POST"):
                business_calls.append((path, payload))
                if path == "/internal/tools/propose_plan":
                    return proposal
                return {"profile": profile, "profile_revision": 1, "active_plan": None, "plan_proposal": proposal if len(business_calls) > 2 else None, "safety_route": None}
        async def intake_upstream(request):
            return httpx.Response(200, json={"choices": [{"finish_reason": "stop", "message": {
                "content": json.dumps({"assistant_text": "请提供资料", "profile_patch": {}})}}],
                "usage": {"prompt_tokens": 20, "completion_tokens": 20, "total_tokens": 40}})
        async def agent_upstream(request):
            return httpx.Response(200, json=tool_completion("propose_plan", {}))
        async with httpx.AsyncClient(transport=httpx.MockTransport(intake_upstream)) as pi, httpx.AsyncClient(transport=httpx.MockTransport(agent_upstream)) as pa:
            import inspect
            self.assertIn("business", inspect.signature(create_app).parameters, "Intake has no foundation confirmation/tools integration")
            app = create_app(provider=DeepSeekAdapter("fixture-only", client=pi), business=FixtureBusiness(),
                agent_provider=FoundationProvider("fixture-only", client=pa))
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://fixture") as client:
                started = (await client.post("/api/intake/sessions", json={"consent": True})).json()
                sid = started["session_id"]
                session = app.state.sessions[sid]
                session.profile, session.revision = profile, 1
                headers = {"Authorization": "Bearer " + started["session_token"]}
                reply = await client.post(f"/api/intake/sessions/{sid}/foundation/confirm-profile",
                    json={"confirmed": True, "expected_revision": 1}, headers=headers)
        self.assertEqual(reply.status_code, 200)
        self.assertTrue(reply.json()["foundation_enabled"])
        self.assertEqual(reply.json()["plan_proposal"]["status"], "draft")
        self.assertIsNone(reply.json()["active_plan"])
        self.assertEqual(reply.json()["tool_trace"], [{"name": "propose_plan", "status": "ok"}])
        self.assertNotIn("/internal/plan/confirm", [c[0] for c in business_calls])

    async def test_model_selects_tool_but_cannot_commit_business_state(self):
        self.assertIsNotNone(FoundationAgent, "Bounded tool-calling foundation agent is not implemented")
        requests = []
        async def upstream(request):
            requests.append(json.loads(request.content))
            return httpx.Response(200, json=tool_completion("propose_plan", {}))
        async with httpx.AsyncClient(transport=httpx.MockTransport(upstream)) as client:
            provider = FoundationProvider("fixture-only-not-secret", client=client)
            calls = []
            async def execute(name, arguments):
                calls.append((name, arguments))
                return {"id": "plan-fixture-1", "revision": 1, "status": "draft"}
            result = await FoundationAgent(provider, execute).run(
                "session_fixture_1", "用户已明确确认档案，请提出一周计划", {"profile_confirmed": True})
        self.assertEqual(calls, [("propose_plan", {"session_id": "session_fixture_1"})])
        self.assertEqual(result.proposal["status"], "draft")
        tool_names = {t["function"]["name"] for t in requests[0]["tools"]}
        self.assertIn("propose_plan", tool_names)
        self.assertNotIn("confirm_plan", tool_names)
        self.assertNotIn("save_profile", tool_names)
        self.assertEqual(result.source, "deepseek_tool_calls")
        self.assertEqual(result.usage["total_tokens"], 100)
        self.assertEqual(len(requests), 1)
