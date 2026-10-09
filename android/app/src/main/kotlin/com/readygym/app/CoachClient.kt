package com.readygym.app

import com.readygym.core.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/** No credentials, default public endpoint, body logs or local pretend responder. */
class CoachException(val code: String, val retryable: Boolean = false, val httpStatus: Int? = null,
    val safetyFeedback: SafetyFeedback? = null) : IOException(code)
data class CoachSession(val id: String, val token: String) {
    override fun toString() = "CoachSession(<redacted>)"
}
data class CoachReply(val session: CoachSession, val assistantText: String, val status: String,
    val confirmedCandidate: CoachCandidate? = null, val revision: Int? = null, val confirmed: Boolean = false,
    val responseSource: String? = null, val safetyRoute: CoachSafetyRoute? = null)

/** Bounded server policy, not a diagnosis or an inference from assistant prose. */
enum class CoachSafetyRoute(val wireValue: String) {
    STOP_AND_SEEK_CARE("stop_and_seek_care"),
    PROFESSIONAL_REVIEW("professional_review"),
    UNSUPPORTED_EXPERIENCE("unsupported_experience");

    fun localFeedback(): SafetyFeedback? = when (this) {
        // Core's conservative STOP guard recognizes this policy label. It does not assert a symptom.
        STOP_AND_SEEK_CARE -> SafetyFeedback(screenCompleted = true,
            currentDiscomfort = "服务端安全路线 stop_and_seek_care：停止训练并及时就医；胸痛等危险信号处置路线，不代表症状诊断")
        PROFESSIONAL_REVIEW -> SafetyFeedback(screenCompleted = true,
            medicalRestrictions = "服务端安全路线 professional_review：普通训练前需要专业评估，不作诊断")
        UNSUPPORTED_EXPERIENCE -> null
    }
}
data class CoachCandidate(val profile: TrainingProfile, val preferences: PlanningPreferences)
interface CoachClient {
    suspend fun confirm(reply: CoachReply): CoachReply = throw CoachException("confirmation_not_supported")
    suspend fun delete(session: CoachSession): Unit = throw CoachException("deletion_not_supported")
    suspend fun start(): CoachReply
    suspend fun message(session: CoachSession, text: String): CoachReply
}
private fun unverifiedCoachSafety() = SafetyFeedback(screenCompleted = true,
    medicalRestrictions = "服务端安全契约无法核实：暂停普通训练并寻求专业评估，不作诊断")

class HttpCoachClient(private val baseUrl: String = "", private val debug: Boolean = false) : CoachClient {
    /** One boundary for transport decoding, structured parsing and confirmation readback. */
    private suspend fun checkedIntakeResponse(block: suspend () -> CoachReply): CoachReply = try {
        block()
    } catch (e: CoachException) {
        if (e.code in setOf("invalid_response", "response_too_large", "unexpected_redirect")) {
            throw CoachException(e.code, e.retryable, e.httpStatus, e.safetyFeedback ?: unverifiedCoachSafety())
        }
        throw e
    }
    override suspend fun start(): CoachReply = checkedIntakeResponse {
        parseReply(exchange("POST", "/api/intake/sessions", JSONObject().put("consent", true), null), null)
    }
    override suspend fun message(session: CoachSession, text: String): CoachReply = checkedIntakeResponse {
        if (text.isBlank() || text.length > 1000) throw CoachException("invalid_input")
        parseReply(exchange("POST", path(session) + "/messages", JSONObject().put("message", text), session), session)
    }
    override suspend fun confirm(reply: CoachReply): CoachReply = checkedIntakeResponse {
        if (reply.status != "ready" || reply.safetyRoute != null || reply.confirmedCandidate == null || reply.revision == null) throw CoachException("invalid_response", safetyFeedback = reply.safetyRoute?.localFeedback())
        val posted = parseReply(exchange("POST", path(reply.session) + "/confirm",
            JSONObject().put("confirmed", true).put("expected_revision", reply.revision), reply.session), reply.session)
        posted.safetyRoute?.let { throw CoachException("invalid_response", safetyFeedback = it.localFeedback()) }
        if (!posted.confirmed || posted.revision != reply.revision || posted.confirmedCandidate != reply.confirmedCandidate) throw CoachException("invalid_response")
        val verified = parseReply(exchange("GET", path(reply.session), null, reply.session), reply.session)
        verified.safetyRoute?.let { throw CoachException("invalid_response", safetyFeedback = it.localFeedback()) }
        if (!verified.confirmed || verified.revision != reply.revision || verified.confirmedCandidate != reply.confirmedCandidate) throw CoachException("invalid_response")
        verified
    }
    override suspend fun delete(session: CoachSession) {
        try { exchange("DELETE", path(session), null, session) }
        catch (e: CoachException) {
            // A lost response or earlier deletion needs exact GET proof, never another model call.
            if (e.code != "network_error" && (e.httpStatus != 404 || e.code !in setOf("session_not_found", "http_404"))) throw e
        }
        try { exchange("GET", path(session), null, session); throw CoachException("deletion_not_verified", true) }
        catch (e: CoachException) { if (e.httpStatus != 404 || e.code !in setOf("session_not_found", "http_404")) throw e }
    }
    private fun path(session: CoachSession): String {
        if (!session.id.matches(Regex("[A-Za-z0-9_-]{1,128}")) || !session.token.matches(Regex("[A-Za-z0-9_-]{1,256}"))) throw CoachException("invalid_session")
        return "/api/intake/sessions/${session.id}"
    }
    private fun parseCandidate(p: JSONObject): CoachCandidate {
        fun number(name: String): Double = (p.get(name) as? Number)?.toDouble()?.takeIf { it.isFinite() } ?: throw CoachException("invalid_response")
        fun integer(name: String): Int {
            val n = number(name)
            if (n != n.toInt().toDouble()) throw CoachException("invalid_response")
            return n.toInt()
        }
        fun string(name: String): String = p.get(name) as? String ?: throw CoachException("invalid_response")
        fun <T> choice(name: String, choices: Map<String, T>): T = choices[string(name)] ?: throw CoachException("invalid_response")
        val age = integer("age"); val height = number("height_cm"); val weight = number("weight_kg")
        val days = integer("days_per_week"); val minutes = integer("minutes_per_session")
        if (age !in 18..120 || height !in 100.0..250.0 || weight !in 30.0..300.0 || days !in listOf(2, 3, 4) || minutes !in listOf(20, 30, 45, 60)) throw CoachException("invalid_response")
        val equipmentJson = p.getJSONArray("equipment")
        val equipmentMap = mapOf("bodyweight" to Equipment.BODYWEIGHT, "dumbbell" to Equipment.DUMBBELL, "barbell" to Equipment.BARBELL, "machine" to Equipment.MACHINE)
        val equipment = (0 until equipmentJson.length()).map { equipmentMap[equipmentJson.get(it) as? String] ?: throw CoachException("invalid_response") }.toSet()
        if (equipment.isEmpty() || equipment.size != equipmentJson.length()) throw CoachException("invalid_response")
        // Explicit backend sentinel only: null, blank and reassuring prose never mean healthy.
        if (string("current_discomfort") != "none" || string("medical_restrictions") != "none") throw CoachException("invalid_response")
        val gender = if (!p.has("gender") || p.isNull("gender")) Gender.UNDISCLOSED else choice("gender", mapOf("undisclosed" to Gender.UNDISCLOSED, "female" to Gender.FEMALE, "male" to Gender.MALE, "other" to Gender.OTHER))
        return CoachCandidate(TrainingProfile(age = age, heightCm = height, weightKg = weight, gender = gender,
            goal = choice("goal", mapOf("muscle" to Goal.MUSCLE, "fat_loss" to Goal.FAT_LOSS, "health" to Goal.HEALTH)),
            location = choice("location", mapOf("home" to Location.HOME, "gym" to Location.GYM)), equipment = equipment),
            PlanningPreferences(days, minutes, choice("experience", mapOf("beginner" to TrainingExperience.BEGINNER, "returning" to TrainingExperience.RETURNING)), SafetyFeedback(screenCompleted = true)))
    }
    private fun parseReply(json: JSONObject, session: CoachSession?): CoachReply {
        // Even a malformed 2xx response cannot turn an unverified safety policy into "none".
        var safetyOnFailure = unverifiedCoachSafety()
        try {
            fun string(name: String): String = json.get(name) as? String ?: throw CoachException("invalid_response")
            if (!json.has("safety_route")) throw CoachException("invalid_response")
            val route = if (json.isNull("safety_route")) null else {
                val value = string("safety_route")
                CoachSafetyRoute.entries.firstOrNull { it.wireValue == value } ?: throw CoachException("invalid_response")
            }
            route?.localFeedback()?.let { safetyOnFailure = it }
            val current = session ?: CoachSession(string("session_id"), string("session_token"))
            path(current)
            if (session != null && json.has("session_id") && string("session_id") != session.id) throw CoachException("invalid_response")
            val text = string("assistant_text"); val status = string("status")
            if (text.isBlank() || text.length > 8000 || status !in setOf("collecting", "ready", "blocked") || string("provider_mode") != "deepseek_official") throw CoachException("invalid_response")
            if ((status == "blocked") != (route != null)) throw CoachException("invalid_response")
            val candidate = if (status == "ready") {
                if (session == null || json.getJSONArray("missing_fields").length() != 0 ||
                    (json.has("clarification_fields") && json.getJSONArray("clarification_fields").length() != 0)) throw CoachException("invalid_response")
                parseCandidate(json.getJSONObject("validated_profile"))
            } else null
            val revision = if (!json.has("revision")) null else (json.get("revision") as? Number)?.toDouble()?.takeIf { it >= 0 && it == it.toInt().toDouble() }?.toInt() ?: throw CoachException("invalid_response")
            val confirmed = if (!json.has("confirmed")) false else json.get("confirmed") as? Boolean ?: throw CoachException("invalid_response")
            val source = if (!json.has("response_source")) null else string("response_source")
            return CoachReply(current, text, status, candidate, revision, confirmed, source, route)
        } catch (_: JSONException) { throw CoachException("invalid_response", safetyFeedback = safetyOnFailure) }
        catch (e: CoachException) {
            // Every value validated inside this parser came from the response.
            // Local request/session validation stays outside this boundary.
            throw CoachException("invalid_response", httpStatus = e.httpStatus,
                safetyFeedback = e.safetyFeedback ?: safetyOnFailure)
        }
    }
    private suspend fun exchange(method: String, path: String, body: JSONObject?, session: CoachSession?): JSONObject = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) throw CoachException("api_config_not_set")
        val root = try { URI(baseUrl) } catch (_: Exception) { throw CoachException("insecure_endpoint") }
        val loopback = root.host in setOf("localhost", "127.0.0.1")
        if (root.host.isNullOrBlank() || root.rawUserInfo != null || root.rawQuery != null || root.rawFragment != null || root.path !in listOf("", "/") ||
            (root.scheme != "https" && !(root.scheme == "http" && debug && loopback))) throw CoachException("insecure_endpoint")
        val connection = root.resolve(path).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 10_000; connection.readTimeout = 30_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            session?.let { connection.setRequestProperty("Authorization", "Bearer ${it.token}") }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            if (status in 300..399) throw CoachException("unexpected_redirect")
            if (connection.contentLengthLong > 65_536) throw CoachException("response_too_large")
            val input = if (status in 200..299) connection.inputStream else connection.errorStream
            val output = ByteArrayOutputStream()
            input?.use { stream ->
                val buffer = ByteArray(4096)
                while (true) {
                    val read = stream.read(buffer); if (read < 0) break
                    if (output.size() + read > 65_536) throw CoachException("response_too_large")
                    output.write(buffer, 0, read)
                }
            }
            if (status !in 200..299) {
                val error = try { JSONObject(output.toString("UTF-8")) } catch (_: JSONException) { null }
                val code = error?.optJSONObject("error")?.optString("code") ?: error?.optJSONObject("detail")?.optString("code")
                val known = setOf("provider_not_configured", "session_expired", "session_not_found", "session_unauthorized", "invalid_session_token", "rate_limit_exceeded", "provider_timeout", "provider_error", "session_busy", "stale_revision", "profile_not_ready", "provider_invalid_response", "provider_unavailable", "provider_auth_failed", "provider_rate_limited", "session_call_limit", "session_token_limit", "app_call_limit", "session_capacity", "provider_busy")
                throw CoachException(if (code in known) code!! else "http_$status", status in listOf(408, 429, 500, 502, 503, 504) && code != "provider_not_configured", status)
            }
            if (status == 204) JSONObject() else JSONObject(output.toString("UTF-8"))
        } catch (e: CoachException) { throw e }
        catch (_: JSONException) { throw CoachException("invalid_response") }
        catch (_: IOException) { throw CoachException("network_error", true) }
        finally { connection.disconnect() }
    }
}
