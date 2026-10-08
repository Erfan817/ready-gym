package com.readygym.core

import org.junit.Assert.*
import org.junit.Test

class OnboardingTest {
    @Test fun supportsInclusiveBoundsAndWhitespaceWithoutCapabilityInference() {
        for ((a, h, w) in listOf(Triple("18", "100", "30"), Triple("120", "250", "300"), Triple(" 30 ", " 170.5 ", " 65.25 "))) {
            val f = Onboarding(); f.age = a; f.height = h; f.weight = w
            assertTrue(f.next()); f.goal = Goal.HEALTH; f.next()
            f.location = Location.HOME; f.equipment = setOf(Equipment.BODYWEIGHT); f.next()
            assertEquals(a.trim().toInt(), f.confirmedProfile()!!.age)
        }
    }
    @Test fun confirmationDoesNotLoopToGoal() {
        val f = adult(); f.next(); f.goal = Goal.HEALTH; f.next()
        f.location = Location.HOME; f.equipment = setOf(Equipment.BODYWEIGHT); f.next()
        assertFalse(f.next()); assertEquals(Step.CONFIRM, f.step)
    }
    @Test fun confirmationProducesIndependentTypedProfileOnlyWhenComplete() {
        val f = adult(); assertNull(f.confirmedProfile()); f.next()
        f.goal = Goal.FAT_LOSS; f.next(); f.location = Location.GYM
        f.equipment = setOf(Equipment.BARBELL, Equipment.MACHINE); f.next()
        val p = f.confirmedProfile()!!
        assertEquals(30, p.age); assertEquals(170.0, p.heightCm, 0.0)
        assertEquals(65.0, p.weightKg, 0.0); assertEquals(Gender.UNDISCLOSED, p.gender)
        assertEquals(Goal.FAT_LOSS, p.goal); assertEquals(Location.GYM, p.location)
        assertEquals(setOf(Equipment.BARBELL, Equipment.MACHINE), p.equipment)
        f.back(); assertNull(f.confirmedProfile()); f.equipment = setOf(Equipment.BODYWEIGHT)
        assertEquals(2, p.equipment.size)
    }
    @Test fun backPreservesInputsAndAllowsEditing() {
        val f = adult(); f.next(); f.goal = Goal.MUSCLE; f.next()
        f.back(); assertEquals(Step.GOAL, f.step); assertEquals(Goal.MUSCLE, f.goal)
        f.back(); assertEquals(Step.PROFILE, f.step); assertEquals("170", f.height)
        f.height = "180"; assertTrue(f.next()); assertEquals("180", f.height)
        f.back(); f.back(); assertEquals(Step.PROFILE, f.step)
    }
    @Test fun conditionsRequireLocationAndEquipment() {
        val f = adult(); f.next(); f.goal = Goal.HEALTH; f.next()
        assertFalse(f.next())
        f.location = Location.HOME; assertFalse(f.next())
        f.equipment = setOf(Equipment.BODYWEIGHT, Equipment.DUMBBELL)
        assertTrue(f.next()); assertEquals(Step.CONFIRM, f.step)
    }
    private fun adult(): Onboarding = Onboarding().apply { age = "30"; height = "170"; weight = "65" }
    @Test fun goalMustBeExplicitBeforeConditions() {
        for (g in Goal.entries) {
            val f = adult(); f.next()
            assertFalse(f.next()); assertEquals(Step.GOAL, f.step)
            f.goal = g; assertTrue(f.next()); assertEquals(Step.CONDITIONS, f.step)
        }
    }
    @Test fun rejectsInvalidBasics() {
        for (bad in listOf("", "17", "121", "18.5", "NaN", "Infinity")) {
            val f = Onboarding(); f.age = bad; f.height = "170"; f.weight = "65"
            assertFalse("age=$bad", f.next()); assertEquals(Step.PROFILE, f.step)
        }
        for (bad in listOf("", "NaN", "Infinity", "-1", "0", "99", "251", "abc")) {
            val f = Onboarding(); f.age = "30"; f.height = bad; f.weight = "65"
            assertFalse("height=$bad", f.next())
        }
        for (bad in listOf("", "NaN", "Infinity", "-1", "0", "29", "301", "abc")) {
            val f = Onboarding(); f.age = "30"; f.height = "170"; f.weight = bad
            assertFalse("weight=$bad", f.next())
        }
    }
    @Test fun validAdultCanReachGoal() {
        val flow = Onboarding()
        flow.age = "18"
        flow.height = "170.5"
        flow.weight = "65"
        assertTrue(flow.next())
        assertEquals(Step.GOAL, flow.step)
    }
}
