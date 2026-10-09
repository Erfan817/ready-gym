package com.readygym.app

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Host Compose interactions using clearly labelled server fixtures, not live AI. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FoundationChatTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private class FixtureClient : CoachClient {
        var confirmations = 0
        var planConfirmations = 0
        var snapshots = 0
        var active: String? = null
        var proposal: String? = null
        var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        override val supportsFoundation = true
        private fun result(confirmed: Boolean = true) = FoundationContract.extras(
            JSONObject(FoundationFixtures.reply(proposal = proposal, active = active, confirmed = confirmed)),
            CoachReply(FoundationFixtures.session, "fixture 服务端整理", "ready", FoundationFixtures.candidate, 1, confirmed, responseSource = "server_policy"))
        override suspend fun start() = CoachReply(FoundationFixtures.session, "fixture 首问", "collecting")
        override suspend fun message(session: CoachSession, text: String): CoachReply {
            if (active != null) proposal = FoundationFixtures.plan("draft-two", 2, days = 4, basedOn = 1)
            return result(confirmed = active != null)
        }
        override suspend fun confirm(reply: CoachReply): CoachReply {
            confirmations++; proposal = FoundationFixtures.plan()
            return result()
        }
        override suspend fun confirmPlan(reply: CoachReply): CoachReply {
            planConfirmations++; gate?.await()
            val plan = requireNotNull(reply.planProposal)
            active = FoundationFixtures.plan(plan.id, plan.revision, "confirmed", plan.daysPerWeek, plan.basedOnRevision)
            proposal = null
            return result()
        }
        override suspend fun readFoundation(session: CoachSession): CoachReply { snapshots++; return result() }
    }
    @Test fun revisionRemainsPendingAndPreservesActiveVersionUntilUserConfirms() {
        val client = FixtureClient()
        compose.runOnUiThread { compose.activity.setContent { ReadyGymTheme { ReadyGymApp(client, true) } } }
        compose.onNodeWithText("同意并开始").performScrollTo().performClick()
        compose.onNodeWithTag("coach-input").performTextInput("fixture complete safe profile")
        compose.onNodeWithText("发送").performClick()
        compose.onAllNodes(hasText("确认资料", substring = true) and hasClickAction()).onFirst().performScrollTo().performClick()
        compose.onNodeWithTag("foundation-confirm-plan").performScrollTo().performClick()
        compose.onNodeWithText("当前生效 v1").performScrollTo().assertExists()
        compose.onNodeWithTag("coach-input").performTextInput("周三没空，改成四天")
        compose.onNodeWithText("发送").performClick()
        compose.onNodeWithText("待确认 v2").performScrollTo().assertExists()
        compose.onNodeWithText("当前生效 v1").performScrollTo().assertExists()
        compose.onNodeWithText("修订差异 · 原生效版未改变").performScrollTo().assertExists()
        capture("03-inline-revision-diff-fixture")
        assertEquals(1, client.planConfirmations)
        compose.onNodeWithTag("coach-input").performTextInput("unsent correction")
        compose.onNodeWithTag("foundation-confirm-plan").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("coach-input").performTextClearance()
        compose.onNodeWithTag("foundation-confirm-plan").performScrollTo().performClick()
        compose.onNodeWithText("当前生效 v2").performScrollTo().assertExists()
        assertEquals(2, client.planConfirmations)
    }
    @Test fun explicitProfileConfirmationKeepsChatAndShowsServerDraftAndExactDemo() {
        val client = FixtureClient()
        compose.runOnUiThread { compose.activity.setContent { ReadyGymTheme { ReadyGymApp(client, true) } } }
        compose.onNodeWithText("同意并开始").performScrollTo().performClick()
        compose.onNodeWithTag("coach-input").performTextInput("fixture complete safe profile")
        compose.onNodeWithText("发送").performClick()
        compose.onNodeWithText("请确认你的资料").performScrollTo().assertExists()
        val button = compose.onAllNodes(hasText("确认资料", substring = true) and hasClickAction()).onFirst()
        button.performScrollTo().performClick()
        compose.onNodeWithTag("coach-input").assertExists()
        compose.onNodeWithText("安排你的训练周").assertDoesNotExist()
        compose.onNodeWithText("待确认 v1").performScrollTo().assertExists()
        compose.onNodeWithText("未经专业审核", substring = true).onFirstIfMultiple()
        compose.onNodeWithTag("foundation-exercise-d1-s1").performScrollTo().performClick()
        compose.onNodeWithText("动作详情：徒手深蹲").assertExists()
        compose.onNodeWithText("播放动作演示").performScrollTo().assertExists()
        capture("01-exact-gif-detail-fixture", dialog = true)
        compose.onNodeWithText("关闭详情").performClick()
        compose.onNodeWithText("待确认 v1").performScrollTo()
        capture("02-inline-server-draft-fixture")
        assertEquals(1, client.confirmations)
        compose.onNodeWithTag("foundation-confirm-plan").performScrollTo().performClick()
        compose.onNodeWithText("当前生效 v1").performScrollTo().assertExists()
        assertEquals(1, client.planConfirmations)
        compose.onNodeWithText("读回确认状态（不重复写入）").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(1, client.snapshots)
    }
    @Test fun unknownConfirmationRecoveryKeepsUnsentInputAndReadOnlyRetryOverRealHttp() {
        compose.waitForIdle()
        val start = """{"session_id":"foundation-session","session_token":"fixture-token","assistant_text":"fixture 首问","status":"collecting","provider_mode":"deepseek_official","safety_route":null}"""
        val draft = FoundationFixtures.reply(proposal = FoundationFixtures.plan())
        val accepted = FoundationFixtures.reply(active = FoundationFixtures.plan(status = "confirmed"))
        ScriptedLoopbackFixture(listOf(200 to start, 200 to draft, null to "", 200 to accepted)).use { server ->
            val coach = CoachConversation(HttpCoachClient(server.url, true, true))
            compose.runOnUiThread { compose.activity.setContent {
                ReadyGymTheme { CoachChatScreen(coach, true, {}, {}) }
            } }
            compose.onNodeWithText("同意并开始").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("fixture 首问").fetchSemanticsNodes().isNotEmpty() || coach.error != null }
            compose.runOnIdle { assertNull("start error: ${coach.error?.code}", coach.error) }
            compose.onNodeWithTag("coach-input").performTextInput("fixture safe profile")
            compose.onNodeWithText("发送").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("待确认 v1").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("foundation-confirm-plan").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("上次确认结果未知", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("coach-input").performTextInput("保留这条未发送修改")
            compose.onNodeWithText("发送").assertIsNotEnabled()
            compose.onNodeWithText("读回确认状态（不重复写入）").performScrollTo().assertIsEnabled()
            compose.onNodeWithText("重试").assertExists().assertIsEnabled()
            compose.runOnIdle {
                assertNotNull("editing unknown must keep the readback recovery action and error", coach.error)
                coach.recordSafety(com.readygym.core.SafetyFeedback(screenCompleted = true, currentDiscomfort = "胸痛"))
                coach.retry()
                assertTrue(coach.busy)
                coach.retry(); coach.readFoundation(); coach.confirmPlan(); coach.send(); coach.edit("must not replace while busy")
                assertEquals("保留这条未发送修改", coach.input)
            }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("当前生效 v1").fetchSemanticsNodes().isNotEmpty() }
            compose.runOnIdle {
                assertEquals("保留这条未发送修改", coach.input)
                assertEquals("confirmed", coach.reply?.activePlan?.status)
                assertTrue(coach.localSafetyBlocked)
                assertEquals(com.readygym.core.SafetyRoute.STOP_AND_SEEK_CARE,
                    com.readygym.core.PlanningEngine().evaluateSafety(coach.retainedSafety!!).route)
            }
            val calls = server.requests.get(6, java.util.concurrent.TimeUnit.SECONDS)
            assertEquals(listOf("POST", "POST", "POST", "GET"), calls.map { it.substringBefore(' ') })
            assertTrue(calls[2].startsWith("POST /api/intake/sessions/foundation-session/foundation/confirm-plan HTTP/1.1"))
            assertTrue(calls[3].startsWith("GET /api/intake/sessions/foundation-session/foundation HTTP/1.1"))
        }
    }
    private fun SemanticsNodeInteraction.onFirstIfMultiple() = Unit
    private fun capture(name: String, dialog: Boolean = false) {
        compose.waitForIdle()
        compose.runOnIdle {
            val view = if (dialog) org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView else compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val target = java.io.File("../build/verification/v0.4.0/foundation/screenshots/$name.png")
            target.parentFile?.mkdirs()
            target.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
