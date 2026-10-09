package com.readygym.app

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.readygym.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real HTTP parser + real Activity-scoped conversation + real offline planning. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoachStructuredSafetyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val first = """{"session_id":"risk-session","session_token":"fixture-token","assistant_text":"服务端首问","status":"collecting","provider_mode":"deepseek_official","safety_route":null}"""
    private fun blocked(route: String) = """{"session_id":"risk-session","assistant_text":"服务端已选择安全路径","status":"blocked","provider_mode":"deepseek_official","safety_route":"$route","validated_profile":{"current_discomfort":"胸痛"}}"""

    private fun open(client: CoachClient) {
        compose.runOnUiThread { compose.activity.setContent { ReadyGymTheme { ReadyGymApp(client, true) } } }
        compose.onNodeWithText("同意并开始").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("服务端首问").fetchSemanticsNodes().isNotEmpty() }
        // No local symptom keyword: only the structured server route may establish risk.
        compose.onNodeWithTag("coach-input").performTextInput("please review this answer")
        compose.onNodeWithText("发送").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("服务端已选择安全路径").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun completeOfflineSafeForm() {
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
        compose.onNodeWithText("没有当前不适或危险信号").performScrollTo().performClick()
        compose.onNodeWithText("没有医学训练限制").performScrollTo().performClick()
        compose.onNodeWithText("生成规则草稿").performClick()
    }

    @Test fun actualHttpBlockedMessageCannotBecomeOfflineDraft() {
        ScriptedLoopbackFixture(listOf(201 to first, 200 to blocked("stop_and_seek_care"))).use { server ->
            val client = HttpCoachClient(server.url, true)
            open(client)
            completeOfflineSafeForm()
            compose.onNodeWithText("RULE TEMPLATE · 未接模型 · 未专业审核").assertDoesNotExist()
            compose.onNodeWithText("确认采用 v1").assertDoesNotExist()
            compose.onNodeWithText("停止训练，不用替换动作继续", substring = true).assertExists()
            val state = compose.activity.viewModelStore["coach-test-${System.identityHashCode(client)}"] as CoachConversation
            assertEquals(SafetyRoute.STOP_AND_SEEK_CARE, PlanningEngine().evaluateSafety(requireNotNull(state.retainedSafety)).route)
            val calls = server.requests.get(6, java.util.concurrent.TimeUnit.SECONDS)
            assertEquals(2, calls.size)
            assertTrue(calls[0].startsWith("POST /api/intake/sessions HTTP/1.1"))
            assertTrue(calls[1].startsWith("POST /api/intake/sessions/risk-session/messages HTTP/1.1"))
        }
    }

    @Test fun malformedOrStatusInconsistentSafetyContractNeverYieldsAnAdoptableReply() = runBlocking {
        val invalid = listOf(
            blocked("stop_and_seek_care").replace(",\"safety_route\":\"stop_and_seek_care\"", ""),
            blocked("stop_and_seek_care").replace("\"stop_and_seek_care\"", "null"),
            blocked("stop_and_seek_care").replace("\"stop_and_seek_care\"", "7"),
            blocked("stop_and_seek_care").replace("\"stop_and_seek_care\"", "{}"),
            blocked("stop_and_seek_care").replace("\"blocked\"", "\"collecting\""),
            blocked("professional_review").replace("\"blocked\"", "\"ready\""),
            first.replace(",\"safety_route\":null", ""),
        )
        for (body in invalid) LoopbackFixture(body).use { server ->
            try {
                HttpCoachClient(server.url, true).message(CoachSession("risk-session", "fixture-token"), "review")
                fail("missing/type/status mismatch must fail closed: $body")
            } catch (e: CoachException) { assertEquals("invalid_response", e.code) }
        }
    }

    @Test fun invalidBlockedRouteCannotBeBypassedByOfflineSafeForm() {
        ScriptedLoopbackFixture(listOf(201 to first, 200 to blocked("unknown_future_route"))).use { server ->
            val client = HttpCoachClient(server.url, true)
            compose.runOnUiThread { compose.activity.setContent { ReadyGymTheme { ReadyGymApp(client, true) } } }
            compose.onNodeWithText("同意并开始").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("服务端首问").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("coach-input").performTextInput("review this answer")
            compose.onNodeWithText("发送").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("服务端响应不符合契约", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("coach-input").assertTextContains("review this answer")
            completeOfflineSafeForm()
            compose.onNodeWithText("RULE TEMPLATE · 未接模型 · 未专业审核").assertDoesNotExist()
            compose.onNodeWithText("确认采用 v1").assertDoesNotExist()
            val state = compose.activity.viewModelStore["coach-test-${System.identityHashCode(client)}"] as CoachConversation
            assertEquals(SafetyRoute.PROFESSIONAL_REVIEW, PlanningEngine().evaluateSafety(requireNotNull(state.retainedSafety)).route)
        }
    }

    private val safeProfile = """{"age":30,"height_cm":170,"weight_kg":65,"goal":"muscle","location":"home","equipment":["dumbbell"],"days_per_week":3,"minutes_per_session":30,"experience":"beginner","current_discomfort":"none","medical_restrictions":"none"}"""
    private fun ready(confirmed: Boolean = false) = """{"session_id":"risk-session","assistant_text":"新的无风险摘要","status":"ready","provider_mode":"deepseek_official","safety_route":null,"validated_profile":$safeProfile,"missing_fields":[],"clarification_fields":[],"revision":1,"confirmed":$confirmed}"""

    @Test fun actualConfirmPostCannotDiscardNewStopRoute() {
        assertConfirmSafetyLatches(readback = false)
    }

    @Test fun actualConfirmGetReadbackCannotDiscardNewStopRoute() {
        assertConfirmSafetyLatches(readback = true)
    }

    private fun assertConfirmSafetyLatches(readback: Boolean) {
        val responses = mutableListOf<Pair<Int?, String>>(201 to first, 200 to ready())
        if (readback) responses.add(200 to ready(confirmed = true))
        responses.add(200 to blocked("stop_and_seek_care"))
        ScriptedLoopbackFixture(responses).use { server ->
            val client = HttpCoachClient(server.url, true)
            compose.runOnUiThread { compose.activity.setContent { ReadyGymTheme { ReadyGymApp(client, true) } } }
            compose.onNodeWithText("同意并开始").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("服务端首问").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("coach-input").performTextInput("complete profile")
            compose.onNodeWithText("发送").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("新的无风险摘要").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("确认资料，进入规则计划").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("正在等待服务端…").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("安排你的训练周").assertDoesNotExist()
            val state = compose.activity.viewModelStore["coach-test-${System.identityHashCode(client)}"] as CoachConversation
            assertNotNull("confirm response safety must survive a rejected confirmation", state.retainedSafety)
            assertEquals(SafetyRoute.STOP_AND_SEEK_CARE, PlanningEngine().evaluateSafety(requireNotNull(state.retainedSafety)).route)
            completeOfflineSafeForm()
            compose.onNodeWithText("RULE TEMPLATE · 未接模型 · 未专业审核").assertDoesNotExist()
            val calls = server.requests.get(6, java.util.concurrent.TimeUnit.SECONDS)
            assertTrue(calls[2].startsWith("POST /api/intake/sessions/risk-session/confirm HTTP/1.1"))
            if (readback) assertTrue(calls[3].startsWith("GET /api/intake/sessions/risk-session HTTP/1.1"))
        }
    }

    private fun state(client: CoachClient) = compose.activity.viewModelStore["coach-test-${System.identityHashCode(client)}"] as CoachConversation
    private fun assertRetainedRoute(client: CoachClient, route: SafetyRoute) {
        assertNotNull(state(client).retainedSafety)
        assertEquals(route, PlanningEngine().evaluateSafety(requireNotNull(state(client).retainedSafety)).route)
    }
    private fun sendSafeSummary() {
        compose.onNodeWithTag("coach-input").performTextInput("fresh symptom-free report")
        compose.onNodeWithText("发送").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("新的无风险摘要").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun assertOldSummaryCannotConfirm(client: CoachClient) {
        compose.onNodeWithText("确认资料，进入规则计划").performScrollTo().assertIsNotEnabled()
        val staleAction = compose.onNodeWithText("确认资料，进入规则计划").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action!!
        compose.runOnUiThread { staleAction() }
        compose.onNodeWithText("安排你的训练周").assertDoesNotExist()
        assertRetainedRoute(client, SafetyRoute.STOP_AND_SEEK_CARE)
    }

    @Test fun actualServerStopSurvivesSafeSummaryClearRestartAndActivityRecreate() {
        val gone = """{"error":{"code":"session_not_found"}}"""
        val replies = listOf(201 to first, 200 to blocked("stop_and_seek_care"), 200 to ready(),
            204 to "", 404 to gone, 201 to first, 200 to ready(), 201 to first, 200 to ready())
        ScriptedLoopbackFixture(replies).use { server ->
            val client = HttpCoachClient(server.url, true)
            open(client)
            assertRetainedRoute(client, SafetyRoute.STOP_AND_SEEK_CARE)
            sendSafeSummary()
            assertOldSummaryCannotConfirm(client)
            compose.onNodeWithText("清空对话并删除服务端会话").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("同意并开始").fetchSemanticsNodes().isNotEmpty() }
            assertRetainedRoute(client, SafetyRoute.STOP_AND_SEEK_CARE)
            compose.onNodeWithText("同意并开始").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("服务端首问").fetchSemanticsNodes().isNotEmpty() }
            sendSafeSummary()
            assertOldSummaryCannotConfirm(client)
            compose.runOnIdle { state(client).restart() }
            assertRetainedRoute(client, SafetyRoute.STOP_AND_SEEK_CARE)
            assertFalse("restart itself must not start the next HTTP request", server.requests.isDone)
            compose.onNodeWithText("同意并开始").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("服务端首问").fetchSemanticsNodes().isNotEmpty() }
            sendSafeSummary()
            val before = state(client)
            compose.activityRule.scenario.recreate()
            compose.runOnUiThread { compose.activity.setContent { ReadyGymTheme { ReadyGymApp(client, true) } } }
            assertSame(before, state(client))
            assertOldSummaryCannotConfirm(client)
            completeOfflineSafeForm()
            compose.onNodeWithText("RULE TEMPLATE · 未接模型 · 未专业审核").assertDoesNotExist()
            compose.onNodeWithText("确认采用 v1").assertDoesNotExist()
            assertEquals(listOf("POST", "POST", "POST", "DELETE", "GET", "POST", "POST", "POST", "POST"),
                server.requests.get(6, java.util.concurrent.TimeUnit.SECONDS).map { it.substringBefore(' ') })
        }
    }

    @Test fun professionalRouteBlocksOfflineWithoutBeingUpgradedFromProfileOrProse() {
        // Fixture profile contains 胸痛 but the validated route alone is authoritative here.
        ScriptedLoopbackFixture(listOf(201 to first, 200 to blocked("professional_review"))).use { server ->
            val client = HttpCoachClient(server.url, true)
            open(client)
            assertRetainedRoute(client, SafetyRoute.PROFESSIONAL_REVIEW)
            compose.onNodeWithText("服务端安全路线：暂停普通训练并寻求专业评估，不作诊断。").assertExists()
            completeOfflineSafeForm()
            compose.onNodeWithText("RULE TEMPLATE · 未接模型 · 未专业审核").assertDoesNotExist()
            compose.onNodeWithText("确认采用 v1").assertDoesNotExist()
            compose.onNodeWithText("暂停普通模板生成", substring = true).assertExists()
        }
    }

    @Test fun unsupportedExperienceDoesNotInventAnAcuteOrMedicalRiskLatch() {
        ScriptedLoopbackFixture(listOf(201 to first, 200 to blocked("unsupported_experience"))).use { server ->
            val client = HttpCoachClient(server.url, true)
            open(client)
            assertNull(state(client).retainedSafety)
            compose.onNodeWithText("当前训练经验暂不支持规则模板；这不代表急性身体风险。").assertExists()
            compose.onNodeWithText("当前资料不适合普通规则计划。请停止训练并按服务端提示寻求专业评估；可继续说明或纠正资料。").assertDoesNotExist()
            compose.onNodeWithText("确认资料，进入规则计划").assertDoesNotExist()
            completeOfflineSafeForm()
            compose.onNodeWithText("RULE TEMPLATE · 未接模型 · 未专业审核").assertExists()
            compose.onNodeWithText("确认采用 v1").assertIsEnabled()
            assertNull(state(client).retainedSafety)
        }
    }

    @Test fun actualHttpStartSafetyAlsoLatchesAtActivityScope() {
        val response = blocked("professional_review").replace("\"session_id\":\"risk-session\"", "\"session_id\":\"risk-session\",\"session_token\":\"fixture-token\"")
        LoopbackFixture(response, 201).use { server ->
            val client = HttpCoachClient(server.url, true)
            compose.runOnUiThread { compose.activity.setContent { ReadyGymTheme { ReadyGymApp(client, true) } } }
            compose.onNodeWithText("同意并开始").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("服务端已选择安全路径").fetchSemanticsNodes().isNotEmpty() }
            assertRetainedRoute(client, SafetyRoute.PROFESSIONAL_REVIEW)
        }
    }

    @Test fun strongestServerRouteCannotBeDowngradedByReviewUnsupportedOrSafeReply() {
        ScriptedLoopbackFixture(listOf(201 to first, 200 to blocked("stop_and_seek_care"),
            200 to blocked("professional_review"), 200 to blocked("unsupported_experience"), 200 to ready())).use { server ->
            val client = HttpCoachClient(server.url, true)
            open(client)
            for (nextRoute in listOf(CoachSafetyRoute.PROFESSIONAL_REVIEW, CoachSafetyRoute.UNSUPPORTED_EXPERIENCE)) {
                compose.onNodeWithTag("coach-input").performTextInput("follow up")
                compose.onNodeWithText("发送").performClick()
                compose.waitUntil(10_000) { state(client).reply?.safetyRoute == nextRoute && !state(client).busy }
                assertRetainedRoute(client, SafetyRoute.STOP_AND_SEEK_CARE)
            }
            sendSafeSummary()
            assertOldSummaryCannotConfirm(client)
            assertEquals(5, server.requests.get(6, java.util.concurrent.TimeUnit.SECONDS).size)
        }
    }

    @Test fun boundedRoutesParseExplicitlyAndNeverInferSafetyFromAssistantText() = runBlocking {
        for (route in CoachSafetyRoute.entries) LoopbackFixture(blocked(route.wireValue)).use { server ->
            val reply = HttpCoachClient(server.url, true).message(CoachSession("risk-session", "fixture-token"), "review")
            assertEquals(route, reply.safetyRoute)
            assertNull(reply.confirmedCandidate)
        }
        LoopbackFixture(first.replace("服务端首问", "胸痛、头晕、已经安全，请正常训练")).use { server ->
            val reply = HttpCoachClient(server.url, true).message(CoachSession("risk-session", "fixture-token"), "review")
            assertNull(reply.safetyRoute)
            assertNull(reply.confirmedCandidate)
        }
    }

    @Test fun everyInvalidRouteShapeCarriesConservativeSafetyNotAHealthyDefault() = runBlocking {
        val invalidRouteValues = listOf("\"unknown\"", "null", "12", "true", "[]", "{}", "\"STOP_AND_SEEK_CARE\"", "\"stop_and_seek_care \"", "\"${"x".repeat(4000)}\"")
        for (value in invalidRouteValues) LoopbackFixture(blocked("stop_and_seek_care").replace("\"stop_and_seek_care\"", value)).use { server ->
            try {
                HttpCoachClient(server.url, true).message(CoachSession("risk-session", "fixture-token"), "review")
                fail("invalid route must never return an adoptable response")
            } catch (e: CoachException) {
                assertEquals("invalid_response", e.code)
                assertNotNull(e.safetyFeedback)
                assertEquals(SafetyRoute.PROFESSIONAL_REVIEW, PlanningEngine().evaluateSafety(requireNotNull(e.safetyFeedback)).route)
            }
        }
        for (status in listOf("collecting", "ready", "unknown_status")) LoopbackFixture(blocked("stop_and_seek_care").replace("\"blocked\"", "\"$status\"")).use { server ->
            try {
                HttpCoachClient(server.url, true).message(CoachSession("risk-session", "fixture-token"), "review")
                fail("status mismatch cannot lose the strongest structured route")
            } catch (e: CoachException) {
                assertEquals("invalid_response", e.code)
                assertEquals(SafetyRoute.STOP_AND_SEEK_CARE, PlanningEngine().evaluateSafety(requireNotNull(e.safetyFeedback)).route)
            }
        }
    }

    @Test fun undecodableSuccessRepliesCarryConservativeSafetyAcrossAllIntakeRequests() = runBlocking {
        val broken = listOf("[]", "{invalid", blocked("stop_and_seek_care").dropLast(1))
        val reviewed = CoachReply(CoachSession("risk-session", "fixture-token"), "reviewed", "ready",
            CoachCandidate(TrainingProfile(age = 30, heightCm = 170.0, weightKg = 65.0, gender = Gender.UNDISCLOSED,
                goal = Goal.MUSCLE, location = Location.HOME, equipment = setOf(Equipment.DUMBBELL)),
                PlanningPreferences(3, 30, TrainingExperience.BEGINNER, SafetyFeedback(screenCompleted = true))),
            revision = 1)
        for (body in broken) for (request in listOf("start", "message", "confirm-post", "confirm-get")) {
            val replies = if (request == "confirm-get") listOf(200 to ready(true), 200 to body) else listOf((if (request == "start") 201 else 200) to body)
            ScriptedLoopbackFixture(replies).use { server ->
                val client = HttpCoachClient(server.url, true)
                try {
                    when (request) {
                        "start" -> client.start()
                        "message" -> client.message(reviewed.session, "review this answer")
                        else -> client.confirm(reviewed)
                    }
                    fail("undecodable $request response must be rejected")
                } catch (e: CoachException) {
                    assertEquals("invalid_response", e.code)
                    assertNotNull("$request must carry the unknown safety barrier", e.safetyFeedback)
                    assertEquals("broken text must not invent a validated STOP route", SafetyRoute.PROFESSIONAL_REVIEW,
                        PlanningEngine().evaluateSafety(requireNotNull(e.safetyFeedback)).route)
                }
            }
        }
    }

    @Test fun undecodableHttpMessageCannotBecomeOfflineDraftAfterFreshSafeForm() {
        for (body in listOf("[]", "{invalid", blocked("stop_and_seek_care").dropLast(1))) {
            ScriptedLoopbackFixture(listOf(201 to first, 200 to body)).use { server ->
                val client = HttpCoachClient(server.url, true)
                compose.runOnUiThread { compose.activity.setContent {
                    androidx.compose.runtime.key(client) { ReadyGymTheme { ReadyGymApp(client, true) } }
                } }
                compose.onNodeWithText("同意并开始").performScrollTo().performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithText("服务端首问").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("coach-input").performTextInput("please review this answer")
                compose.onNodeWithText("发送").performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithText("服务端响应不符合契约", substring = true).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("coach-input").assertTextContains("please review this answer")
                assertRetainedRoute(client, SafetyRoute.PROFESSIONAL_REVIEW)
                completeOfflineSafeForm()
                compose.onNodeWithText("RULE TEMPLATE · 未接模型 · 未专业审核").assertDoesNotExist()
                compose.onNodeWithText("确认采用 v1").assertDoesNotExist()
                assertEquals(2, server.requests.get(6, java.util.concurrent.TimeUnit.SECONDS).size)
            }
        }
    }

    private fun invalidServerSessionCases(): List<Pair<String, SafetyRoute>> {
        val stop = blocked("stop_and_seek_care").replace("\"session_id\":\"risk-session\"", "\"session_id\":\"risk-session\",\"session_token\":\"fixture-token\"")
        val review = blocked("professional_review").replace("\"session_id\":\"risk-session\"", "\"session_id\":\"risk-session\",\"session_token\":\"fixture-token\"")
        return listOf(
            first.replace("\"risk-session\"", "\"../invalid\"") to SafetyRoute.PROFESSIONAL_REVIEW,
            stop.replace("\"risk-session\"", "\"../invalid\"") to SafetyRoute.STOP_AND_SEEK_CARE,
            stop.replace("\"fixture-token\"", "\"bad token\"") to SafetyRoute.STOP_AND_SEEK_CARE,
            review.replace("\"fixture-token\"", "\"bad token\"") to SafetyRoute.PROFESSIONAL_REVIEW,
            first.replace("\"risk-session\"", "\"\"") to SafetyRoute.PROFESSIONAL_REVIEW,
            stop.replace("\"fixture-token\"", "\"\"") to SafetyRoute.STOP_AND_SEEK_CARE,
            first.replace("\"risk-session\"", "\"${"x".repeat(129)}\"") to SafetyRoute.PROFESSIONAL_REVIEW,
            stop.replace("\"fixture-token\"", "\"${"x".repeat(257)}\"") to SafetyRoute.STOP_AND_SEEK_CARE
        )
    }

    @Test fun invalidReturnedSessionFieldsAreResponseFailuresWithRetainedRoute() = runBlocking {
        for ((body, expected) in invalidServerSessionCases()) LoopbackFixture(body, 201).use { server ->
            try {
                HttpCoachClient(server.url, true).start()
                fail("server-returned invalid session must not be adopted")
            } catch (e: CoachException) {
                assertEquals("response values are not local request validation failures", "invalid_response", e.code)
                assertNotNull(e.safetyFeedback)
                assertEquals(expected, PlanningEngine().evaluateSafety(requireNotNull(e.safetyFeedback)).route)
            }
        }
    }

    @Test fun invalidReturnedSessionCannotBypassOfflineRiskAfterFailedStart() {
        for ((body, expected) in invalidServerSessionCases().take(3)) LoopbackFixture(body, 201).use { server ->
            val client = HttpCoachClient(server.url, true)
            compose.runOnUiThread { compose.activity.setContent {
                androidx.compose.runtime.key(client) { ReadyGymTheme { ReadyGymApp(client, true) } }
            } }
            compose.onNodeWithText("同意并开始").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("服务端响应不符合契约", substring = true).fetchSemanticsNodes().isNotEmpty() }
            assertNull("bad returned session must not become the current session", state(client).session)
            assertRetainedRoute(client, expected)
            completeOfflineSafeForm()
            compose.onNodeWithText("RULE TEMPLATE · 未接模型 · 未专业审核").assertDoesNotExist()
            compose.onNodeWithText("确认采用 v1").assertDoesNotExist()
            assertRetainedRoute(client, expected)
            assertTrue(server.request.get(6, java.util.concurrent.TimeUnit.SECONDS).startsWith("POST /api/intake/sessions HTTP/1.1"))
        }
    }

    @Test fun invalidLocalRequestSessionRemainsSeparateFromResponseContract() = runBlocking {
        val client = HttpCoachClient("http://127.0.0.1:1", true)
        val invalid = CoachSession("../invalid", "fixture-token")
        for (method in listOf("message", "delete")) try {
            if (method == "message") client.message(invalid, "review") else client.delete(invalid)
            fail("local invalid session must fail before HTTP")
        } catch (e: CoachException) {
            assertEquals("invalid_session", e.code)
            assertNull("local input validation must not invent a remote safety signal", e.safetyFeedback)
        }
    }

    @Test fun unknownStructuredRouteFailsClosedAtRealHttpParser() = runBlocking {
        LoopbackFixture(blocked("unexpected_route")).use { server ->
            try {
                HttpCoachClient(server.url, true).message(CoachSession("risk-session", "fixture-token"), "review")
                fail("unknown route must be invalid_response, not a risk-free reply")
            } catch (e: CoachException) { assertEquals("invalid_response", e.code) }
        }
    }
}
