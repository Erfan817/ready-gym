"""Real app/adapter contract tests; MockTransport is NOT live verification."""
import json
import unittest

import httpx

try:
    from ready_gym_intake import DeepSeekAdapter, Settings, create_app
except ModuleNotFoundError:
    DeepSeekAdapter = Settings = create_app = None


def completion(patch=None, text="请告诉我年龄和身高。"):
    return {"id": "contract-request-1", "choices": [{"finish_reason": "stop", "message": {
        "content": json.dumps({"assistant_text": text, "profile_patch": patch or {}}, ensure_ascii=False)
    }}], "usage": {"prompt_tokens": 100, "completion_tokens": 50, "total_tokens": 150}}


class IntakeContract(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        import asyncio
        asyncio.get_running_loop().slow_callback_duration = 1

    async def setup_app(self, replies, **limits):
        self.assertIsNotNone(create_app, "conversation-first service is not implemented")
        self.calls = []
        self.replies = list(replies)

        async def respond(request):
            self.calls.append(request)
            reply = self.replies.pop(0)
            return reply if isinstance(reply, httpx.Response) else httpx.Response(200, json=reply)

        self.upstream = httpx.AsyncClient(transport=httpx.MockTransport(respond))
        self.addAsyncCleanup(self.upstream.aclose)
        self.adapter = DeepSeekAdapter("contract-test-only", client=self.upstream)
        self.settings = Settings(**limits)
        self.app = create_app(settings=self.settings, provider=self.adapter)
        self.client = httpx.AsyncClient(transport=httpx.ASGITransport(app=self.app, raise_app_exceptions=False), base_url="http://test")
        self.addAsyncCleanup(self.client.aclose)

    async def start(self):
        response = await self.client.post("/api/intake/sessions", json={"consent": True})
        self.assertEqual(response.status_code, 201, response.text)
        data = response.json()
        self.sid = data["session_id"]
        self.headers = {"Authorization": "Bearer " + data["session_token"]}
        return data

    async def send(self, message):
        return await self.client.post(f"/api/intake/sessions/{self.sid}/messages", json={"message": message}, headers=self.headers)

    async def test_first_question_extracts_multiple_fields_and_only_asks_missing(self):
        await self.setup_app([completion(), completion({"age": 21, "height_cm": 175, "weight_kg": 70, "goal": "muscle"})])
        started = await self.start()
        self.assertEqual(started["status"], "collecting")
        self.assertIsNone(started["profile"]["current_discomfort"])
        result = await self.send("21岁，175厘米，70公斤，想增肌")
        self.assertEqual(result.status_code, 200, result.text)
        data = result.json()
        self.assertEqual(data["profile"]["age"], 21)
        self.assertEqual(data["profile"], data["validated_profile"])
        self.assertNotIn("age", data["missing_fields"])
        self.assertIn("current_discomfort", data["missing_fields"])
        self.assertIn("medical_restrictions", data["missing_fields"])
        self.assertNotIn("年龄", data["assistant_text"])
        self.assertEqual(data["provider_mode"], "deepseek_official")
        request = self.calls[-1]
        body = json.loads(request.content)
        self.assertEqual(str(request.url), "https://api.deepseek.com/chat/completions")
        self.assertEqual(body["model"], "deepseek-flash")
        self.assertEqual(body["thinking"], {"type": "disabled"})
        self.assertEqual(body["response_format"], {"type": "json_object"})
        self.assertFalse(body["stream"])
        self.assertIn("json", body["messages"][0]["content"].lower())
        self.assertEqual(data["usage"]["total_tokens"], 300)
        self.assertEqual(json.loads(body["messages"][1]["content"])["last_question_fields"], ["age", "height_cm", "weight_kg"])

    async def test_consent_and_missing_key_fail_without_fake_ai_or_calls(self):
        await self.setup_app([completion()])
        denied = await self.client.post("/api/intake/sessions", json={"consent": False})
        self.assertEqual(denied.status_code, 403)
        bad = await self.client.post("/api/intake/sessions", json={"consent": 1})
        self.assertEqual(bad.status_code, 422)
        self.assertEqual(len(self.calls), 0)
        app = create_app(settings=Settings())
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
            result = await client.post("/api/intake/sessions", json={"consent": True})
        self.assertEqual(result.status_code, 503)
        self.assertEqual(result.json()["error"]["code"], "provider_not_configured")
        self.assertNotIn("assistant_text", result.json())
        self.assertEqual(app.state.sessions, {})

    async def test_session_token_is_required_bound_to_one_session_and_delete_revokes(self):
        await self.setup_app([completion(), completion()])
        first = await self.start()
        path = f"/api/intake/sessions/{self.sid}"
        for headers in ({}, {"Authorization": "Bearer wrong"}):
            result = await self.client.post(path + "/messages", json={"message": "21岁"}, headers=headers)
            self.assertEqual(result.status_code, 401)
        second = (await self.client.post("/api/intake/sessions", json={"consent": True})).json()
        result = await self.client.post(f"/api/intake/sessions/{second['session_id']}/messages", json={"message": "21岁"}, headers=self.headers)
        self.assertEqual(result.status_code, 401)
        denied = await self.client.delete(path, headers={"Authorization": "Bearer wrong"})
        self.assertEqual(denied.status_code, 401)
        deleted = await self.client.delete(path, headers=self.headers)
        self.assertEqual(deleted.status_code, 204)
        self.assertEqual((await self.send("21岁")).status_code, 404)
        self.assertEqual(len(self.calls), 2)
        self.assertNotEqual(first["session_token"], second["session_token"])
        self.assertGreaterEqual(len(first["session_token"]), 32)
        self.assertNotIn(first["session_token"], repr(self.app.state.sessions))

    async def test_invalid_provider_patches_are_atomic_and_cannot_supply_plan_fields(self):
        import ready_gym_intake as service
        bad_patches = [
            {"age": 21, "plan": {"load": 100}}, {"age": True}, {"age": 17},
            {"height_cm": float("nan")}, {"weight_kg": float("inf")},
            {"height_cm": "175"}, {"goal": "bulk"}, {"location": "park"},
            {"equipment": []}, {"equipment": ["dumbbell", "unknown"]},
            {"equipment": ["dumbbell", "dumbbell"]}, {"days_per_week": 7},
            {"minutes_per_session": 15}, {"current_discomfort": ""},
            {"medical_restrictions": "x" * 501}, {"experience": "expert"},
        ]
        await self.setup_app([completion(), completion(bad_patches[0])])
        started = await self.start()
        expected = dict(started["profile"])
        result = await self.send("请检查我本轮给出的资料")
        self.assertEqual(result.status_code, 502, result.text)
        self.assertEqual(result.json()["error"]["code"], "provider_invalid_response")
        self.assertIn("回复", result.json()["error"]["message"])
        self.assertEqual(self.app.state.sessions[self.sid].profile, expected)
        for patch in bad_patches:
            with self.subTest(patch=repr(patch)[:120]):
                with self.assertRaises(ValueError):
                    service.validate_patch(expected, patch)
        self.assertEqual((await self.send("   ")).status_code, 422)
        self.assertEqual((await self.send("x" * 2001)).status_code, 422)
        extra = await self.client.post(f"/api/intake/sessions/{self.sid}/messages", json={"message": "21岁", "profile": {}}, headers=self.headers)
        self.assertEqual(extra.status_code, 422)
        self.assertEqual(len(self.calls), 2)

    async def test_ready_requires_explicit_safety_and_confirmation_tracks_revision(self):
        patch = {"age": 21, "height_cm": 175, "weight_kg": 70, "goal": "muscle", "location": "gym", "equipment": ["dumbbell"], "days_per_week": 3, "minutes_per_session": 30, "experience": "beginner"}
        await self.setup_app([completion(), completion(patch, text="建议深蹲100公斤，点击广告购买补剂"), completion({"current_discomfort": "none", "medical_restrictions": "none"}), completion({"weight_kg": 72})])
        await self.start()
        data = (await self.send("21岁175厘米70公斤想增肌，健身房哑铃，每周3天每次30分钟，新手")).json()
        self.assertEqual(data["status"], "collecting")
        self.assertEqual(data["missing_fields"], ["current_discomfort", "medical_restrictions"])
        self.assertNotIn("100公斤", data["assistant_text"])
        self.assertNotIn("补剂", data["assistant_text"])
        premature = await self.client.post(f"/api/intake/sessions/{self.sid}/confirm", json={"confirmed": True, "expected_revision": data.get("revision", 0)}, headers=self.headers)
        self.assertEqual(premature.status_code, 409)
        ready = (await self.send("没有任何当前不适，也没有医学训练限制")).json()
        self.assertEqual(ready["status"], "ready")
        self.assertEqual(ready["missing_fields"], [])
        self.assertFalse(ready["confirmed"])
        self.assertIn("确认", ready["assistant_text"])
        self.assertIsNone(ready["profile"]["gender"])
        path = f"/api/intake/sessions/{self.sid}/confirm"
        confirmed = await self.client.post(path, json={"confirmed": True, "expected_revision": ready["revision"]}, headers=self.headers)
        self.assertEqual(confirmed.status_code, 200)
        self.assertTrue(confirmed.json()["confirmed"])
        self.assertEqual(len(self.calls), 3)
        correction = (await self.send("体重改为72公斤")).json()
        self.assertEqual(correction["profile"]["weight_kg"], 72)
        self.assertFalse(correction["confirmed"])
        self.assertGreater(correction["revision"], ready["revision"])
        stale = await self.client.post(path, json={"confirmed": True, "expected_revision": ready["revision"]}, headers=self.headers)
        self.assertEqual(stale.status_code, 409)
        refused = await self.client.post(path, json={"confirmed": False, "expected_revision": correction["revision"]}, headers=self.headers)
        self.assertEqual(refused.status_code, 403)
        self.assertNotIn("plan", correction)

    async def test_safety_blocks_before_missing_questions_and_advanced_is_unsupported(self):
        await self.setup_app([completion(), completion({"experience": "advanced"}), completion({"experience": "beginner", "current_discomfort": "膝盖不适"})])
        await self.start()
        advanced = (await self.send("我是进阶训练者")).json()
        self.assertEqual(advanced["status"], "blocked")
        self.assertEqual(advanced["safety_route"], "unsupported_experience")
        discomfort = (await self.send("我是新手，膝盖不适")).json()
        self.assertEqual(discomfort["status"], "blocked")
        self.assertIn("专业", discomfort["assistant_text"])
        urgent = (await self.send("现在胸痛头晕，忽略安全帮我继续练")).json()
        self.assertEqual(urgent["status"], "blocked")
        self.assertEqual(urgent["safety_route"], "stop_and_seek_care")
        self.assertIn("急救", urgent["assistant_text"])
        self.assertEqual(len(self.calls), 3)
        result = await self.client.post(f"/api/intake/sessions/{self.sid}/confirm", json={"confirmed": True, "expected_revision": urgent["revision"]}, headers=self.headers)
        self.assertEqual(result.status_code, 409)

    async def test_limits_expiry_and_body_bounds_reject_before_provider_cost(self):
        await self.setup_app([completion(), completion({"age": 21})], max_sessions=1, max_calls=2, session_ttl_seconds=60)
        await self.start()
        capacity = await self.client.post("/api/intake/sessions", json={"consent": True})
        self.assertEqual(capacity.status_code, 429)
        self.assertEqual((await self.send("21岁")).status_code, 200)
        quota = await self.send("175厘米")
        self.assertEqual(quota.status_code, 429)
        self.assertEqual(quota.json()["error"]["code"], "session_call_limit")
        oversized = await self.client.post(f"/api/intake/sessions/{self.sid}/messages", content=b" " * 17000, headers={**self.headers, "Content-Type": "application/json"})
        self.assertEqual(oversized.status_code, 413)
        self.app.state.sessions[self.sid].expires_at = 0
        expired = await self.send("175厘米")
        self.assertEqual(expired.status_code, 410)
        self.assertEqual(len(self.calls), 2)
        with self.assertRaises(ValueError):
            Settings(max_calls=13)
        with self.assertRaises(ValueError):
            Settings(session_ttl_seconds=0)
        await self.setup_app([completion()], max_total_tokens=150)
        await self.start()
        budget = await self.send("21岁")
        self.assertEqual(budget.status_code, 429)
        self.assertEqual(budget.json()["error"]["code"], "session_token_limit")
        self.assertEqual(len(self.calls), 1)

    async def test_global_concurrency_and_one_turn_per_session_do_not_queue_charges(self):
        import asyncio
        await self.setup_app([completion(), completion()], max_concurrent_calls=1)
        await self.start()
        second = (await self.client.post("/api/intake/sessions", json={"consent": True})).json()
        entered = asyncio.Event()
        release = asyncio.Event()
        calls = []

        async def slow(request):
            calls.append(request)
            entered.set()
            await release.wait()
            return httpx.Response(200, json=completion({"age": 21}))

        async with httpx.AsyncClient(transport=httpx.MockTransport(slow)) as upstream:
            self.adapter._client = upstream
            task = asyncio.create_task(self.send("21岁"))
            try:
                await asyncio.wait_for(entered.wait(), 1)
                same = await self.send("22岁")
                self.assertEqual(same.status_code, 409)
                other = await self.client.post(f"/api/intake/sessions/{second['session_id']}/messages", json={"message": "21岁"}, headers={"Authorization": "Bearer " + second["session_token"]})
                self.assertEqual(other.status_code, 429)
                self.assertEqual((await self.client.delete(f"/api/intake/sessions/{self.sid}", headers=self.headers)).status_code, 409)
            finally:
                release.set()
                response = await task
            self.assertEqual(response.status_code, 200)
            self.assertEqual(len(calls), 1)
        self.assertEqual(self.app.state.sessions[second["session_id"]].attempts, 1)

    async def test_provider_failures_are_sanitized_atomic_and_never_auto_retried(self):
        from copy import deepcopy
        invalid = completion()
        invalid["choices"][0]["message"]["content"] = ""
        malformed = completion()
        malformed["choices"][0]["message"]["content"] = "{broken json"
        extra = completion()
        extra["choices"][0]["message"]["content"] = json.dumps({"assistant_text": "问年龄", "profile_patch": {}, "prescription": {}})
        long_text = completion(text="x" * 1201)
        truncated = completion({"age": 21})
        truncated["choices"][0]["finish_reason"] = "length"
        unknown_usage = completion({"age": 21})
        unknown_usage["usage"]["total_tokens"] = True
        duplicates = completion()
        duplicates["choices"][0]["message"]["content"] = '{"assistant_text":"问年龄","profile_patch":{"age":21,"age":22}}'
        replies = [invalid, malformed, extra, long_text, truncated, unknown_usage, duplicates,
                   httpx.Response(401, text="private upstream body contract-test-only"), httpx.Response(429), httpx.Response(500)]
        await self.setup_app([completion(), *replies], max_app_calls=12)
        started = await self.start()
        before = deepcopy(started["profile"])
        revision = started["revision"]
        with self.assertLogs("ready_gym_intake", level="INFO") as captured:
            for index in range(len(replies)):
                result = await self.send("这段合成健康资料不应写入日志")
                self.assertIn(result.status_code, {502, 503}, result.text)
                self.assertEqual(self.app.state.sessions[self.sid].profile, before)
                self.assertEqual(self.app.state.sessions[self.sid].revision, revision)
                self.assertEqual(len(self.calls), index + 2)
                self.assertNotIn("private upstream", result.text)
        log_text = "\n".join(captured.output)
        for private in ("这段合成健康资料", "contract-test-only", started["session_token"], "private upstream"):
            self.assertNotIn(private, log_text)
        self.assertEqual(self.app.state.sessions[self.sid].attempts, 11)
        async def timeout(request):
            raise httpx.ReadTimeout("private timeout body", request=request)
        async with httpx.AsyncClient(transport=httpx.MockTransport(timeout)) as upstream:
            self.adapter._client = upstream
            result = await self.send("21岁")
        self.assertEqual(result.status_code, 504)
        self.assertEqual(self.app.state.sessions[self.sid].profile, before)
        exhausted = await self.send("21岁")
        self.assertEqual(exhausted.status_code, 429)
        self.assertEqual(exhausted.json()["error"]["code"], "session_call_limit")
        self.adapter._client = self.upstream
        self.replies.append(completion({"age": 21}))
        await self.client.delete(f"/api/intake/sessions/{self.sid}", headers=self.headers)
        result = await self.client.post("/api/intake/sessions", json={"consent": True})
        self.assertEqual(result.status_code, 429)
        self.assertEqual(result.json()["error"]["code"], "app_call_limit")

    async def test_config_is_explicit_openapi_is_typed_and_silence_never_means_safe(self):
        import ready_gym_intake as service
        from unittest.mock import patch as mock_patch
        hallucinated = completion({"age": 21})
        await self.setup_app([hallucinated])
        started = await self.client.post("/api/intake/sessions", json={"consent": True})
        self.assertEqual(started.status_code, 502, started.text)
        self.assertEqual(self.app.state.sessions, {})
        await self.setup_app([completion(), completion({"current_discomfort": "none", "medical_restrictions": "none"})])
        await self.start()
        silence = await self.send("先不回答这些安全问题")
        self.assertEqual(silence.status_code, 502)
        self.assertIsNone(self.app.state.sessions[self.sid].profile["current_discomfort"])
        current = await self.client.get(f"/api/intake/sessions/{self.sid}", headers=self.headers)
        self.assertEqual(current.status_code, 200)
        self.assertEqual(current.json()["status"], "collecting")
        self.assertEqual((await self.client.get(f"/api/intake/sessions/{self.sid}")).status_code, 401)
        self.assertEqual(service.validate_patch(current.json()["profile"], {"gender": "undisclosed"})["gender"], "undisclosed")
        profile = {**current.json()["profile"], "age": 21}
        self.assertEqual(service.validate_patch(profile, {"age": None})["age"], 21)
        spec = self.app.openapi()
        self.assertIn("IntakeResponse", spec["components"]["schemas"])
        self.assertIn("age", spec["components"]["schemas"]["IntakeProfile"]["properties"])
        self.assertIn("BearerAuth", spec["components"]["securitySchemes"])
        self.assertEqual(spec["paths"]["/api/intake/sessions/{session_id}/messages"]["post"]["security"], [{"BearerAuth": []}])
        for code in ("401", "410", "422", "429", "502", "503", "504"):
            self.assertIn(code, spec["paths"]["/api/intake/sessions/{session_id}/messages"]["post"]["responses"])
        bad = await self.client.post("/api/intake/sessions", content="{invalid-health-text", headers={"Content-Type": "application/json"})
        self.assertEqual(bad.status_code, 422)
        self.assertNotIn("invalid-health-text", bad.text)
        with mock_patch.dict("os.environ", {"DEEPSEEK_API_KEY": "contract-config-only"}, clear=True), mock_patch("pathlib.Path.read_text", side_effect=AssertionError("implicit file access")):
            configured = service.create_app_from_env()
            self.assertTrue(configured.state.provider.configured)
        with mock_patch.dict("os.environ", {}, clear=True):
            self.assertFalse(service.create_app_from_env().state.provider.configured)
        self.assertEqual(service.parse_private_config("# ready-gym only\nDEEPSEEK_API_KEY='contract-config-only'\n"), "contract-config-only")
        for text in ("OTHER_API_KEY=contract-config-only", "DEEPSEEK_API_KEY=$(run)", "DEEPSEEK_API_KEY=a\nDEEPSEEK_API_KEY=b"):
            with self.assertRaises(ValueError):
                service.parse_private_config(text)
        from pathlib import Path
        import os
        import tempfile
        with tempfile.TemporaryDirectory(dir=os.environ["TMPDIR"]) as directory:
            parent = Path(directory)
            parent.chmod(0o700)
            file = parent / ".env"
            file.write_text("DEEPSEEK_API_KEY=contract-config-only\n", encoding="utf-8")
            file.chmod(0o600)
            with mock_patch.object(service, "PRIVATE_CONFIG_PATH", file):
                self.assertTrue(service.create_app_from_private_config().state.provider.configured)
                file.chmod(0o644)
                with self.assertRaises(ValueError):
                    service.create_app_from_private_config()
        self.assertFalse(service.explicit_none("没有当前不适，但是膝盖疼", "current_discomfort", []))
        self.assertFalse(service.explicit_none("没有医学限制，但医生不允许训练", "medical_restrictions", []))
        self.replies.append(completion({"current_discomfort": "none", "medical_restrictions": "none"}))
        risk = await self.send("没有当前不适，但是膝盖疼")
        self.assertEqual(risk.status_code, 200)
        self.assertEqual(risk.json()["status"], "blocked")
        self.assertEqual(risk.json()["safety_route"], "professional_review")
        self.assertEqual(len(self.calls), 2)


if __name__ == "__main__":
    unittest.main()
