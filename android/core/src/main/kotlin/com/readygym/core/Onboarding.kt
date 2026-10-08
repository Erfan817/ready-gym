package com.readygym.core

enum class Step { PROFILE, GOAL, CONDITIONS, CONFIRM }
enum class Gender(val label: String) { UNDISCLOSED("不透露"), FEMALE("女"), MALE("男"), OTHER("其他") }
enum class Goal(val label: String) { MUSCLE("增肌"), FAT_LOSS("减脂"), HEALTH("保持健康") }
enum class Location(val label: String) { HOME("家"), GYM("健身房") }
enum class Equipment(val label: String) { BODYWEIGHT("徒手"), DUMBBELL("哑铃"), BARBELL("杠铃"), MACHINE("固定器械") }
data class TrainingProfile(val schemaVersion: Int = 1, val age: Int, val heightCm: Double, val weightKg: Double, val gender: Gender, val goal: Goal, val location: Location, val equipment: Set<Equipment>)
class Onboarding {
    fun confirmedProfile(): TrainingProfile? {
        if (step != Step.CONFIRM || !validBasics() || goal == null || location == null || equipment.isEmpty()) return null
        return TrainingProfile(age = age.trim().toInt(), heightCm = height.trim().toDouble(), weightKg = weight.trim().toDouble(), gender = gender, goal = goal!!, location = location!!, equipment = equipment.toSet())
    }
    var gender = Gender.UNDISCLOSED
    var goal: Goal? = null
    var location: Location? = null
    var equipment = emptySet<Equipment>()
    var age = ""
    var height = ""
    var weight = ""
    var step = Step.PROFILE
        private set
    fun back() { step = Step.entries[(step.ordinal - 1).coerceAtLeast(0)] }
    fun next(): Boolean {
        if (step == Step.CONFIRM) return false
        if (step == Step.CONDITIONS) {
            if (location == null || equipment.isEmpty()) return false
            step = Step.CONFIRM
            return true
        }
        if (step == Step.GOAL) {
            if (goal == null) return false
            step = Step.CONDITIONS
            return true
        }
        if (!validBasics()) return false
        step = Step.GOAL
        return true
    }
    private fun validBasics(): Boolean {
        val a = age.trim().toIntOrNull()
        val h = height.trim().toDoubleOrNull()
        val w = weight.trim().toDoubleOrNull()
        return a != null && a in 18..120 && h != null && h.isFinite() && h in 100.0..250.0 && w != null && w.isFinite() && w in 30.0..300.0
    }
}
