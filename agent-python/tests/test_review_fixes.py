"""Safety review regressions: real ASGI/adapter, MockTransport only (not live)."""
import unittest

import httpx
import test_intake
from test_intake import completion


FULL_PROFILE = {
    "age": 21, "height_cm": 175, "weight_kg": 70, "goal": "muscle",
    "location": "gym", "equipment": ["dumbbell"], "days_per_week": 3,
    "minutes_per_session": 30, "experience": "beginner",
    "current_discomfort": "none", "medical_restrictions": "none",
}
READY_MESSAGE = "21岁175厘米70公斤想增肌，健身房哑铃，每周3天每次30分钟，新手，没有当前不适，也没有医学训练限制"


class ReviewFixes(unittest.IsolatedAsyncioTestCase):
    asyncSetUp = test_intake.IntakeContract.asyncSetUp
    setup_app = test_intake.IntakeContract.setup_app
    start = test_intake.IntakeContract.start
    send = test_intake.IntakeContract.send

    async def snapshot(self):
        response = await self.client.get(f"/api/intake/sessions/{self.sid}", headers=self.headers)
        self.assertEqual(response.status_code, 200, response.text)
        return response.json()

    async def confirm(self, revision):
        return await self.client.post(
            f"/api/intake/sessions/{self.sid}/confirm",
            json={"confirmed": True, "expected_revision": revision}, headers=self.headers,
        )

    async def test_ambiguous_safety_denials_cannot_be_confirmed(self):
        messages = (
            "我不是没有当前不适，运动时会气短；我不是没有医学训练限制。",
            "我不能确认没有当前不适，也不能确认没有医学训练限制",
            "我可能没有当前不适，可能没有医学训练限制",
            "朋友说‘没有当前不适，没有医学训练限制’，我还不知道",
            "之前说没有当前不适、没有医学训练限制，现在撤回这个回答",
            "不确定是否没有当前不适，没有医学训练限制吗？",
        )
        for message in messages:
            with self.subTest(message=message):
                await self.setup_app([completion(), completion(FULL_PROFILE)])
                await self.start()
                response = await self.send(message)
                self.assertEqual(response.status_code, 502, response.text)
                self.assertEqual(response.json()["error"]["code"], "provider_invalid_response")
                current = await self.snapshot()
                self.assertIsNone(current["profile"]["current_discomfort"])
                self.assertIsNone(current["profile"]["medical_restrictions"])
                self.assertEqual(current["status"], "collecting")
                self.assertEqual((await self.confirm(current["revision"])).status_code, 409)
                self.assertEqual(len(self.calls), 2)


    async def test_uncertain_safety_revokes_previous_safe_values_even_on_failure(self):
        for reply in (completion({"current_discomfort": "none", "medical_restrictions": "none"}),
                      completion(), httpx.Response(500, text="synthetic upstream failure")):
            with self.subTest(reply=repr(reply)):
                await self.setup_app([completion(), completion(FULL_PROFILE), reply])
                await self.start()
                ready = (await self.send(READY_MESSAGE)).json()
                self.assertEqual(ready["status"], "ready")
                self.assertEqual((await self.confirm(ready["revision"])).status_code, 200)
                response = await self.send("我不能确认没有当前不适，也不能确认没有医学训练限制")
                self.assertIn(response.status_code, {200, 502})
                current = await self.snapshot()
                self.assertIsNone(current["profile"]["current_discomfort"])
                self.assertIsNone(current["profile"]["medical_restrictions"])
                self.assertEqual(current["profile"]["age"], 21)
                self.assertEqual(current["status"], "collecting")
                self.assertFalse(current["confirmed"])
                self.assertGreater(current["revision"], ready["revision"])
                self.assertEqual((await self.confirm(current["revision"])).status_code, 409)
                self.assertEqual(len(self.calls), 3)


    async def test_provider_stop_route_latches_through_local_guard_and_later_none(self):
        for field in ("current_discomfort", "medical_restrictions"):
            with self.subTest(field=field):
                await self.setup_app([
                    completion(), completion(FULL_PROFILE), completion({field: "头晕"}),
                    completion({"current_discomfort": "none", "medical_restrictions": "none"}),
                ])
                await self.start()
                ready = (await self.send(READY_MESSAGE)).json()
                self.assertEqual(ready["status"], "ready")
                # Neutral raw text isolates the provider source; raw 眩晕 now
                # correctly short-circuits before the adapter (second review B).
                first = await self.send("有一项异常需要核对，没有医学训练限制")
                self.assertEqual(first.status_code, 200, first.text)
                self.assertEqual(first.json()["safety_route"], "stop_and_seek_care")
                self.assertTrue(self.app.state.sessions[self.sid].urgent)
                mild = await self.send("膝盖疼")
                self.assertEqual(mild.json()["safety_route"], "stop_and_seek_care")
                last = await self.send("没有当前不适，也没有医学训练限制")
                self.assertEqual(last.status_code, 200, last.text)
                self.assertEqual(last.json()["safety_route"], "stop_and_seek_care")
                self.assertEqual(last.json()["status"], "blocked")
                self.assertEqual((await self.confirm(last.json()["revision"])).status_code, 409)
                self.assertEqual(len(self.calls), 4)


    async def test_underage_correction_with_empty_patch_invalidates_only_age(self):
        await self.setup_app([completion(), completion(FULL_PROFILE), completion(text="当前仅支持成人，请澄清年龄。")])
        await self.start()
        ready = (await self.send(READY_MESSAGE)).json()
        self.assertEqual((await self.confirm(ready["revision"])).status_code, 200)
        response = await self.send("年龄填错了，其实只有17岁")
        self.assertEqual(response.status_code, 200, response.text)
        data = response.json()
        self.assertEqual(data["status"], "collecting")
        self.assertEqual(data["clarification_fields"], ["age"])
        self.assertEqual(data["missing_fields"], ["age"])
        self.assertIsNone(data["profile"]["age"])
        for key in FULL_PROFILE.keys() - {"age"}:
            self.assertEqual(data["profile"][key], FULL_PROFILE[key])
        self.assertFalse(data["confirmed"])
        self.assertGreater(data["revision"], ready["revision"])
        self.assertEqual((await self.confirm(data["revision"])).status_code, 409)
        self.assertEqual((await self.confirm(ready["revision"])).status_code, 409)
        self.assertEqual(len(self.calls), 3)


    async def test_named_corrections_are_fail_closed_for_every_profile_field(self):
        corrections = {
            "age": "年龄填错了，其实只有17岁",
            "height_cm": "身高填错了，改成99厘米",
            "weight_kg": "体重填错了，其实只有29公斤",
            "gender": "性别填错了，暂时不能确定",
            "goal": "目标填错了，其实是竞技比赛",
            "location": "训练地点填错了，其实在公园",
            "equipment": "器械填错了，其实只有弹力带",
            "days_per_week": "频率填错了，其实每周只能1天",
            "minutes_per_session": "时长填错了，其实每次只有15分钟",
            "experience": "经验填错了，其实我是竞技训练者",
        }
        malformed = completion()
        malformed["choices"][0]["message"]["content"] = "{synthetic invalid json"
        for field, message in corrections.items():
            for reply in (completion(), malformed, httpx.Response(500)):
                with self.subTest(field=field, reply=repr(reply)):
                    full = {**FULL_PROFILE, "gender": "undisclosed"}
                    await self.setup_app([completion(), completion(full), reply])
                    await self.start()
                    ready = (await self.send(READY_MESSAGE)).json()
                    self.assertEqual((await self.confirm(ready["revision"])).status_code, 200)
                    response = await self.send(message)
                    self.assertIn(response.status_code, {200, 502}, response.text)
                    current = await self.snapshot()
                    self.assertIsNone(current["profile"][field])
                    self.assertEqual(current["clarification_fields"], [field])
                    self.assertIn(field, current["missing_fields"])
                    self.assertEqual(current["status"], "collecting")
                    self.assertFalse(current["confirmed"])
                    self.assertGreater(current["revision"], ready["revision"])
                    self.assertEqual((await self.confirm(current["revision"])).status_code, 409)
                    for other in full.keys() - {field}:
                        self.assertEqual(current["profile"][other], full[other])
                    self.assertEqual(current["calls_used"], 3)
                    self.assertEqual(len(self.calls), 3)


    async def test_bounded_provider_clarifications_prevent_adoption_until_resolved(self):
        def clarified(patch, fields):
            reply = completion(patch, text="模型文字不是状态协议，也不显示。")
            envelope = {"assistant_text": "模型文字不是状态协议，也不显示。",
                        "profile_patch": patch, "clarification_fields": fields}
            reply["choices"][0]["message"]["content"] = __import__("json").dumps(envelope)
            return reply

        await self.setup_app([
            completion(), completion(FULL_PROFILE), clarified({}, ["days_per_week"]),
            clarified({"days_per_week": 4}, []),
        ])
        await self.start()
        ready = (await self.send(READY_MESSAGE)).json()
        self.assertEqual((await self.confirm(ready["revision"])).status_code, 200)
        response = await self.send("我之前那一项可能说得不准，请澄清")
        self.assertEqual(response.status_code, 200, response.text)
        data = response.json()
        self.assertEqual(data["clarification_fields"], ["days_per_week"])
        self.assertIsNone(data["profile"]["days_per_week"])
        self.assertEqual(data["missing_fields"], ["days_per_week"])
        self.assertFalse(data["confirmed"])
        self.assertEqual(data["status"], "collecting")
        self.assertNotIn("模型文字", data["assistant_text"])
        self.assertEqual((await self.confirm(data["revision"])).status_code, 409)
        resolved = await self.send("每周确定是4天")
        self.assertEqual(resolved.status_code, 200, resolved.text)
        self.assertEqual(resolved.json()["profile"]["days_per_week"], 4)
        self.assertEqual(resolved.json()["clarification_fields"], [])
        self.assertEqual(resolved.json()["status"], "ready")
        self.assertEqual((await self.confirm(resolved.json()["revision"])).status_code, 200)
        self.assertEqual(len(self.calls), 4)


    async def test_huge_numeric_provider_values_are_sanitized_502_and_atomic(self):
        huge = 10 ** 400
        for field in ("height_cm", "weight_kg"):
            for value in (huge, -huge):
                with self.subTest(field=field, sign="positive" if value > 0 else "negative"):
                    await self.setup_app([completion(), completion(FULL_PROFILE), completion({field: value})])
                    await self.start()
                    ready = (await self.send(READY_MESSAGE)).json()
                    response = await self.send("请检查本次提供的值")
                    self.assertEqual(response.status_code, 502, response.text)
                    self.assertEqual(response.json()["error"]["code"], "provider_invalid_response")
                    current = await self.snapshot()
                    self.assertEqual(current["profile"], ready["profile"])
                    self.assertEqual(current["revision"], ready["revision"])
                    self.assertEqual(current["clarification_fields"], [])
                    self.assertEqual(current["calls_used"], 3)
                    self.assertNotIn(str(huge), response.text)
                    self.assertEqual(len(self.calls), 3)


    async def test_unsupported_numeric_corrections_cannot_be_normalized_by_model(self):
        corrections = (
            ("age", "年龄填错了，其实只有17岁", 21),
            ("height_cm", "身高改为99厘米", 175),
            ("weight_kg", "体重改为29公斤", 70),
            ("days_per_week", "频率改为每周1天", 3),
            ("minutes_per_session", "时长改为每次15分钟", 30),
        )
        for field, message, fabricated in corrections:
            with self.subTest(field=field):
                await self.setup_app([completion(), completion(FULL_PROFILE), completion({field: fabricated})])
                await self.start()
                ready = (await self.send(READY_MESSAGE)).json()
                response = await self.send(message)
                self.assertEqual(response.status_code, 502, response.text)
                self.assertEqual(response.json()["error"]["code"], "provider_invalid_response")
                current = await self.snapshot()
                self.assertIsNone(current["profile"][field])
                self.assertEqual(current["clarification_fields"], [field])
                self.assertEqual(current["status"], "collecting")
                self.assertEqual((await self.confirm(current["revision"])).status_code, 409)
                self.assertGreater(current["revision"], ready["revision"])


    async def test_reported_or_qualified_denial_clauses_are_not_user_safety_evidence(self):
        messages = (
            "引用示例：没有当前不适；没有医学训练限制",
            "他告诉我：没有当前不适；没有医学训练限制",
            "没有当前不适，也没有医学训练限制，我说不准",
            "没有当前不适，也没有医学训练限制，应该是这样",
        )
        for message in messages:
            with self.subTest(message=message):
                await self.setup_app([completion(), completion(FULL_PROFILE)])
                await self.start()
                response = await self.send(message)
                self.assertEqual(response.status_code, 502, response.text)
                current = await self.snapshot()
                self.assertEqual(current["status"], "collecting")
                self.assertEqual((await self.confirm(current["revision"])).status_code, 409)


    async def test_clarification_envelope_is_bounded_and_invalid_payloads_are_atomic(self):
        import json
        invalid_fields = (None, "age", ["unknown"], ["age", "age"], [True], ["age"] * 13)
        replies = []
        for fields in invalid_fields:
            reply = completion()
            reply["choices"][0]["message"]["content"] = json.dumps({
                "assistant_text": "合成验证文字", "profile_patch": {}, "clarification_fields": fields,
            })
            replies.append(reply)
        conflict = completion()
        conflict["choices"][0]["message"]["content"] = json.dumps({
            "assistant_text": "合成验证文字", "profile_patch": {"age": 22}, "clarification_fields": ["age"],
        })
        await self.setup_app([completion(), completion(FULL_PROFILE), *replies, conflict])
        await self.start()
        ready = (await self.send(READY_MESSAGE)).json()
        for index in range(len(replies) + 1):
            with self.subTest(index=index):
                response = await self.send("请检查本次提案")
                self.assertEqual(response.status_code, 502, response.text)
                self.assertEqual(response.json()["error"]["code"], "provider_invalid_response")
                current = await self.snapshot()
                self.assertEqual(current["profile"], ready["profile"])
                self.assertEqual(current["revision"], ready["revision"])
                self.assertEqual(current["clarification_fields"], [])
                self.assertEqual(current["calls_used"], index + 3)
                self.assertEqual(len(self.calls), index + 3)

    async def test_corrections_invalidate_before_quota_without_an_extra_attempt(self):
        await self.setup_app([completion(), completion(FULL_PROFILE)], max_calls=2)
        await self.start()
        ready = (await self.send(READY_MESSAGE)).json()
        self.assertEqual((await self.confirm(ready["revision"])).status_code, 200)
        response = await self.send("年龄填错了，其实只有17岁")
        self.assertEqual(response.status_code, 429, response.text)
        self.assertEqual(response.json()["error"]["code"], "session_call_limit")
        current = await self.snapshot()
        self.assertEqual(current["status"], "collecting")
        self.assertEqual(current["clarification_fields"], ["age"])
        self.assertIsNone(current["profile"]["age"])
        self.assertFalse(current["confirmed"])
        self.assertEqual((await self.confirm(current["revision"])).status_code, 409)
        self.assertEqual(len(self.calls), 2)


    async def test_pending_correction_requires_a_supported_user_answer_not_a_model_guess(self):
        await self.setup_app([
            completion(), completion(FULL_PROFILE), completion(), completion({"age": 21}),
            completion({"age": 21}),
            completion({"age": 22}),
        ])
        await self.start()
        await self.send(READY_MESSAGE)
        await self.send("年龄填错了，其实只有17岁")
        guessed = await self.send("17岁")
        self.assertEqual(guessed.status_code, 502, guessed.text)
        current = await self.snapshot()
        self.assertIsNone(current["profile"]["age"])
        self.assertEqual(current["clarification_fields"], ["age"])
        self.assertEqual((await self.confirm(current["revision"])).status_code, 409)
        missing_answer = await self.send("我不知道")
        self.assertEqual(missing_answer.status_code, 502, missing_answer.text)
        resolved = await self.send("年龄确定为22岁")
        self.assertEqual(resolved.status_code, 200, resolved.text)
        self.assertEqual(resolved.json()["profile"]["age"], 22)
        self.assertEqual(resolved.json()["clarification_fields"], [])
        self.assertEqual(resolved.json()["status"], "ready")


    async def test_inflight_expiry_never_returns_success_or_an_expired_token(self):
        for initial in (True, False):
            with self.subTest(initial=initial):
                await self.setup_app([completion()])
                if not initial:
                    await self.start()
                requests = []

                async def expire_during_response(request):
                    requests.append(request)
                    for session in self.app.state.sessions.values():
                        session.expires_at = 0
                    return httpx.Response(200, json=completion({} if initial else {"age": 21}))

                async with httpx.AsyncClient(transport=httpx.MockTransport(expire_during_response)) as upstream:
                    self.adapter._client = upstream
                    response = (await self.client.post("/api/intake/sessions", json={"consent": True})
                                if initial else await self.send("21岁"))
                self.assertEqual(response.status_code, 410, response.text)
                self.assertEqual(response.json()["error"]["code"], "session_expired")
                self.assertNotIn("session_token", response.json())
                self.assertEqual(self.app.state.sessions, {})
                self.assertEqual(len(requests), 1)
                self.assertEqual(self.app.state.provider_attempts, 1 if initial else 2)


    async def test_corrected_enums_and_equipment_require_matching_user_evidence(self):
        cases = (
            ("goal", "目标填错了，实际是竞技比赛", "muscle", "目标更正为健康", "health"),
            ("location", "地点填错了，其实在公园", "gym", "地点更正为在家", "home"),
            ("equipment", "器械填错了，其实只有弹力带", ["dumbbell"], "器械更正为徒手", ["bodyweight"]),
            ("experience", "经验填错了，其实我是竞技训练者", "beginner", "经验更正为恢复训练", "returning"),
            ("gender", "性别填错了，不能确定", "male", "性别改为不透露", "undisclosed"),
        )
        for field, message, fabricated, answer, value in cases:
            with self.subTest(field=field):
                await self.setup_app([
                    completion(), completion(FULL_PROFILE), completion({field: fabricated}),
                    completion({field: value}),
                ])
                await self.start()
                await self.send(READY_MESSAGE)
                response = await self.send(message)
                self.assertEqual(response.status_code, 502, response.text)
                current = await self.snapshot()
                self.assertIsNone(current["profile"][field])
                self.assertEqual(current["clarification_fields"], [field])
                self.assertEqual((await self.confirm(current["revision"])).status_code, 409)
                resolved = await self.send(answer)
                self.assertEqual(resolved.status_code, 200, resolved.text)
                self.assertEqual(resolved.json()["profile"][field], value)
                self.assertEqual(resolved.json()["clarification_fields"], [])
                self.assertEqual(resolved.json()["status"], "ready")


    async def test_exported_contract_has_typed_required_clarifications(self):
        import json
        from pathlib import Path
        import ready_gym_intake as service

        path = Path(__file__).resolve().parents[2] / "docs" / "intake-api.json"
        exported = json.loads(path.read_text(encoding="utf-8"))
        live_schema = service.create_app().openapi()
        self.assertEqual(exported, live_schema)
        for name in ("IntakeResponse", "StartResponse"):
            schema = exported["components"]["schemas"][name]
            self.assertIn("clarification_fields", schema["required"])
            prop = schema["properties"]["clarification_fields"]
            self.assertEqual(prop["maxItems"], 12)
            self.assertEqual(set(prop["items"]["enum"]), set(service.Session(token_digest="test").profile))


if __name__ == "__main__":
    unittest.main()
