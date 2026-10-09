package com.readygym.app

import androidx.activity.compose.setContent
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoachChatScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private class TestOnlyCoachClient : CoachClient {
        var starts = 0
        var messages = 0
        var failMessage = true
        var confirmations = 0
        val session = CoachSession("test-session", "test-token")
        val startGate = CompletableDeferred<Unit>()
        val messageGate = CompletableDeferred<Unit>()
        var candidate: CoachCandidate? = null
        override suspend fun start(): CoachReply { starts++; startGate.await(); return CoachReply(session, "服务端首问：请说说年龄与目标", "collecting") }
        override suspend fun confirm(reply: CoachReply): CoachReply { confirmations++; return reply }
        override suspend fun delete(session: CoachSession) {}
        override suspend fun message(session: CoachSession, text: String): CoachReply {
            messages++; messageGate.await()
            if (failMessage) throw CoachException("network_error", true)
            return CoachReply(session, if (candidate == null) "服务端回复：还需要当前安全自述" else "服务端回复：资料已整理，请核对摘要", if (candidate == null) "collecting" else "ready", candidate)
        }
    }

    @Test fun consentIsRequiredThenServerQuestionAppearsAndRetryRetainsInput() {
        val client = TestOnlyCoachClient()
        compose.runOnUiThread { compose.activity.setContent { ReadyGymTheme { ReadyGymApp(client, serviceConfigured = true) } } }
        assertEquals(0, client.starts)
        compose.onNodeWithText("服务端首问：请说说年龄与目标").assertDoesNotExist()
        compose.onNodeWithText("同意并开始").performScrollTo().performClick()
        compose.onNodeWithText("正在连接…").assertIsNotEnabled()
        assertEquals(1, client.starts)
        compose.runOnIdle { client.startGate.complete(Unit) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("服务端首问：请说说年龄与目标").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, client.starts)
        compose.onNodeWithTag("coach-input").performTextInput("21岁，想增肌")
        compose.onNodeWithText("发送").performClick()
        compose.onNodeWithText("发送").assertIsNotEnabled()
        assertEquals(1, client.messages)
        compose.runOnIdle { client.messageGate.complete(Unit) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("重试").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("coach-input").assertTextContains("21岁，想增肌")
        compose.onNodeWithText("可能已计费，重试会再次发起请求", substring = true).assertExists()
        assertEquals(1, client.messages)
        capture("02-error-input-retained-test-fixture")
        client.failMessage = false
        compose.onNodeWithText("重试").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("服务端回复：还需要当前安全自述").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(2, client.messages)
        compose.onNodeWithTag("coach-input").assertTextContains("")
    }

    @Test fun summaryCanBeCorrectedAndOnlyExplicitConfirmationMapsIntoRulePlan() {
        val client = TestOnlyCoachClient().apply {
            failMessage = false; startGate.complete(Unit); messageGate.complete(Unit)
            candidate = CoachCandidate(com.readygym.core.TrainingProfile(age = 21, heightCm = 175.0, weightKg = 70.0,
                gender = com.readygym.core.Gender.UNDISCLOSED, goal = com.readygym.core.Goal.MUSCLE,
                location = com.readygym.core.Location.GYM, equipment = setOf(com.readygym.core.Equipment.DUMBBELL)),
                com.readygym.core.PlanningPreferences(2, 20, com.readygym.core.TrainingExperience.RETURNING, com.readygym.core.SafetyFeedback(screenCompleted = true)))
        }
        compose.runOnUiThread { compose.activity.setContent { ReadyGymTheme { ReadyGymApp(client, serviceConfigured = true) } } }
        compose.onNodeWithText("同意并开始").performScrollTo().performClick()
        compose.onNodeWithTag("coach-input").performTextInput("21岁，没有当前不适和医学训练限制")
        compose.onNodeWithText("发送").performClick()
        compose.onNodeWithText("请确认你的资料").performScrollTo().assertExists()
        capture("03-summary-test-fixture-not-live-AI")
        compose.onNodeWithText("安排你的训练周").assertDoesNotExist()
        compose.onNodeWithText("这些信息不对").performScrollTo().performClick()
        compose.onNodeWithText("确认资料，进入规则计划").assertDoesNotExist()
        compose.onNodeWithTag("coach-input").performTextInput("更正：每周2次，每次20分钟，恢复训练")
        compose.onNodeWithText("发送").performClick()
        compose.onNodeWithText("确认资料，进入规则计划").performScrollTo().performClick()
        compose.onNodeWithText("每周2天").performScrollTo().assertIsSelected()
        compose.onNodeWithText("20分钟").performScrollTo().assertIsSelected()
        compose.onNodeWithText("恢复训练").performScrollTo().assertIsSelected()
        compose.onNodeWithText("生成规则草稿").assertIsEnabled()
        compose.onNodeWithText("生成规则草稿").performClick()
        compose.onNodeWithText("RULE TEMPLATE · 未接模型 · 未专业审核").assertExists()
    }

    private fun openSafeChatDraft(): TestOnlyCoachClient {
        val client = TestOnlyCoachClient().apply {
            failMessage = false; startGate.complete(Unit); messageGate.complete(Unit)
            candidate = CoachCandidate(com.readygym.core.TrainingProfile(age = 21, heightCm = 175.0, weightKg = 70.0,
                gender = com.readygym.core.Gender.UNDISCLOSED, goal = com.readygym.core.Goal.MUSCLE,
                location = com.readygym.core.Location.GYM, equipment = setOf(com.readygym.core.Equipment.DUMBBELL)),
                com.readygym.core.PlanningPreferences(2, 20, com.readygym.core.TrainingExperience.RETURNING, com.readygym.core.SafetyFeedback(screenCompleted = true)))
        }
        compose.runOnUiThread { compose.activity.setContent { ReadyGymTheme { ReadyGymApp(client, true) } } }
        compose.onNodeWithText("同意并开始").performScrollTo().performClick()
        compose.onNodeWithTag("coach-input").performTextInput("21岁，没有当前不适和医学训练限制")
        compose.onNodeWithText("发送").performClick()
        compose.onNodeWithText("确认资料，进入规则计划").performScrollTo().performClick()
        compose.onNodeWithText("生成规则草稿").performClick()
        return client
    }

    private fun reportUrgentFeedback() {
        compose.onNodeWithText("身体不适").performClick()
        compose.onNodeWithText("胸痛、头晕、急性损伤或关节疼痛").performScrollTo().performClick()
        compose.onNodeWithText("没有医学训练限制").performScrollTo().performClick()
        compose.onNodeWithText("评估安全反馈").performClick()
        compose.onNodeWithText("关闭安全反馈").performClick()
    }

    private fun assertSummaryBlockedWithoutProviderConfirmation(client: TestOnlyCoachClient) {
        compose.onNodeWithText("确认资料，进入规则计划").performScrollTo().assertIsNotEnabled()
        val action = compose.onNodeWithText("确认资料，进入规则计划").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action!!
        val before = client.confirmations
        compose.runOnUiThread { action() }
        compose.onNodeWithText("安排你的训练周").assertDoesNotExist()
        assertEquals(before, client.confirmations)
    }

    @Test fun urgentRiskSurvivesNewSafeSummaryRestartClearAndActivityRecreation() {
        val client = openSafeChatDraft()
        reportUrgentFeedback()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertSummaryBlockedWithoutProviderConfirmation(client)
        compose.onNodeWithTag("coach-input").performTextInput("资料更正：仍无当前不适")
        compose.onNodeWithText("发送").performClick()
        assertSummaryBlockedWithoutProviderConfirmation(client)
        compose.activityRule.scenario.recreate()
        compose.runOnUiThread { compose.activity.setContent { ReadyGymTheme { ReadyGymApp(client, true) } } }
        assertSummaryBlockedWithoutProviderConfirmation(client)
        val state = compose.activity.viewModelStore["coach-test-${System.identityHashCode(client)}"] as CoachConversation
        compose.runOnIdle { state.restart() }
        assertEquals(1, client.starts)
        compose.onNodeWithText("同意并开始").performScrollTo().performClick()
        compose.onNodeWithTag("coach-input").performTextInput("再次提交无风险资料")
        compose.onNodeWithText("发送").performClick()
        assertSummaryBlockedWithoutProviderConfirmation(client)
        compose.onNodeWithText("清空对话并删除服务端会话").performScrollTo().performClick()
        compose.onNodeWithText("本次会话已记录身体风险", substring = true).assertExists()
        compose.onNodeWithText("同意并开始").performScrollTo().performClick()
        compose.onNodeWithTag("coach-input").performTextInput("清空后无风险资料")
        compose.onNodeWithText("发送").performClick()
        assertSummaryBlockedWithoutProviderConfirmation(client)
    }

    @Test fun urgentRiskCannotBeClearedByOfflineReentryAndSafeForm() {
        openSafeChatDraft()
        reportUrgentFeedback()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("离线规则模式（不使用AI）").performScrollTo().performClick()
        compose.onNodeWithContentDescription("年龄（岁）").performScrollTo().performTextInput("30")
        compose.onNodeWithContentDescription("身高（cm）").performScrollTo().performTextInput("170")
        compose.onNodeWithContentDescription("体重（kg）").performScrollTo().performTextInput("65")
        compose.onNodeWithContentDescription("体重（kg）").performImeAction()
        compose.onNodeWithText("下一步").performClick()
        compose.onNodeWithText("增肌").performScrollTo().performClick()
        compose.onNodeWithText("下一步").performClick()
        compose.onNodeWithText("家").performScrollTo().performClick()
        compose.onNodeWithText("哑铃").performScrollTo().performClick()
        compose.onNodeWithText("下一步").performClick()
        compose.onNodeWithText("确认本次资料").performClick()
        compose.onNodeWithText("制定训练计划").performClick()
        compose.onNodeWithText("生成规则草稿").assertIsNotEnabled()
        compose.onNodeWithText("没有当前不适或危险信号").performScrollTo().performClick()
        compose.onNodeWithText("没有医学训练限制").performScrollTo().performClick()
        compose.onNodeWithText("生成规则草稿").performClick()
        compose.onNodeWithText("停止训练，不用替换动作继续", substring = true).assertExists()
        compose.onNodeWithText("RULE TEMPLATE · 未接模型 · 未专业审核").assertDoesNotExist()
    }

    @Test fun changedSafetyIsRetainedEvenWhenFeedbackDialogIsDismissedWithoutEvaluation() {
        val client = openSafeChatDraft()
        compose.onNodeWithText("身体不适").performClick()
        compose.onNodeWithText("胸痛、头晕、急性损伤或关节疼痛").performScrollTo().performClick()
        compose.onNodeWithText("关闭安全反馈").performClick()
        compose.onNodeWithText("确认采用 v1").assertIsNotEnabled()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertSummaryBlockedWithoutProviderConfirmation(client)
    }

    @Test fun urgentFeedbackCannotBeBypassedByReturningToOldSafeCoachSummary() {
        openSafeChatDraft()
        compose.onNodeWithText("身体不适").performClick()
        compose.onNodeWithText("胸痛、头晕、急性损伤或关节疼痛").performScrollTo().performClick()
        compose.onNodeWithText("没有医学训练限制").performScrollTo().performClick()
        compose.onNodeWithText("评估安全反馈").performClick()
        compose.onNodeWithText("关闭安全反馈").performClick()
        compose.onNodeWithText("确认采用 v1").assertIsNotEnabled()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("确认资料，进入规则计划").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("本次会话已记录身体风险", substring = true).assertExists()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val target = java.io.File("../build/verification/chat-tdd/screenshots/$name.png")
            target.parentFile?.mkdirs()
            target.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun rapidRetryDoesNotDuplicateAnInFlightRequest() {
        val gate = CompletableDeferred<Unit>(); var calls = 0
        val client = object : CoachClient {
            override suspend fun start(): CoachReply {
                calls++
                if (calls == 1) throw CoachException("network_error", true)
                gate.await()
                return CoachReply(CoachSession("session", "token"), "首问", "collecting")
            }
            override suspend fun message(session: CoachSession, text: String) = throw CoachException("network_error")
        }
        val state = CoachConversation(client)
        compose.runOnIdle { state.start(); state.retry(); state.retry() }
        assertEquals(2, calls)
        compose.runOnIdle { gate.complete(Unit) }
    }

    @Test fun serverPolicyTextIsNotMislabelledAsRawModelReply() {
        val client = object : CoachClient {
            override suspend fun start() = CoachReply(CoachSession("session", "token"), "只追问缺失字段", "collecting", responseSource = "server_policy")
            override suspend fun message(session: CoachSession, text: String) = throw CoachException("network_error")
        }
        compose.runOnUiThread { compose.activity.setContent { ReadyGymTheme { ReadyGymApp(client, true) } } }
        compose.onNodeWithText("同意并开始").performScrollTo().performClick()
        compose.onNodeWithText("教练 · 服务端整理").assertExists()
        compose.onNodeWithText("AI教练 · 服务端回复").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun largeTextWelcomeAndOfflineButtonRemainReachable() {
        compose.runOnUiThread { compose.activity.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 1.6f)) {
                ReadyGymTheme { ReadyGymApp() }
            }
        } }
        compose.onNodeWithText("服务未配置，不能连接AI").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("同意并开始").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("离线规则模式（不使用AI）").performScrollTo().assertIsDisplayed()
        capture("04-small-large-font")
    }

    @Test
    @Config(qualifiers = "w640dp-h320dp-land")
    fun landscapeWelcomeKeepsConsentAndOfflineActionScrollable() {
        compose.onNodeWithText("同意并开始").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("离线规则模式（不使用AI）").performScrollTo().assertIsDisplayed()
        capture("05-landscape")
    }

    @Test fun clearConversationDeletesServerSessionBeforeClearingLocalData() {
        var deleted = 0
        val gate = CompletableDeferred<Unit>()
        val client = object : CoachClient {
            override suspend fun start() = CoachReply(CoachSession("session", "token"), "首问", "collecting")
            override suspend fun message(session: CoachSession, text: String) = throw CoachException("network_error")
            override suspend fun delete(session: CoachSession) { deleted++; gate.await() }
        }
        compose.runOnUiThread { compose.activity.setContent { ReadyGymTheme { ReadyGymApp(client, true) } } }
        compose.onNodeWithText("同意并开始").performScrollTo().performClick()
        compose.onNodeWithTag("coach-input").performTextInput("待发送资料")
        compose.onNodeWithText("清空对话并删除服务端会话").performScrollTo().performClick()
        assertEquals(1, deleted)
        compose.onNodeWithTag("coach-input").assertTextContains("待发送资料")
        compose.runOnIdle { gate.complete(Unit) }
        compose.onNodeWithText("同意并开始").performScrollTo().assertExists()
        compose.onNodeWithText("首问").assertDoesNotExist()
    }

    @Test fun clearRecoversAfterTransientReadbackOnlyWhenExactGetActuallyReturns404() {
        val start = """{"session_id":"review-target","session_token":"fixture-token","assistant_text":"保留首问","status":"collecting","provider_mode":"deepseek_official","safety_route":null}"""
        val gone = """{"error":{"code":"session_not_found"}}"""
        ScriptedLoopbackFixture(listOf(200 to start, 204 to "", 503 to "{}", 404 to gone, 404 to gone)).use { server ->
            compose.runOnUiThread { compose.activity.setContent { ReadyGymTheme { ReadyGymApp(HttpCoachClient(server.url, true), true) } } }
            compose.onNodeWithText("同意并开始").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("保留首问").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("coach-input").performTextInput("未发送资料")
            compose.onNodeWithText("清空对话并删除服务端会话").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("重试").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("保留首问").assertExists()
            compose.onNodeWithTag("coach-input").assertTextContains("未发送资料")
            compose.onNodeWithText("重试").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("同意并开始").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("保留首问").assertDoesNotExist()
            compose.onNodeWithTag("coach-input").assertTextEquals("", "告诉教练（最多1000字）")
            assertEquals(listOf("POST", "DELETE", "GET", "DELETE", "GET"), server.requests.get(6, java.util.concurrent.TimeUnit.SECONDS).map { it.substringBefore(' ') })
        }
    }

    @Test fun forged500DeleteReadbackKeepsConversationAndUnsentInput() {
        val start = """{"session_id":"review-target","session_token":"fixture-token","assistant_text":"不能丢失的首问","status":"collecting","provider_mode":"deepseek_official","safety_route":null}"""
        ScriptedLoopbackFixture(listOf(200 to start, 204 to "", 500 to """{"error":{"code":"session_not_found"}}""")).use { server ->
            compose.runOnUiThread { compose.activity.setContent { ReadyGymTheme { ReadyGymApp(HttpCoachClient(server.url, true), true) } } }
            compose.onNodeWithText("同意并开始").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("不能丢失的首问").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("coach-input").performTextInput("仍需保留的输入")
            compose.onNodeWithText("清空对话并删除服务端会话").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("重试").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("不能丢失的首问").assertExists()
            compose.onNodeWithTag("coach-input").assertTextContains("仍需保留的输入")
            compose.onNodeWithText("同意并开始").assertDoesNotExist()
            assertEquals(listOf("POST", "DELETE", "GET"), server.requests.get(6, java.util.concurrent.TimeUnit.SECONDS).map { it.substringBefore(' ') })
        }
    }

    @Test fun namedSessionUnauthorizedCodeOffersFreshConsentWithoutAutoStart() {
        val client = object : CoachClient {
            var starts = 0
            override suspend fun start(): CoachReply { starts++; return CoachReply(CoachSession("session-$starts", "token"), "首问$starts", "collecting") }
            override suspend fun message(session: CoachSession, text: String): CoachReply = throw CoachException("session_unauthorized")
        }
        compose.runOnUiThread { compose.activity.setContent { ReadyGymTheme { ReadyGymApp(client, true) } } }
        compose.onNodeWithText("同意并开始").performScrollTo().performClick()
        compose.onNodeWithTag("coach-input").performTextInput("待重新发送的回答")
        compose.onNodeWithText("发送").performClick()
        compose.onNodeWithText("会话授权已失效", substring = true).assertExists()
        compose.onNodeWithText("重新开始会话").performClick()
        compose.onNodeWithTag("coach-input").assertTextContains("待重新发送的回答")
        assertEquals(1, client.starts)
        compose.onNodeWithText("同意并开始").performScrollTo().performClick()
        assertEquals(2, client.starts)
        compose.onNodeWithTag("coach-input").assertTextContains("待重新发送的回答")
    }

    @Test fun unauthorizedSessionOffersExplicitFreshConsentWithoutLosingInput() {
        val first = """{"session_id":"first","session_token":"fixture-token","assistant_text":"首问1","status":"collecting","provider_mode":"deepseek_official","safety_route":null}"""
        val second = first.replace("first", "second").replace("首问1", "首问2")
        ScriptedLoopbackFixture(listOf(200 to first, 401 to """{"error":{"code":"session_unauthorized"}}""", 200 to second)).use { server ->
            val client = HttpCoachClient(server.url, true)
            compose.runOnUiThread { compose.activity.setContent { ReadyGymTheme { ReadyGymApp(client, true) } } }
            compose.onNodeWithText("同意并开始").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("首问1").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("coach-input").performTextInput("21 years old, gain muscle")
            compose.onNodeWithText("发送").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("正在等待服务端…").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("重新开始会话").assertExists()
            compose.onNodeWithText("会话授权已失效", substring = true).assertExists()
            compose.onNodeWithText("重新开始会话").performClick()
            compose.onNodeWithText("会话授权已失效", substring = true).assertDoesNotExist()
            compose.onNodeWithTag("coach-input").assertTextContains("21 years old, gain muscle")
            compose.onNodeWithText("同意并开始").performScrollTo().assertExists()
            assertFalse("restart must not issue a new request before consent", server.requests.isDone)
            compose.onNodeWithText("同意并开始").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("首问2").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("coach-input").assertTextContains("21 years old, gain muscle")
            val requests = server.requests.get(6, java.util.concurrent.TimeUnit.SECONDS)
            assertEquals(3, requests.size)
            assertTrue(requests[1].startsWith("POST /api/intake/sessions/first/messages HTTP/1.1"))
            assertTrue(requests[2].startsWith("POST /api/intake/sessions HTTP/1.1"))
        }
    }

    @Test fun expiredSessionCanRestartWithFreshConsentWithoutLosingInput() {
        val client = object : CoachClient {
            var starts = 0
            override suspend fun start(): CoachReply { starts++; return CoachReply(CoachSession("session-$starts", "token"), "首问$starts", "collecting") }
            override suspend fun message(session: CoachSession, text: String): CoachReply = throw CoachException("session_expired")
        }
        compose.runOnUiThread { compose.activity.setContent { ReadyGymTheme { ReadyGymApp(client, true) } } }
        compose.onNodeWithText("同意并开始").performScrollTo().performClick()
        compose.onNodeWithTag("coach-input").performTextInput("21岁，想增肌")
        compose.onNodeWithText("发送").performClick()
        compose.onNodeWithText("重新开始会话").performClick()
        compose.onNodeWithText("同意并开始").performScrollTo().assertExists()
        compose.onNodeWithTag("coach-input").assertTextContains("21岁，想增肌")
        compose.onNodeWithText("同意并开始").performClick()
        assertEquals(2, client.starts)
        compose.onNodeWithTag("coach-input").assertTextContains("21岁，想增肌")
    }

    @Test fun activityRecreationRetainsConversationAndTypedInput() {
        val client = TestOnlyCoachClient().apply { startGate.complete(Unit) }
        compose.runOnUiThread { compose.activity.setContent { ReadyGymTheme { ReadyGymApp(client, true) } } }
        compose.onNodeWithText("同意并开始").performScrollTo().performClick()
        compose.onNodeWithTag("coach-input").performTextInput("回答尚未发送")
        compose.activityRule.scenario.recreate()
        compose.runOnUiThread { compose.activity.setContent { ReadyGymTheme { ReadyGymApp(client, true) } } }
        compose.onNodeWithTag("coach-input").assertTextContains("回答尚未发送")
        compose.onNodeWithText("服务端首问：请说说年龄与目标").assertExists()
        assertEquals(1, client.starts)
    }

    @Test fun firstLaunchIsConversationNotMandatoryOnboarding() {
        capture("01-welcome-unconfigured")
        compose.onNodeWithText("你的AI训练教练").assertIsDisplayed()
        compose.onNodeWithText("服务未配置，不能连接AI").assertExists()
        compose.onNodeWithContentDescription("年龄（岁）").assertDoesNotExist()
        compose.onNodeWithText("同意并开始").assertExists()
        compose.onNodeWithText("离线规则模式（不使用AI）").performScrollTo().performClick()
        compose.onNodeWithContentDescription("年龄（岁）").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("你的AI训练教练").assertExists()
    }
}
