package com.readygym.app

import java.net.ServerSocket
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Actual loopback HTTP, never a production responder. No JDK-only server dependency. */
internal class LoopbackFixture(private val response: String, private val code: Int = 200) : AutoCloseable {
    private val socket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    private val executor = Executors.newSingleThreadExecutor()
    val url = "http://127.0.0.1:${socket.localPort}"
    val request: Future<String> = executor.submit<String> {
        socket.accept().use { client ->
            val input = client.getInputStream()
            val headerBytes = java.io.ByteArrayOutputStream()
            var end = ""
            while (!end.endsWith("\r\n\r\n")) {
                val b = input.read(); if (b < 0) break
                headerBytes.write(b); end = (end + b.toChar()).takeLast(4)
            }
            val headers = headerBytes.toString("UTF-8").trim().split("\r\n")
            val length = headers.firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }?.substringAfter(':')?.trim()?.toInt() ?: 0
            val body = ByteArray(length)
            var read = 0
            while (read < length) { val n = input.read(body, read, length - read); if (n < 0) break; read += n }
            val bytes = response.toByteArray(Charsets.UTF_8)
            client.getOutputStream().apply {
                write("HTTP/1.1 $code Fixture\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                write(bytes); flush()
            }
            headers.joinToString("\n") + "\n" + String(body)
        }
    }
    override fun close() { socket.close(); executor.shutdownNow() }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CoachHttpContractTest {
    @Test fun confirmationPostsReviewedRevisionThenVerifiesExactServerSnapshot() = runBlocking {
        val profile = """{"age":21,"height_cm":175,"weight_kg":70,"goal":"muscle","location":"gym","equipment":["dumbbell"],"days_per_week":3,"minutes_per_session":30,"experience":"beginner","current_discomfort":"none","medical_restrictions":"none"}"""
        val response = """{"session_id":"session","assistant_text":"摘要已确认","status":"ready","provider_mode":"deepseek_official","validated_profile":$profile,"missing_fields":[],"revision":3,"confirmed":true}"""
        val socket = java.net.ServerSocket(0, 2, java.net.InetAddress.getByName("127.0.0.1"))
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val actual = executor.submit<List<String>> {
            (0..1).map { socket.accept().use { c ->
                val input = c.getInputStream(); val header = java.io.ByteArrayOutputStream(); var end = ""
                while (!end.endsWith("\r\n\r\n")) { val b = input.read(); if (b < 0) break; header.write(b); end = (end + b.toChar()).takeLast(4) }
                val headers = header.toString("UTF-8")
                val length = headers.lines().firstOrNull { it.startsWith("Content-Length:", true) }?.substringAfter(':')?.trim()?.toInt() ?: 0
                val body = ByteArray(length); var n = 0
                while (n < length) { val r = input.read(body, n, length - n); if (r < 0) break; n += r }
                c.getOutputStream().apply { write("HTTP/1.1 200 Fixture\r\nContent-Length: ${response.toByteArray().size}\r\nConnection: close\r\n\r\n$response".toByteArray()); flush() }
                headers + String(body, Charsets.UTF_8)
            } }
        }
        try {
            val candidate = CoachCandidate(com.readygym.core.TrainingProfile(age = 21, heightCm = 175.0, weightKg = 70.0, gender = com.readygym.core.Gender.UNDISCLOSED,
                goal = com.readygym.core.Goal.MUSCLE, location = com.readygym.core.Location.GYM, equipment = setOf(com.readygym.core.Equipment.DUMBBELL)),
                com.readygym.core.PlanningPreferences(3, 30, com.readygym.core.TrainingExperience.BEGINNER, com.readygym.core.SafetyFeedback(screenCompleted = true)))
            val confirmed = HttpCoachClient("http://127.0.0.1:${socket.localPort}", true).confirm(CoachReply(CoachSession("session", "token"), "请确认摘要", "ready", candidate, revision = 3))
            assertTrue(confirmed.confirmed)
            val calls = actual.get()
            assertTrue(calls[0].startsWith("POST /api/intake/sessions/session/confirm HTTP/1.1"))
            assertTrue(calls[0].contains("\"expected_revision\":3"))
            assertTrue(calls[0].contains("\"confirmed\":true"))
            assertTrue(calls[1].startsWith("GET /api/intake/sessions/session HTTP/1.1"))
        } finally { socket.close(); executor.shutdownNow() }
    }

    @Test fun deletionUsesBearerAndIsVerifiedByExactSessionReadback() = runBlocking {
        // One real server accepts DELETE then proves the exact target no longer exists.
        val socket = java.net.ServerSocket(0, 2, java.net.InetAddress.getByName("127.0.0.1"))
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val requests = executor.submit<List<String>> {
            (0..1).map { index -> socket.accept().use { client ->
                val input = client.getInputStream().bufferedReader()
                val header = buildList { while (true) { val line = input.readLine() ?: break; if (line.isEmpty()) break; add(line) } }.joinToString("\n")
                val payload = if (index == 0) "" else "{\"error\":{\"code\":\"session_not_found\"}}"
                val code = if (index == 0) 204 else 404
                client.getOutputStream().apply { write("HTTP/1.1 $code Fixture\r\nContent-Length: ${payload.toByteArray().size}\r\nConnection: close\r\n\r\n$payload".toByteArray()); flush() }
                header
            } }
        }
        try {
            HttpCoachClient("http://127.0.0.1:${socket.localPort}", true).delete(CoachSession("session", "token"))
            val actual = requests.get()
            assertTrue(actual[0].startsWith("DELETE /api/intake/sessions/session HTTP/1.1"))
            assertTrue(actual[1].startsWith("GET /api/intake/sessions/session HTTP/1.1"))
            assertTrue(actual.all { it.contains("Authorization: Bearer token", ignoreCase = true) })
        } finally { socket.close(); executor.shutdownNow() }
    }

    @Test fun cannotAdoptReadyWithUnsupportedOrForgedValues() = runBlocking {
        val base = """{"age":21,"height_cm":175,"weight_kg":70,"goal":"muscle","location":"gym","equipment":["dumbbell"],"days_per_week":3,"minutes_per_session":30,"experience":"beginner","current_discomfort":"none","medical_restrictions":"none"}"""
        val mutations = listOf(
            base.replace("\"age\":21", "\"age\":17"),
            base.replace("\"age\":21", "\"age\":\"21\""),
            base.replace("\"height_cm\":175", "\"height_cm\":999"),
            base.replace("\"weight_kg\":70", "\"weight_kg\":0"),
            base.replace("[\"dumbbell\"]", "[]"),
            base.replace("[\"dumbbell\"]", "[\"unavailable\"]"),
            base.replace("\"days_per_week\":3", "\"days_per_week\":7"),
            base.replace("\"minutes_per_session\":30", "\"minutes_per_session\":90"),
            base.replace("\"beginner\"", "\"advanced\""),
            base.replace("\"current_discomfort\":\"none\"", "\"current_discomfort\":\"胸痛\""),
            base.replace("\"medical_restrictions\":\"none\"", "\"medical_restrictions\":\"\""),
        )
        for (profile in mutations) LoopbackFixture("""{"assistant_text":"请确认","status":"ready","provider_mode":"deepseek_official","validated_profile":$profile,"missing_fields":[]}""").use { server ->
            try { HttpCoachClient(server.url, true).message(CoachSession("session", "token"), "测试档案"); fail("invalid ready must fail") }
            catch (e: CoachException) { assertEquals("invalid_response", e.code) }
        }
    }

    @Test fun responseSessionMismatchIsRejectedBeforeSummary() = runBlocking {
        LoopbackFixture("""{"session_id":"other-session","assistant_text":"请确认","status":"collecting","provider_mode":"deepseek_official","validated_profile":null,"missing_fields":["age"]}""").use { server ->
            try { HttpCoachClient(server.url, true).message(CoachSession("session", "token"), "测试档案"); fail("session mismatch") }
            catch (e: CoachException) { assertEquals("invalid_response", e.code) }
        }
    }

    @Test fun neverTreatsNumericAssistantTextAsAIReply() = runBlocking {
        LoopbackFixture("""{"assistant_text":123,"status":"collecting","provider_mode":"deepseek_official","validated_profile":null,"missing_fields":["age"]}""").use { server ->
            try { HttpCoachClient(server.url, true).message(CoachSession("session", "token"), "测试档案"); fail("numeric text") }
            catch (e: CoachException) { assertEquals("invalid_response", e.code) }
        }
    }

    @Test fun readyRequiresAllValidatedFieldsAndExplicitSafety() = runBlocking {
        val profile = """{"age":21,"height_cm":175,"weight_kg":70,"goal":"muscle","location":"gym","equipment":["dumbbell"],"days_per_week":3,"minutes_per_session":30,"experience":"beginner","current_discomfort":"none","medical_restrictions":"none"}"""
        LoopbackFixture("""{"assistant_text":"请确认摘要","status":"ready","provider_mode":"deepseek_official","validated_profile":$profile,"missing_fields":[]}""").use { server ->
            val candidate = HttpCoachClient(server.url, debug = true).message(CoachSession("session", "token"), "没有医学训练限制").confirmedCandidate
            assertNotNull(candidate)
            assertEquals(21, candidate!!.profile.age)
            assertTrue(candidate.preferences.safety.screenCompleted)
            assertEquals(3, candidate.preferences.daysPerWeek)
        }
    }

    @Test fun forgedReadyCannotDefaultNullSafetyToHealthy() = runBlocking {
        val profile = """{"age":21,"height_cm":175,"weight_kg":70,"goal":"muscle","location":"gym","equipment":["dumbbell"],"days_per_week":3,"minutes_per_session":30,"experience":"beginner","current_discomfort":null,"medical_restrictions":null}"""
        LoopbackFixture("""{"assistant_text":"已安全可训练","status":"ready","provider_mode":"deepseek_official","validated_profile":$profile,"missing_fields":[]}""").use { server ->
            try { HttpCoachClient(server.url, debug = true).message(CoachSession("session", "token"), "你好"); fail("must reject null safety") }
            catch (e: CoachException) { assertEquals("invalid_response", e.code) }
        }
    }

    @Test fun messagesUseSessionBearerAndPreserveNaturalLanguage() = runBlocking {
        LoopbackFixture("""{"assistant_text":"还需要你的当前安全自述。","status":"collecting","provider_mode":"deepseek_official","validated_profile":null,"missing_fields":["safety"]}""").use { server ->
            val reply = HttpCoachClient(server.url, debug = true).message(CoachSession("test-session", "test-token"), "21岁，想增肌")
            val request = server.request.get()
            assertTrue(request.startsWith("POST /api/intake/sessions/test-session/messages HTTP/1.1"))
            assertTrue(request.contains("Authorization: Bearer test-token", ignoreCase = true))
            assertTrue(request.contains("21岁，想增肌"))
            assertEquals("还需要你的当前安全自述。", reply.assistantText)
        }
    }

    @Test fun rejectsPublicCleartextBeforeSendingConsent() = runBlocking {
        try { HttpCoachClient("http://example.com", debug = true).start(); fail("cleartext must fail") }
        catch (e: CoachException) { assertEquals("insecure_endpoint", e.code) }
    }

    @Test fun rejectsOversizedResponseWithoutPretendingModelAnswered() = runBlocking {
        LoopbackFixture(" ".repeat(65_537)).use { server ->
            try { HttpCoachClient(server.url, debug = true).start(); fail("size must be bounded") }
            catch (e: CoachException) { assertEquals("response_too_large", e.code) }
        }
    }

    @Test fun tooLongInputIsRejectedBeforeNetwork() = runBlocking {
        try { HttpCoachClient("http://127.0.0.1:1", debug = true).message(CoachSession("session", "token"), "a".repeat(1001)); fail("length must fail") }
        catch (e: CoachException) { assertEquals("invalid_input", e.code) }
    }

    @Test fun unconfiguredNeverCallsAnyModel() = runBlocking {
        try { HttpCoachClient().start(); fail("must not fabricate AI") }
        catch (e: CoachException) { assertEquals("api_config_not_set", e.code) }
    }

    @Test fun providerNotConfiguredErrorIsNotAReply() = runBlocking {
        LoopbackFixture("""{"detail":{"code":"provider_not_configured"}}""", 503).use { server ->
            try { HttpCoachClient(server.url, debug = true).start(); fail("provider missing") }
            catch (e: CoachException) { assertEquals("provider_not_configured", e.code) }
        }
    }

    @Test fun redirectsNeverForwardSessionToken() = runBlocking {
        LoopbackFixture("", 302).use { server ->
            try { HttpCoachClient(server.url, debug = true).message(CoachSession("session", "token"), "你好"); fail("redirect must fail") }
            catch (e: CoachException) { assertEquals("unexpected_redirect", e.code) }
        }
    }

    @Test fun startPostsExplicitConsentAndUsesServerFirstQuestion() = runBlocking {
        LoopbackFixture("""{"session_id":"test-session","session_token":"test-token","assistant_text":"服务端AI首问：你希望实现什么目标？","status":"collecting","provider_mode":"deepseek_official","validated_profile":null,"missing_fields":["age"]}""").use { server ->
            val reply = HttpCoachClient(server.url, debug = true).start()
            val request = server.request.get()
            assertTrue(request.startsWith("POST /api/intake/sessions HTTP/1.1"))
            assertTrue(request.contains("\"consent\":true"))
            assertEquals("test-session", reply.session.id)
            assertEquals("服务端AI首问：你希望实现什么目标？", reply.assistantText)
        }
    }
}
