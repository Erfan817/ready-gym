"""Second review: actual ASGI + adapter, isolated MockTransport, never live."""
import unittest

import httpx
import test_intake
from test_intake import completion
from test_review_fixes import FULL_PROFILE, READY_MESSAGE


class SecondReview(unittest.IsolatedAsyncioTestCase):
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

    async def assert_not_adoptable(self, *, calls, route=None):
        current = await self.snapshot()
        self.assertNotEqual(current["status"], "ready")
        self.assertFalse(current["confirmed"])
        self.assertEqual(current["safety_route"], route)
        self.assertEqual((await self.confirm(current["revision"])).status_code, 409)
        self.assertEqual(current["calls_used"], calls)
        self.assertEqual(len(self.calls), calls)
        self.assertEqual(self.app.state.provider_attempts, calls)
        self.assertEqual(current["response_source"], "server_policy")
        self.assertNotIn("MODEL_PROSE", current["assistant_text"])
        return current

    async def test_a_whole_message_safety_evidence_and_revocation_matrix(self):
        messages = (
            "没有当前不适。没有医学训练限制。我有当前不适",
            "我有当前不适。没有当前不适。没有医学训练限制",
            "这些是朋友的回答，不代表我的情况。没有当前不适。没有医学训练限制",
            "这是他的回答，不代表我。没有医学训练限制。没有当前不适",
            "没有当前不适。没有医学训练限制。我有医学限制",
        )
        replies = (
            (completion({"current_discomfort": "none", "medical_restrictions": "none"}, text="MODEL_PROSE"), 502),
            (completion(text="MODEL_PROSE"), 200),
            (httpx.Response(500, text="isolated synthetic upstream failure"), 502),
        )
        for message in messages:
            for was_ready in (False, True):
                for reply, expected in replies:
                    with self.subTest(message=message, ready=was_ready, expected=expected):
                        baseline = FULL_PROFILE if was_ready else {
                            key: value for key, value in FULL_PROFILE.items()
                            if key not in {"current_discomfort", "medical_restrictions"}
                        }
                        await self.setup_app([completion(), completion(baseline), reply])
                        await self.start()
                        before = (await self.send(READY_MESSAGE if was_ready else "21岁175厘米70公斤想增肌，健身房哑铃，每周3天每次30分钟，新手")).json()
                        if was_ready:
                            self.assertEqual((await self.confirm(before["revision"])).status_code, 200)
                        response = await self.send(message)
                        self.assertEqual(response.status_code, expected, response.text)
                        current = await self.assert_not_adoptable(calls=3)
                        affected = {"current_discomfort", "medical_restrictions"} if "朋友" in message or "他的" in message else (
                            {"medical_restrictions"} if message.endswith("我有医学限制") else {"current_discomfort"}
                        )
                        for field in affected:
                            self.assertIsNone(current["profile"][field])
                            if was_ready:
                                self.assertIn(field, current["clarification_fields"])
                        self.assertEqual(current["profile"]["age"], 21)
                        if was_ready:
                            self.assertGreater(current["revision"], before["revision"])

    async def test_a_unambiguous_own_denials_keep_mixed_extraction(self):
        await self.setup_app([completion(), completion(FULL_PROFILE, text="MODEL_PROSE")])
        await self.start()
        result = await self.send(READY_MESSAGE)
        self.assertEqual(result.status_code, 200, result.text)
        data = result.json()
        self.assertEqual(data["status"], "ready")
        self.assertEqual(data["clarification_fields"], [])
        self.assertIsNone(data["safety_route"])
        self.assertEqual(data["profile"]["age"], 21)
        self.assertEqual((await self.confirm(data["revision"])).status_code, 200)
        self.assertEqual(len(self.calls), 2)
        self.assertNotIn("MODEL_PROSE", data["assistant_text"])

    async def test_b_raw_known_risk_precedes_provider_failures_and_all_budgets(self):
        cases = (
            ("我现在运动时眩晕", "stop_and_seek_care", "current_discomfort"),
            ("我现在运动时头晕", "stop_and_seek_care", "current_discomfort"),
            ("我现在运动时胸痛", "stop_and_seek_care", "current_discomfort"),
            ("我现在运动时气短", "professional_review", "current_discomfort"),
            ("我有当前不适", "professional_review", "current_discomfort"),
            ("有医学限制", "professional_review", "medical_restrictions"),
        )
        modes = (
            ({}, completion()),
            ({}, httpx.Response(500)),
            ({"max_calls": 2}, None),
            ({"max_total_tokens": 300}, None),
            ({"max_app_calls": 2}, None),
        )
        for message, route, field in cases:
            for limits, reply in modes:
                with self.subTest(message=message, limits=limits, failed=isinstance(reply, httpx.Response)):
                    replies = [completion(), completion(FULL_PROFILE)]
                    if reply is not None:
                        replies.append(reply)
                    await self.setup_app(replies, **limits)
                    await self.start()
                    ready = (await self.send(READY_MESSAGE)).json()
                    self.assertEqual((await self.confirm(ready["revision"])).status_code, 200)
                    response = await self.send(message)
                    self.assertEqual(response.status_code, 200, response.text)
                    self.assertEqual(response.json()["status"], "blocked")
                    self.assertEqual(response.json()["safety_route"], route)
                    current = await self.assert_not_adoptable(calls=2, route=route)
                    self.assertNotIn(current["profile"][field], {None, "none"})
                    self.assertGreater(current["revision"], ready["revision"])
                    self.assertEqual((await self.confirm(ready["revision"])).status_code, 409)

    async def test_b_known_profile_risk_and_raw_route_share_urgent_latch(self):
        for field in ("current_discomfort", "medical_restrictions"):
            with self.subTest(field=field):
                await self.setup_app([
                    completion(), completion(FULL_PROFILE), completion({field: "眩晕"}),
                    completion({"current_discomfort": "none", "medical_restrictions": "none"}),
                ])
                await self.start()
                ready = (await self.send(READY_MESSAGE)).json()
                self.assertEqual((await self.confirm(ready["revision"])).status_code, 200)
                first = await self.send("有一项异常需要核对")
                self.assertEqual(first.status_code, 200, first.text)
                self.assertEqual(first.json()["safety_route"], "stop_and_seek_care")
                await self.assert_not_adoptable(calls=3, route="stop_and_seek_care")
                mild = await self.send("膝盖疼")
                self.assertEqual(mild.json()["safety_route"], "stop_and_seek_care")
                last = await self.send("没有当前不适，也没有医学训练限制")
                self.assertEqual(last.status_code, 200, last.text)
                await self.assert_not_adoptable(calls=4, route="stop_and_seek_care")
                self.assertEqual(last.json()["profile"]["current_discomfort"], "none")

    async def test_c_numeric_mentions_are_not_affirmative_answers_matrix(self):
        cases = (
            ("age", "年龄", "21岁", 21, "年龄确定为22岁", 22),
            ("height_cm", "身高", "175厘米", 175, "身高确定为176厘米", 176),
            ("weight_kg", "体重", "70公斤", 70, "体重确定为71公斤", 71),
            ("days_per_week", "频率", "每周3天", 3, "每周确定为4天", 4),
            ("minutes_per_session", "时长", "每次30分钟", 30, "每次确定为45分钟", 45),
        )
        templates = (
            "{label}填错了，我不是{answer}",
            "不确定是否{answer}",
            "引用样例：{answer}",
            "这是朋友的回答：{answer}，不代表我的情况",
            "我撤回{answer}这个回答，{label}仍待核实",
            "{answer}。我不是{answer}",
        )
        for field, label, answer, old, positive, new in cases:
            for template in templates:
                for pending in (False, True):
                    with self.subTest(field=field, template=template, pending=pending):
                        replies = [completion(), completion(FULL_PROFILE)]
                        if pending:
                            replies.append(completion())
                        replies.extend([completion({field: old}, text="MODEL_PROSE"), completion({field: new})])
                        await self.setup_app(replies)
                        await self.start()
                        ready = (await self.send(READY_MESSAGE)).json()
                        self.assertEqual((await self.confirm(ready["revision"])).status_code, 200)
                        if pending:
                            invalidated = await self.send(f"{label}撤回，仍待核实")
                            self.assertEqual(invalidated.status_code, 200, invalidated.text)
                        response = await self.send(template.format(label=label, answer=answer))
                        self.assertEqual(response.status_code, 502, response.text)
                        current = await self.assert_not_adoptable(calls=4 if pending else 3)
                        self.assertIsNone(current["profile"][field])
                        self.assertEqual(current["clarification_fields"], [field])
                        self.assertGreater(current["revision"], ready["revision"])
                        for other in FULL_PROFILE.keys() - {field}:
                            self.assertEqual(current["profile"][other], FULL_PROFILE[other])
                        resolved = await self.send(positive)
                        self.assertEqual(resolved.status_code, 200, resolved.text)
                        self.assertEqual(resolved.json()["profile"][field], new)
                        self.assertEqual(resolved.json()["status"], "ready")
                        self.assertEqual((await self.confirm(resolved.json()["revision"])).status_code, 200)
                        self.assertEqual(len(self.calls), 5 if pending else 4)

    async def test_c_numeric_revocation_survives_empty_error_and_quota(self):
        modes = (
            ({}, completion(), 200, 3),
            ({}, httpx.Response(500), 502, 3),
            ({"max_calls": 2}, None, 429, 2),
        )
        for limits, reply, expected, calls in modes:
            with self.subTest(limits=limits, expected=expected):
                replies = [completion(), completion(FULL_PROFILE)]
                if reply is not None:
                    replies.append(reply)
                await self.setup_app(replies, **limits)
                await self.start()
                ready = (await self.send(READY_MESSAGE)).json()
                self.assertEqual((await self.confirm(ready["revision"])).status_code, 200)
                response = await self.send("不确定是否21岁")
                self.assertEqual(response.status_code, expected, response.text)
                current = await self.assert_not_adoptable(calls=calls)
                self.assertIsNone(current["profile"]["age"])
                self.assertEqual(current["clarification_fields"], ["age"])
                self.assertGreater(current["revision"], ready["revision"])

    async def test_d_withdrawn_reported_or_uncertain_enum_answers_matrix(self):
        cases = (
            ("experience", "训练经验", "新手", "beginner", "训练经验确定为恢复训练", "returning"),
            ("location", "训练地点", "健身房", "gym", "训练地点确定为在家", "home"),
            ("equipment", "器械", "哑铃", ["dumbbell"], "器械确定为徒手", ["bodyweight"]),
            ("goal", "目标", "增肌", "muscle", "目标确定为健康", "health"),
            ("gender", "性别", "男性", "male", "性别确定为不透露", "undisclosed"),
        )
        templates = (
            "我撤回{answer}这个回答，{label}仍待核实",
            "{label}不确定是否{answer}",
            "引用样例：{label}{answer}",
            "这是朋友的回答：{label}{answer}，不代表我的情况",
            "{label}不是{answer}",
            "{label}{answer}。我撤回这个回答，{label}仍待核实",
        )
        for field, label, answer, old, positive, new in cases:
            for template in templates:
                for pending in (False, True):
                    with self.subTest(field=field, template=template, pending=pending):
                        full = {**FULL_PROFILE, "gender": "male"}
                        replies = [completion(), completion(full)]
                        if pending:
                            replies.append(completion())
                        replies.extend([completion({field: old}, text="MODEL_PROSE"), completion({field: new})])
                        await self.setup_app(replies)
                        await self.start()
                        ready = (await self.send(READY_MESSAGE)).json()
                        self.assertEqual((await self.confirm(ready["revision"])).status_code, 200)
                        if pending:
                            invalidated = await self.send(f"{label}撤回，仍待核实")
                            self.assertEqual(invalidated.status_code, 200, invalidated.text)
                        response = await self.send(template.format(label=label, answer=answer))
                        self.assertEqual(response.status_code, 502, response.text)
                        current = await self.assert_not_adoptable(calls=4 if pending else 3)
                        self.assertIsNone(current["profile"][field])
                        self.assertEqual(current["clarification_fields"], [field])
                        self.assertGreater(current["revision"], ready["revision"])
                        for other in full.keys() - {field}:
                            self.assertEqual(current["profile"][other], full[other])
                        resolved = await self.send(positive)
                        self.assertEqual(resolved.status_code, 200, resolved.text)
                        self.assertEqual(resolved.json()["profile"][field], new)
                        self.assertEqual(resolved.json()["status"], "ready")
                        self.assertEqual((await self.confirm(resolved.json()["revision"])).status_code, 200)
                        self.assertEqual(len(self.calls), 5 if pending else 4)

    async def test_d_enum_revocation_survives_empty_error_and_quota(self):
        modes = (
            ({}, completion(), 200, 3),
            ({}, httpx.Response(500), 502, 3),
            ({"max_calls": 2}, None, 429, 2),
        )
        for limits, reply, expected, calls in modes:
            with self.subTest(limits=limits, expected=expected):
                replies = [completion(), completion(FULL_PROFILE)]
                if reply is not None:
                    replies.append(reply)
                await self.setup_app(replies, **limits)
                await self.start()
                ready = (await self.send(READY_MESSAGE)).json()
                self.assertEqual((await self.confirm(ready["revision"])).status_code, 200)
                response = await self.send("我撤回新手这个回答，训练经验仍待核实")
                self.assertEqual(response.status_code, expected, response.text)
                current = await self.assert_not_adoptable(calls=calls)
                self.assertIsNone(current["profile"]["experience"])
                self.assertEqual(current["clarification_fields"], ["experience"])
                self.assertGreater(current["revision"], ready["revision"])

    async def test_combined_unbound_context_cannot_restore_numeric_or_enum(self):
        cases = (
            ("age", "21岁", 21), ("height_cm", "175厘米", 175),
            ("weight_kg", "70公斤", 70), ("days_per_week", "每周3天", 3),
            ("minutes_per_session", "每次30分钟", 30),
            ("experience", "新手", "beginner"), ("location", "健身房", "gym"),
            ("equipment", "哑铃", ["dumbbell"]), ("goal", "增肌", "muscle"),
            ("gender", "男性", "male"),
        )
        templates = ("引用样例。{answer}", "{answer}。我不确定", "{answer}。我撤回这个回答")
        for field, answer, value in cases:
            for template in templates:
                with self.subTest(field=field, template=template):
                    await self.setup_app([completion(), completion(FULL_PROFILE), completion({field: value})])
                    await self.start()
                    ready = (await self.send(READY_MESSAGE)).json()
                    self.assertEqual((await self.confirm(ready["revision"])).status_code, 200)
                    response = await self.send(template.format(answer=answer))
                    self.assertEqual(response.status_code, 502, response.text)
                    current = await self.assert_not_adoptable(calls=3)
                    self.assertIsNone(current["profile"][field])
                    self.assertIn(field, current["clarification_fields"])

    async def test_combined_symptom_qualification_cannot_keep_old_safety(self):
        for reply, limits, expected, calls in (
            (completion(), {}, 200, 3), (httpx.Response(500), {}, 502, 3),
            (None, {"max_calls": 2}, 429, 2),
        ):
            with self.subTest(limits=limits, expected=expected):
                replies = [completion(), completion(FULL_PROFILE)]
                if reply is not None:
                    replies.append(reply)
                await self.setup_app(replies, **limits)
                await self.start()
                ready = (await self.send(READY_MESSAGE)).json()
                self.assertEqual((await self.confirm(ready["revision"])).status_code, 200)
                response = await self.send("我运动时气短，但我不确定训练强度")
                self.assertEqual(response.status_code, expected, response.text)
                current = await self.assert_not_adoptable(calls=calls)
                self.assertIsNone(current["profile"]["current_discomfort"])
                self.assertEqual(current["profile"]["medical_restrictions"], "none")
                self.assertEqual(current["profile"]["age"], 21)

    async def test_combined_named_ambiguity_preserves_and_collects_other_fields(self):
        await self.setup_app([
            completion(), completion(FULL_PROFILE),
            completion({"age": 22}, text="MODEL_PROSE"),
            completion({"height_cm": 176, "weight_kg": 71}, text="MODEL_PROSE"),
            completion({"experience": "returning"}),
        ])
        await self.start()
        ready = (await self.send(READY_MESSAGE)).json()
        self.assertEqual((await self.confirm(ready["revision"])).status_code, 200)
        response = await self.send("训练经验不确定是否新手；年龄确定为22岁")
        self.assertEqual(response.status_code, 200, response.text)
        current = await self.assert_not_adoptable(calls=3)
        self.assertEqual(current["profile"]["age"], 22)
        self.assertIsNone(current["profile"]["experience"])
        self.assertEqual(current["clarification_fields"], ["experience"])
        self.assertEqual(current["missing_fields"], ["experience"])
        self.assertNotIn("年龄", current["assistant_text"])
        followup = await self.send("身高176厘米，体重71公斤")
        self.assertEqual(followup.status_code, 200, followup.text)
        current = await self.assert_not_adoptable(calls=4)
        self.assertEqual(current["profile"]["height_cm"], 176)
        self.assertEqual(current["profile"]["weight_kg"], 71)
        self.assertEqual(current["clarification_fields"], ["experience"])
        resolved = await self.send("训练经验确定为恢复训练")
        self.assertEqual(resolved.status_code, 200, resolved.text)
        self.assertEqual(resolved.json()["status"], "ready")
        self.assertEqual((await self.confirm(resolved.json()["revision"])).status_code, 200)
        self.assertEqual(len(self.calls), 5)

    async def test_combined_safety_revocation_precedes_quota(self):
        messages = (
            "没有当前不适。没有医学训练限制。我有当前不适",
            "这些是朋友的回答，不代表我的情况。没有当前不适。没有医学训练限制",
        )
        for message in messages:
            with self.subTest(message=message):
                await self.setup_app([completion(), completion(FULL_PROFILE)], max_calls=2)
                await self.start()
                ready = (await self.send(READY_MESSAGE)).json()
                self.assertEqual((await self.confirm(ready["revision"])).status_code, 200)
                response = await self.send(message)
                self.assertEqual(response.status_code, 429, response.text)
                current = await self.assert_not_adoptable(calls=2)
                self.assertIsNone(current["profile"]["current_discomfort"])
                self.assertGreater(current["revision"], ready["revision"])

    async def test_combined_pending_values_require_complete_affirmative_clauses(self):
        cases = (
            ("age", "年龄", "不满21岁", 21),
            ("height_cm", "身高", "不高于175厘米", 175),
            ("weight_kg", "体重", "不超过70公斤", 70),
            ("days_per_week", "频率", "每周不超过3天", 3),
            ("minutes_per_session", "时长", "每次不到30分钟", 30),
            ("experience", "经验", "尚非新手", "beginner"),
            ("location", "地点", "我不在健身房", "gym"),
            ("equipment", "器械", "哑铃只是样本", ["dumbbell"]),
            ("goal", "目标", "我并不想增肌", "muscle"),
            ("gender", "性别", "非男性", "male"),
        )
        for field, label, message, proposed in cases:
            with self.subTest(field=field, message=message):
                await self.setup_app([
                    completion(), completion(FULL_PROFILE), completion(), completion({field: proposed}),
                ])
                await self.start()
                ready = (await self.send(READY_MESSAGE)).json()
                self.assertEqual((await self.confirm(ready["revision"])).status_code, 200)
                withdrawn = await self.send(f"{label}撤回，仍待核实")
                self.assertEqual(withdrawn.status_code, 200, withdrawn.text)
                response = await self.send(message)
                self.assertEqual(response.status_code, 502, response.text)
                current = await self.assert_not_adoptable(calls=4)
                self.assertIsNone(current["profile"][field])
                self.assertIn(field, current["clarification_fields"])


if __name__ == "__main__":
    unittest.main()
