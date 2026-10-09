package com.readygym.app

import org.json.JSONArray
import org.json.JSONObject

/** Server-validated proposals; distinct from the optional local planning engine. */
data class FoundationExercise(val slotId: String, val exerciseId: String, val sets: Int,
    val repsMin: Int?, val repsMax: Int?, val restSeconds: Int, val seconds: Int? = null)
data class FoundationDay(val dayOfWeek: Int, val training: Boolean, val exercises: List<FoundationExercise>)
data class FoundationChange(val field: String, val before: String, val after: String)
data class FoundationToolTrace(val name: String, val status: String)
data class FoundationPlan(val id: String, val revision: Int, val status: String, val basedOnRevision: Int?,
    val title: String, val daysPerWeek: Int, val minutesPerSession: Int, val trainingDays: List<Int>,
    val unavailableDays: List<Int>, val equipment: List<String>, val source: String,
    val week: List<FoundationDay>, val changes: List<FoundationChange>, val notices: List<String>)

internal object FoundationContract {
    val exerciseNames = linkedMapOf("rg-v1-bodyweight-squat" to "徒手深蹲", "rg-v1-knee-push-up" to "跪姿俯卧撑",
        "rg-v1-glute-bridge" to "徒手臀桥", "rg-v1-dead-bug" to "死虫式核心练习",
        "rg-v1-dumbbell-row" to "双侧俯身哑铃划船", "rg-v1-barbell-row" to "俯身杠铃划船")
    private val tools = setOf("check_entitlement", "get_profile", "search_exercises", "propose_plan", "propose_revision", "get_training_history")
    private val equipmentValues = setOf("bodyweight", "dumbbell", "barbell", "machine")
    private fun invalid(): Nothing = throw CoachException("invalid_response")
    private fun text(j: JSONObject, key: String, max: Int = 500): String = (j.get(key) as? String)?.takeIf { it.isNotBlank() && it.length <= max } ?: invalid()
    private fun integer(j: JSONObject, key: String, min: Int = 0, max: Int = 1_000_000): Int {
        val n = (j.get(key) as? Number)?.toDouble() ?: invalid()
        return if (n.isFinite() && n >= min && n <= max && n == n.toInt().toDouble()) n.toInt() else invalid()
    }
    private fun id(j: JSONObject, key: String) = text(j, key, 128).also { if (!it.matches(Regex("[A-Za-z0-9_-]{1,128}"))) invalid() }
    private fun <T> items(a: JSONArray, max: Int, map: (Int) -> T): List<T> {
        if (a.length() > max) invalid()
        return (0 until a.length()).map(map)
    }
    private fun days(j: JSONObject, key: String): List<Int> {
        val a = j.getJSONArray(key)
        return items(a, 7) { i ->
            val n = (a.get(i) as? Number)?.toDouble() ?: invalid()
            if (n !in 1.0..7.0 || n != n.toInt().toDouble()) invalid()
            n.toInt()
        }.also { if (it.distinct().size != it.size || it != it.sorted()) invalid() }
    }
    private fun strings(j: JSONObject, key: String, max: Int, length: Int): List<String> {
        val a = j.getJSONArray(key)
        return items(a, max) { (a.get(it) as? String)?.takeIf { s -> s.isNotBlank() && s.length <= length } ?: invalid() }
    }
    fun plan(j: JSONObject, expectedStatus: String): FoundationPlan {
        val status = text(j, "status")
        if (status != expectedStatus || text(j, "review_status") != "not_professionally_reviewed") invalid()
        val revision = integer(j, "revision", 1)
        val based = if (j.isNull("based_on_revision")) null else integer(j, "based_on_revision", 1)
        if (!j.has("based_on_revision") || (based != null && revision != based + 1) || (based == null && revision != 1)) invalid()
        val frequency = integer(j, "days_per_week", 2, 4)
        val minutes = integer(j, "minutes_per_session")
        if (minutes !in setOf(20, 30, 45, 60)) invalid()
        val training = days(j, "training_days"); val unavailable = days(j, "unavailable_days")
        if (training.size != frequency || training.any { it in unavailable }) invalid()
        val equipment = strings(j, "equipment", 4, 20)
        if (equipment.isEmpty() || equipment.toSet().size != equipment.size || !equipmentValues.containsAll(equipment)) invalid()
        val source = text(j, "source")
        if (source !in setOf("agent_rule_tool", "rule_fallback")) invalid()
        val weekJson = j.getJSONArray("week")
        if (weekJson.length() != 7) invalid()
        val week = items(weekJson, 7) { index ->
            val day = weekJson.getJSONObject(index); val weekday = integer(day, "day_of_week", 1, 7)
            if (weekday != index + 1) invalid()
            val kind = text(day, "kind")
            if (kind !in setOf("training", "rest") || ((weekday in training) != (kind == "training"))) invalid()
            val actions = day.getJSONArray("exercises")
            val exercises = items(actions, 6) { n ->
                val x = actions.getJSONObject(n); val exerciseId = id(x, "exercise_id")
                if (exerciseId !in exerciseNames || !x.has("load_kg") || !x.isNull("load_kg")) invalid()
                if (exerciseId == "rg-v1-dumbbell-row" && "dumbbell" !in equipment || exerciseId == "rg-v1-barbell-row" && "barbell" !in equipment) invalid()
                val timed = x.has("seconds") && !x.isNull("seconds")
                val seconds = if (timed) integer(x, "seconds", 10, 60) else null
                if (timed && (!x.has("reps_min") || !x.isNull("reps_min") || !x.has("reps_max") || !x.isNull("reps_max"))) invalid()
                val low = if (timed) null else integer(x, "reps_min", 4, 20)
                val high = if (timed) null else integer(x, "reps_max", low!!, 20)
                FoundationExercise(id(x, "slot_id"), exerciseId, integer(x, "sets", 1, 3), low, high, integer(x, "rest_seconds", 30, 120), seconds)
            }
            if ((kind == "training" && exercises.isEmpty()) || (kind == "rest" && exercises.isNotEmpty()) || exercises.map { it.slotId }.distinct().size != exercises.size) invalid()
            FoundationDay(weekday, kind == "training", exercises)
        }
        val slots = week.flatMap { it.exercises }.map { it.slotId }
        if (slots.distinct().size != slots.size) invalid()
        val changesJson = j.getJSONArray("changes")
        val changes = items(changesJson, 5) { n ->
            val c = changesJson.getJSONObject(n); val field = text(c, "field", 40)
            if (field !in setOf("days_per_week", "minutes_per_session", "unavailable_days", "equipment", "training_days")) invalid()
            FoundationChange(field, text(c, "before", 200), text(c, "after", 200)).also { if (it.before == it.after) invalid() }
        }
        if (based != null && changes.isEmpty() || based == null && changes.isNotEmpty() || changes.map { it.field }.distinct().size != changes.size) invalid()
        return FoundationPlan(id(j, "id"), revision, status, based, text(j, "title", 120), frequency, minutes,
            training, unavailable, equipment, source, week, changes, strings(j, "notices", 12, 800))
    }
    fun extras(j: JSONObject, reply: CoachReply): CoachReply {
        if (!j.has("foundation_enabled")) return reply
        if (j.get("foundation_enabled") !is Boolean) invalid()
        if (!j.getBoolean("foundation_enabled")) return reply
        j.getJSONArray("clarification_fields") // Foundation requires an explicit safety/clarification contract.
        val proposal = if (j.isNull("plan_proposal")) null else plan(j.getJSONObject("plan_proposal"), "draft")
        val active = if (j.isNull("active_plan")) null else plan(j.getJSONObject("active_plan"), "confirmed")
        if (!j.has("plan_proposal") || !j.has("active_plan")) invalid()
        if ((proposal != null || active != null) && (reply.status != "ready" || !reply.confirmed || reply.safetyRoute != null)) invalid()
        if (proposal != null && proposal.basedOnRevision != active?.revision) invalid()
        val a = j.getJSONArray("tool_trace")
        val trace = items(a, 12) { i ->
            val t = a.getJSONObject(i); val name = text(t, "name", 40); val status = text(t, "status", 20)
            if (name !in tools || status !in setOf("ok", "rejected")) invalid()
            FoundationToolTrace(name, status)
        }
        val source = text(j, "agent_source", 40)
        if (source !in setOf("deepseek_tool_calls", "rule_fallback", "server_policy")) invalid()
        val notice = if (j.isNull("agent_notice")) null else text(j, "agent_notice", 2000)
        return reply.copy(foundationEnabled = true, planProposal = proposal, activePlan = active, toolTrace = trace, agentSource = source, agentNotice = notice)
    }
}
