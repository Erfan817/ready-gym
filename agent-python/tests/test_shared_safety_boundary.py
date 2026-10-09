"""Whole-answer evidence boundary: actual ASGI/adapter, upstream fixture only."""
import unittest

import httpx
import test_intake
from test_intake import completion
from test_review_fixes import FULL_PROFILE, READY_MESSAGE


class SharedSafetyBoundary(unittest.IsolatedAsyncioTestCase):
    asyncSetUp = test_intake.IntakeContract.asyncSetUp
    setup_app = test_intake.IntakeContract.setup_app
    start = test_intake.IntakeContract.start
    send = test_intake.IntakeContract.send

    async def snapshot(self):
        response = await self.client.get(f"/api/intake/sessions/{self.sid}", headers=self.headers)
        self.assertEqual(200, response.status_code, response.text)
        return response.json()

    async def confirm(self, revision):
        return await self.client.post(
            f"/api/intake/sessions/{self.sid}/confirm",
            json={"confirmed": True, "expected_revision": revision}, headers=self.headers,
        )

    async def test_detached_qualification_cannot_restore_either_safety_field(self):
        denial = "没有当前不适。没有医学训练限制"
        qualifiers = ("这些回答待核实", "以上回答待确认", "这些回答待澄清")
        for was_ready in (False, True):
            for qualifier in qualifiers:
                for message in (f"{denial}。{qualifier}", f"{qualifier}；{denial}"):
                    for patch in (
                        {"current_discomfort": "none", "medical_restrictions": "none"},
                        {"current_discomfort": "none"},
                        {"medical_restrictions": "none"},
                    ):
                        with self.subTest(ready=was_ready, message=message, patch=patch):
                            base = FULL_PROFILE if was_ready else {
                                key: value for key, value in FULL_PROFILE.items()
                                if key not in {"current_discomfort", "medical_restrictions"}
                            }
                            await self.setup_app([completion(), completion(base), completion(patch)])
                            await self.start()
                            before = (await self.send(READY_MESSAGE if was_ready else "21岁175厘米70公斤想增肌，健身房哑铃，每周3天每次30分钟，新手")).json()
                            if was_ready:
                                self.assertEqual(200, (await self.confirm(before["revision"])).status_code)
                            rejected = await self.send(message)
                            self.assertEqual(502, rejected.status_code, rejected.text)
                            current = await self.snapshot()
                            self.assertEqual("collecting", current["status"])
                            self.assertFalse(current["confirmed"])
                            self.assertEqual({"current_discomfort", "medical_restrictions"}, set(current["clarification_fields"]))
                            for field in ("current_discomfort", "medical_restrictions"):
                                self.assertIsNone(current["profile"][field])
                            self.assertEqual(409, (await self.confirm(current["revision"])).status_code)
                            self.assertEqual(21, current["profile"]["age"])
                            self.assertEqual(3, len(self.calls))
                            self.assertEqual(3, current["calls_used"])

    async def test_qualification_remains_revoked_on_empty_error_and_budget(self):
        for reply, limits, expected, calls in (
            (completion(), {}, 200, 3),
            (httpx.Response(500), {}, 502, 3),
            (None, {"max_calls": 2}, 429, 2),
        ):
            with self.subTest(expected=expected):
                replies = [completion(), completion(FULL_PROFILE)]
                if reply is not None:
                    replies.append(reply)
                await self.setup_app(replies, **limits)
                await self.start()
                ready = (await self.send(READY_MESSAGE)).json()
                self.assertEqual(200, (await self.confirm(ready["revision"])).status_code)
                response = await self.send("没有当前不适。没有医学训练限制。这些回答待核实")
                self.assertEqual(expected, response.status_code, response.text)
                current = await self.snapshot()
                self.assertEqual("collecting", current["status"])
                self.assertFalse(current["confirmed"])
                self.assertEqual(409, (await self.confirm(current["revision"])).status_code)
                self.assertEqual(calls, len(self.calls))

    async def test_explicit_new_own_denial_resolves_only_after_qualification_is_absent(self):
        await self.setup_app([
            completion(), completion(FULL_PROFILE), completion(),
            completion({"current_discomfort": "none", "medical_restrictions": "none"}),
        ])
        await self.start()
        await self.send(READY_MESSAGE)
        await self.send("没有当前不适。没有医学训练限制。这些回答待核实")
        current = await self.snapshot()
        self.assertEqual(409, (await self.confirm(current["revision"])).status_code)
        resolved = await self.send("我没有当前不适。我没有医学训练限制")
        self.assertEqual(200, resolved.status_code, resolved.text)
        self.assertEqual("ready", resolved.json()["status"])
        self.assertEqual([], resolved.json()["clarification_fields"])
        self.assertEqual(200, (await self.confirm(resolved.json()["revision"])).status_code)
        self.assertEqual(4, len(self.calls))


if __name__ == "__main__":
    unittest.main()
