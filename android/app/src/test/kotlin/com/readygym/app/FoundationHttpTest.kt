package com.readygym.app

import com.readygym.core.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** All responses are explicit fixtures over actual loopback HTTP, never model calls. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FoundationHttpTest {
    @Test fun capturedRealJavaRepliesDecodeAcrossDraftActiveAndRevision() = runBlocking {
        val raw = javaClass.getResourceAsStream("/foundation-real-java-replies.json")!!.bufferedReader().use { it.readText() }
        val replies = JSONObject(raw).getJSONArray("replies")
        for (i in 0 until replies.length()) {
            val body = replies.getJSONObject(i)
            body.put("session_id", FoundationFixtures.session.id)
            LoopbackFixture(body.toString()).use { server ->
                val decoded = HttpCoachClient(server.url, true).message(FoundationFixtures.session, "captured fictional Java fixture")
                assertTrue(decoded.foundationEnabled)
                assertNotNull(decoded.planProposal ?: decoded.activePlan)
                val plan = decoded.planProposal ?: decoded.activePlan!!
                assertEquals(7, plan.week.size)
                assertTrue(plan.week.flatMap { it.exercises }.any { it.seconds == 20 })
            }
        }
    }
    @Test fun timedCoreExerciseUsesSecondsInsteadOfInventedRepetitions() = runBlocking {
        val timed = FoundationFixtures.plan().replace("rg-v1-bodyweight-squat", "rg-v1-dead-bug")
            .replace("\"reps_min\":8,\"reps_max\":12", "\"reps_min\":null,\"reps_max\":null,\"seconds\":20")
        LoopbackFixture(FoundationFixtures.reply(proposal = timed)).use { server ->
            val response = HttpCoachClient(server.url, true).message(FoundationFixtures.session, "fixture timed exercise")
            assertNotNull(response.planProposal)
            val item = response.planProposal!!.week.first().exercises.first()
            val getter = item.javaClass.methods.firstOrNull { it.name == "getSeconds" }
            assertNotNull("timed exercise must retain seconds, not fabricate repetitions", getter)
            assertEquals(20, getter!!.invoke(item))
        }
    }
    @Test fun basePrefixIsPreservedForBothSlashFormsWithRuntimeDemoAuth() = runBlocking {
        val body = """{"session_id":"foundation-session","session_token":"fixture-token","assistant_text":"fixture 首问","status":"collecting","provider_mode":"deepseek_official","safety_route":null}"""
        for (suffix in listOf("/ready-gym-demo", "/ready-gym-demo/")) LoopbackFixture(body).use { server ->
            HttpCoachClient(server.url + suffix, true, true, "fixture-demo-code").start()
            val request = server.request.get(6, java.util.concurrent.TimeUnit.SECONDS)
            assertTrue(request.startsWith("POST /ready-gym-demo/api/intake/sessions HTTP/1.1"))
            assertTrue(request.contains("X-Ready-Gym-Demo: fixture-demo-code", true))
        }
    }
    @Test fun traversalAndCredentialBearingEndpointsAreRejectedBeforeRequest() = runBlocking {
        for (url in listOf("https://example.org/demo/../escape", "https://example.org/demo/%2e%2e/escape", "https://example.org/demo/%252e%252e", "https://example.org/demo//next", "https://example.org/demo?secret=x", "https://name:password@example.org/demo")) {
            try { HttpCoachClient(url, false, true, "fixture-demo-code").start(); fail("unsafe endpoint") }
            catch (e: CoachException) { assertEquals("insecure_endpoint", e.code) }
        }
    }
    @Test fun explicitPlanConfirmUsesDemoAndSessionAuthThenExactGetBeforeAdoption() = runBlocking {
        val draft = FoundationFixtures.plan()
        val accepted = FoundationFixtures.reply(active = FoundationFixtures.plan(status = "confirmed"))
        ScriptedLoopbackFixture(listOf(200 to accepted, 200 to accepted)).use { server ->
            val client = HttpCoachClient(server.url, true, true, "fixture-demo-code")
            val reviewed = FoundationContract.extras(JSONObject(FoundationFixtures.reply(proposal = draft)),
                CoachReply(FoundationFixtures.session, "fixture", "ready", FoundationFixtures.candidate, 1, true))
            val result = client.confirmPlan(reviewed)
            assertEquals("confirmed", result.activePlan?.status)
            val calls = server.requests.get(6, java.util.concurrent.TimeUnit.SECONDS)
            assertTrue(calls[0].startsWith("POST /api/intake/sessions/foundation-session/foundation/confirm-plan HTTP/1.1"))
            assertTrue(calls[1].startsWith("GET /api/intake/sessions/foundation-session/foundation HTTP/1.1"))
            assertTrue(calls.all { it.contains("X-Ready-Gym-Demo: fixture-demo-code", true) })
            assertTrue(calls.all { it.contains("Authorization: Bearer fixture-token", true) })
        }
    }
    @Test fun successfulPostWithStaleGetRetainsSnapshotUntilExactRecoveryReadback() = runBlocking {
        val draft = FoundationFixtures.plan()
        val original = FoundationFixtures.reply(proposal = draft)
        val accepted = FoundationFixtures.reply(active = FoundationFixtures.plan(status = "confirmed"))
        val reviewed = FoundationContract.extras(JSONObject(original),
            CoachReply(FoundationFixtures.session, "fixture", "ready", FoundationFixtures.candidate, 1, true))
        for (changed in listOf(
            accepted.replace("\"revision\":1,\"confirmed\"", "\"revision\":2,\"confirmed\""),
            accepted.replace("\"age\":30", "\"age\":31"))) {
            ScriptedLoopbackFixture(listOf(200 to accepted, 200 to original, 200 to changed, 200 to accepted)).use { server ->
                val client = HttpCoachClient(server.url, true, true)
                try { client.confirmPlan(reviewed); fail("POST success with stale GET must remain unknown") }
                catch (e: CoachException) { assertEquals("invalid_response", e.code) }
                try { client.readFoundation(reviewed.session); fail("final validation failure must retain original profile snapshot") }
                catch (e: CoachException) { assertEquals("invalid_response", e.code) }
                try { client.confirmPlan(reviewed); fail("stale GET cannot enable another confirmation POST") }
                catch (e: CoachException) { assertEquals("plan_confirmation_unknown", e.code) }
                assertEquals(reviewed.planProposal!!.copy(status = "confirmed"), client.readFoundation(reviewed.session).activePlan)
                val requests = server.requests.get(6, java.util.concurrent.TimeUnit.SECONDS)
                assertEquals(listOf("POST", "GET", "GET", "GET"), requests.map { it.substringBefore(' ') })
            }
        }
    }
    @Test fun lostPlanPostRequiresOriginalProfileBeforeIndependentReadbackCanResolveUnknown() = runBlocking {
        val draft = FoundationFixtures.plan()
        val accepted = FoundationFixtures.reply(active = FoundationFixtures.plan(status = "confirmed"))
        val reviewed = FoundationContract.extras(JSONObject(FoundationFixtures.reply(proposal = draft)),
            CoachReply(FoundationFixtures.session, "fixture", "ready", FoundationFixtures.candidate, 1, true))
        val changedSnapshots = listOf(
            accepted.replace("\"revision\":1,\"confirmed\"", "\"revision\":2,\"confirmed\""),
            accepted.replace("\"age\":30", "\"age\":31"))
        for (changed in changedSnapshots) {
            ScriptedLoopbackFixture(listOf(null to "", 503 to "{}", 200 to changed, 200 to accepted)).use { server ->
                val client = HttpCoachClient(server.url, true, true)
                try { client.confirmPlan(reviewed); fail("lost POST must remain unknown") }
                catch (e: CoachException) { assertEquals("network_error", e.code) }
                try { client.readFoundation(reviewed.session); fail("failed GET cannot resolve unknown") }
                catch (e: CoachException) { assertEquals(503, e.httpStatus) }
                try { client.readFoundation(reviewed.session); fail("same plan with changed profile is not confirmation proof") }
                catch (e: CoachException) { assertEquals("invalid_response", e.code) }
                try { client.confirmPlan(reviewed); fail("changed readback must still block duplicate confirmation") }
                catch (e: CoachException) { assertEquals("plan_confirmation_unknown", e.code) }
                assertEquals(reviewed.planProposal!!.copy(status = "confirmed"), client.readFoundation(reviewed.session).activePlan)
                val calls = server.requests.get(6, java.util.concurrent.TimeUnit.SECONDS)
                assertEquals(listOf("POST", "GET", "GET", "GET"), calls.map { it.substringBefore(' ') })
                assertTrue(calls[0].startsWith("POST /api/intake/sessions/foundation-session/foundation/confirm-plan HTTP/1.1"))
                assertTrue(calls.drop(1).all { it.startsWith("GET /api/intake/sessions/foundation-session/foundation HTTP/1.1") })
            }
        }
    }
    @Test fun malformedPlanIsConservativelyBlockedWithoutLosingValidatedStopRoute() = runBlocking {
        val mutations = listOf(
            FoundationFixtures.plan().replace("rg-v1-bodyweight-squat", "invented-exercise"),
            FoundationFixtures.plan().replace("\"sets\":2", "\"sets\":90"),
            FoundationFixtures.plan().replace("\"review_status\":\"not_professionally_reviewed\"", "\"review_status\":\"approved\""),
            FoundationFixtures.plan().replace("\"training_days\":[1, 3, 5]", "\"training_days\":[1, 3, 3]"))
        for (plan in mutations) LoopbackFixture(FoundationFixtures.reply(proposal = plan)).use { server ->
            try { HttpCoachClient(server.url, true).message(FoundationFixtures.session, "fixture"); fail("malformed plan") }
            catch (e: CoachException) { assertNotNull(e.safetyFeedback) }
        }
        val blocked = FoundationFixtures.reply(proposal = mutations.first()).replace("\"safety_route\":null", "\"safety_route\":\"stop_and_seek_care\"").replace("\"status\":\"ready\"", "\"status\":\"blocked\"")
        LoopbackFixture(blocked).use { server ->
            try { HttpCoachClient(server.url, true).message(FoundationFixtures.session, "fixture"); fail("blocked cannot adopt") }
            catch (e: CoachException) { assertEquals(SafetyRoute.STOP_AND_SEEK_CARE, PlanningEngine().evaluateSafety(e.safetyFeedback!!).route) }
        }
    }
    @Test fun profileConfirmationKeepsFoundationSessionThenUsesExactSnapshot() = runBlocking {
        val body = FoundationFixtures.reply(proposal = FoundationFixtures.plan())
        ScriptedLoopbackFixture(listOf(200 to body, 200 to body, 200 to body)).use { server ->
            val client = HttpCoachClient(server.url, true)
            val reviewed = client.message(FoundationFixtures.session, "fixture profile")
            client.confirm(reviewed)
            val requests = server.requests.get(6, java.util.concurrent.TimeUnit.SECONDS)
            assertTrue("profile confirm must enter Foundation, not legacy planner", requests[1].startsWith("POST /api/intake/sessions/foundation-session/foundation/confirm-profile HTTP/1.1"))
            assertTrue(requests[2].startsWith("GET /api/intake/sessions/foundation-session/foundation HTTP/1.1"))
        }
    }
    @Test fun serverPlanDraftIsRetainedRatherThanDiscardedAsUnknownLegacyFields() = runBlocking {
        LoopbackFixture(FoundationFixtures.reply(proposal = FoundationFixtures.plan())).use { server ->
            val reply = HttpCoachClient(server.url, true).message(FoundationFixtures.session, "fixture completed profile")
            val getter = reply.javaClass.methods.firstOrNull { it.name == "getPlanProposal" }
            assertNotNull("server draft must reach the chat, not be silently discarded", getter)
            assertNotNull("draft must be parsed", getter!!.invoke(reply))
        }
    }
}

internal object FoundationFixtures {
    val session = CoachSession("foundation-session", "fixture-token")
    val profile = """{"age":30,"height_cm":170,"weight_kg":65,"goal":"muscle","location":"home","equipment":["dumbbell"],"days_per_week":3,"minutes_per_session":30,"experience":"beginner","current_discomfort":"none","medical_restrictions":"none"}"""
    val candidate = CoachCandidate(TrainingProfile(age = 30, heightCm = 170.0, weightKg = 65.0,
        goal = Goal.MUSCLE, gender = Gender.UNDISCLOSED, location = Location.HOME, equipment = setOf(Equipment.DUMBBELL)),
        PlanningPreferences(3, 30, TrainingExperience.BEGINNER, SafetyFeedback(screenCompleted = true)))
    fun plan(id: String = "draft-one", revision: Int = 1, status: String = "draft", days: Int = 3, basedOn: Int? = null): String {
        val training = if (days == 4) listOf(1, 2, 4, 6) else listOf(1, 3, 5)
        val week = (1..7).joinToString(",") { day ->
            val actions = if (day in training) """[{"slot_id":"d$day-s1","exercise_id":"rg-v1-bodyweight-squat","sets":2,"reps_min":8,"reps_max":12,"rest_seconds":60,"load_kg":null}]""" else "[]"
            """{"day_of_week":$day,"kind":"${if (day in training) "training" else "rest"}","exercises":$actions}"""
        }
        val changes = if (basedOn == null) "[]" else """[{"field":"days_per_week","before":"3","after":"4"}]"""
        return """{"id":"$id","revision":$revision,"status":"$status","based_on_revision":${basedOn ?: "null"},"title":"一周基础抗阻计划","days_per_week":$days,"minutes_per_session":30,"training_days":${training},"unavailable_days":[],"equipment":["dumbbell"],"source":"agent_rule_tool","review_status":"not_professionally_reviewed","week":[$week],"changes":$changes,"notices":["未经专业审核，不是医疗处方；不推算负重"]}"""
    }
    fun reply(proposal: String? = null, active: String? = null, confirmed: Boolean = true, text: String = "fixture服务端计划结果") =
        """{"session_id":"foundation-session","assistant_text":"$text","status":"ready","provider_mode":"deepseek_official","safety_route":null,"validated_profile":$profile,"missing_fields":[],"clarification_fields":[],"revision":1,"confirmed":$confirmed,"foundation_enabled":true,"plan_proposal":${proposal ?: "null"},"active_plan":${active ?: "null"},"tool_trace":[{"name":"get_profile","status":"ok"},{"name":"propose_plan","status":"ok"}],"agent_source":"deepseek_tool_calls","response_source":"deepseek_tool_calls","agent_notice":"fixture工具结果；未经专业审核"}"""
}
