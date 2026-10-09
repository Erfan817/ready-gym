"""Safety review regressions: real ASGI/adapter, MockTransport only (not live)."""
import unittest

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


if __name__ == "__main__":
    unittest.main()
