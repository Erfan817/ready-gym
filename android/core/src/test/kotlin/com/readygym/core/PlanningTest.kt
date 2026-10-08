package com.readygym.core

import org.junit.Assert.*
import org.junit.Test

class PlanningTest {
    private val engine = PlanningEngine()
    private fun profile(equipment: Set<Equipment> = setOf(Equipment.BODYWEIGHT), goal: Goal = Goal.HEALTH) =
        TrainingProfile(age = 30, heightCm = 170.0, weightKg = 65.0, gender = Gender.UNDISCLOSED,
            goal = goal, location = Location.HOME, equipment = equipment)
    private fun preferences(days: Int = 2, minutes: Int = 20, experience: TrainingExperience = TrainingExperience.BEGINNER) =
        PlanningPreferences(daysPerWeek = days, minutesPerSession = minutes, experience = experience,
            safety = SafetyFeedback(screenCompleted = true))
    private fun generated(profile: TrainingProfile = profile(), preferences: PlanningPreferences = preferences()): PlanDraft {
        val result = engine.generate(profile, preferences, "test-plan")
        assertTrue(result.toString(), result is GenerationResult.Generated)
        return (result as GenerationResult.Generated).draft
    }

    @Test fun confirmationRejectsMalformedCopiedDraftsRatherThanCertifyingThem() {
        val base = generated()
        val first = base.week.first()
        val malformed = listOf(base.copy(schemaVersion = 99), base.copy(week = emptyList()),
            base.copy(week = listOf(first.copy(session = first.session!!.copy(estimatedSeconds = 99999))) + base.week.drop(1)))
        for (draft in malformed) {
            val result = engine.confirm(draft, 1, true, SafetyFeedback(screenCompleted = true), 1L)
            assertFalse(result.toString(), result is ConfirmationResult.Confirmed)
            assertTrue(result.toString(), result.toString().contains("INVALID_DRAFT"))
        }
    }

    @Test fun freeTextUrgentSelfReportsCannotFallThroughToOrdinaryPlanning() {
        for (text in listOf("胸痛", "运动时头晕", "急性关节疼痛", "chest pain", "dizziness")) {
            val feedback = SafetyFeedback(screenCompleted = true, currentDiscomfort = text)
            assertEquals(text, SafetyRoute.STOP_AND_SEEK_CARE, engine.evaluateSafety(feedback).route)
        }
    }

    @Test fun fourDayConstraintsCannotCreateEmptyTrainingSessions() {
        val onlyLower = preferences(days = 4).copy(constraints = TrainingConstraints(
            excludedPatterns = setOf(MovementPattern.HORIZONTAL_PUSH, MovementPattern.HORIZONTAL_PULL, MovementPattern.ANTI_EXTENSION)))
        val result = engine.generate(profile(), onlyLower, "test-plan")
        assertFalse(result.toString(), result is GenerationResult.Generated)
        assertTrue(result.toString(), result.toString().contains("NO_ELIGIBLE_EXERCISES"))
    }

    @Test fun outputsTakeDeepReadOnlySnapshotsOfMutableInputAndCollections() {
        val equipment = mutableSetOf(Equipment.DUMBBELL, Equipment.BARBELL)
        val excluded = mutableSetOf(MovementPattern.SQUAT)
        val pref = preferences().copy(constraints = TrainingConstraints(excludedPatterns = excluded))
        val draft = generated(profile(equipment), pref)
        equipment.clear(); excluded.clear()
        assertEquals(setOf(Equipment.DUMBBELL, Equipment.BARBELL), draft.profile.equipment)
        assertEquals(setOf(MovementPattern.SQUAT), draft.preferences.constraints.excludedPatterns)
        val confirmed = (engine.confirm(draft, 1, true, SafetyFeedback(screenCompleted = true), 1L) as ConfirmationResult.Confirmed).plan
        try {
            (confirmed.week as MutableList<PlannedDay>).clear()
            fail("Confirmed week must not be mutable")
        } catch (_: UnsupportedOperationException) { }
        try {
            (confirmed.week.first().session!!.exercises as MutableList<PlannedExercise>).clear()
            fail("Confirmed actions must not be mutable")
        } catch (_: UnsupportedOperationException) { }
    }

    @Test fun ordinaryFatigueOrSorenessPausesInsteadOfBeingCertifiedNormalOrEscalatingVolume() {
        for (recovery in listOf(SelfReportedRecovery.ORDINARY_FATIGUE, SelfReportedRecovery.MILD_MUSCLE_SORENESS)) {
            val feedback = SafetyFeedback(screenCompleted = true, recovery = recovery)
            val decision = engine.evaluateSafety(feedback)
            assertEquals(SafetyRoute.PAUSE_AND_REASSESS, decision.route)
            assertTrue(decision.message.contains("自述"))
            assertTrue(decision.message.contains("不自动加量"))
            assertFalse(engine.generate(profile(), preferences().copy(safety = feedback), "test-plan") is GenerationResult.Generated)
        }
        val urgent = SafetyFeedback(screenCompleted = true, recovery = SelfReportedRecovery.ORDINARY_FATIGUE,
            signals = setOf(SafetySignal.ACUTE_JOINT_PAIN))
        assertEquals(SafetyRoute.STOP_AND_SEEK_CARE, engine.evaluateSafety(urgent).route)
    }

    @Test fun replacementProposalCannotHideUrgentFeedbackBehindAStaleVersion() {
        val feedback = SafetyFeedback(screenCompleted = true, signals = setOf(SafetySignal.CHEST_PAIN))
        val result = engine.proposeReplacement(generated(), 0, "unknown", "unknown", "invented", emptySet(), feedback)
        assertTrue(result.toString(), result is RevisionResult.SafetyBlocked)
        assertEquals(SafetyRoute.STOP_AND_SEEK_CARE, (result as RevisionResult.SafetyBlocked).decision.route)
    }

    @Test fun invalidReplacementRequestsAreTypedErrorsAndNeverMutateDraft() {
        val safe = SafetyFeedback(screenCompleted = true)
        val draft = generated(profile(setOf(Equipment.DUMBBELL, Equipment.BARBELL)))
        val row = draft.week.first().session!!.exercises.first { it.exercise.movementPattern == MovementPattern.HORIZONTAL_PULL }
        val stale = engine.proposeReplacement(draft, 0, "day-1", row.slotId, "rg-v1-barbell-row", setOf(Equipment.BARBELL), safe)
        assertFalse(stale.toString(), stale is RevisionResult.Proposed)
        assertTrue(stale.toString(), stale.toString().contains("STALE_REVISION"))
        for (id in listOf("invented", "rg-v1-glute-bridge", "rg-v1-dumbbell-row")) {
            val result = engine.proposeReplacement(draft, 1, "day-1", row.slotId, id, setOf(Equipment.BARBELL), safe)
            assertFalse(result.toString(), result is RevisionResult.Proposed)
            assertTrue(result.toString(), result.toString().contains("INVALID_REPLACEMENT"))
        }
        assertFalse(engine.proposeReplacement(draft, 1, "day-1", row.slotId, "rg-v1-barbell-row", emptySet(), safe) is RevisionResult.Proposed)
        assertFalse(engine.proposeReplacement(draft, 1, "unknown", row.slotId, "rg-v1-barbell-row", emptySet(), safe) is RevisionResult.Proposed)
        assertEquals(1, draft.revision)
        assertTrue(draft.changes.isEmpty())
    }

    @Test fun replacingConfirmedPlanCreatesAnUnconfirmedNewVersionWithoutTouchingOriginal() {
        val safe = SafetyFeedback(screenCompleted = true)
        val draft = generated(profile(setOf(Equipment.DUMBBELL, Equipment.BARBELL)))
        val original = (engine.confirm(draft, 1, true, safe, 1234L) as ConfirmationResult.Confirmed).plan
        val row = original.week.first().session!!.exercises.first { it.exercise.movementPattern == MovementPattern.HORIZONTAL_PULL }
        val result = engine.proposeReplacement(original, 1, "day-1", row.slotId, "rg-v1-barbell-row", setOf(Equipment.BARBELL), safe)
        assertTrue(result.toString(), result is RevisionResult.Proposed)
        val revised = (result as RevisionResult.Proposed).draft
        assertEquals(2, revised.revision)
        assertEquals(1, revised.basedOnRevision)
        assertEquals(PlanStatus.DRAFT, revised.status)
        assertNull(revised.confirmation)
        val changed = revised.week.first().session!!.exercises.first { it.slotId == row.slotId }
        assertEquals("rg-v1-barbell-row", changed.exercise.stableId)
        assertEquals(row.sets, changed.sets)
        assertEquals(row.reps, changed.reps)
        assertEquals(row.restSeconds, changed.restSeconds)
        assertEquals(original.week[3], revised.week[3])
        assertEquals(original.profile, revised.profile)
        assertEquals(PlanStatus.CONFIRMED, original.status)
        assertEquals(row, original.week.first().session!!.exercises.first { it.slotId == row.slotId })
        assertEquals(1, revised.changes.size)
        assertEquals("rg-v1-dumbbell-row", revised.changes.single().fromExerciseId)
        assertEquals("rg-v1-barbell-row", revised.changes.single().toExerciseId)
        assertTrue(revised.changes.single().reason.contains("器械"))
        assertEquals(PlanStatus.CONFIRMED,
            (engine.confirm(revised, 2, true, safe, 1235L) as ConfirmationResult.Confirmed).plan.status)
    }

    @Test fun safetySignalsBlockReplacementBeforeLookup() {
        val draft = generated()
        val feedback = SafetyFeedback(screenCompleted = true, signals = setOf(SafetySignal.DIZZINESS))
        val result = engine.replacementCandidates(draft, "unknown", "unknown", emptySet(), feedback)
        assertTrue(result.toString(), result.toString().contains("SafetyBlocked"))
        assertTrue(result.toString(), result.toString().contains("STOP_AND_SEEK_CARE"))
    }

    @Test fun replacementLookupReturnsErrorsForMissingSlotsInsteadOfThrowing() {
        val draft = generated()
        for ((session, slot) in listOf("unknown" to "unknown", "day-1" to "unknown")) {
            val result = engine.replacementCandidates(draft, session, slot, emptySet(), SafetyFeedback(screenCompleted = true))
            assertFalse(result.toString(), result is ReplacementCandidatesResult.Available)
            assertTrue(result.toString(), result.toString().contains("NOT_FOUND"))
        }
    }

    @Test fun missingReplacementReturnsAnExplicitReasonNotASameMuscleGuess() {
        val draft = generated(profile(setOf(Equipment.DUMBBELL)))
        val row = draft.week.first().session!!.exercises.first { it.exercise.movementPattern == MovementPattern.HORIZONTAL_PULL }
        val result = engine.replacementCandidates(draft, "day-1", row.slotId,
            setOf(Equipment.MACHINE), SafetyFeedback(screenCompleted = true))
        assertFalse(result.toString(), result is ReplacementCandidatesResult.Available)
        assertTrue(result.toString(), result.toString().contains("NoCandidate"))
        assertTrue(result.toString(), result.toString().contains("不编造"))
    }

    @Test fun replacementCandidatesPreservePatternPrimaryMusclesDifficultyAndEquipment() {
        val draft = generated(profile(setOf(Equipment.DUMBBELL, Equipment.BARBELL)))
        val row = draft.week.first().session!!.exercises.first { it.exercise.movementPattern == MovementPattern.HORIZONTAL_PULL }
        val result = engine.replacementCandidates(draft, "day-1", row.slotId,
            setOf(Equipment.BARBELL), SafetyFeedback(screenCompleted = true))
        assertTrue(result.toString(), result is ReplacementCandidatesResult.Available)
        val candidates = (result as ReplacementCandidatesResult.Available).candidates
        assertEquals(listOf("rg-v1-barbell-row"), candidates.map { it.stableId })
        assertTrue(candidates.all { it.movementPattern == row.exercise.movementPattern })
        assertTrue(candidates.all { it.primaryMuscles == row.exercise.primaryMuscles })
        assertTrue(candidates.all { it.difficulty <= row.exercise.difficulty })
        assertTrue(candidates.all { setOf(Equipment.BARBELL).containsAll(it.equipment) })
    }

    @Test fun confirmationRechecksFreshSafetyRatherThanTrustingOldDraftScreening() {
        val draft = generated()
        for (feedback in listOf(SafetyFeedback(),
            SafetyFeedback(screenCompleted = true, signals = setOf(SafetySignal.CHEST_PAIN)),
            SafetyFeedback(screenCompleted = true, medicalRestrictions = "新的医生限制"))) {
            val result = engine.confirm(draft, 1, true, feedback, 1234L)
            assertFalse(result.toString(), result is ConfirmationResult.Confirmed)
            assertTrue(result.toString(), result.toString().contains("SafetyBlocked"))
            assertEquals(PlanStatus.DRAFT, draft.status)
        }
    }

    @Test fun confirmationRejectsMissingConsentStaleVersionAndRepeatConfirmation() {
        val draft = generated()
        val safe = SafetyFeedback(screenCompleted = true)
        val consent = engine.confirm(draft, 1, false, safe, 1234L)
        assertFalse(consent.toString(), consent is ConfirmationResult.Confirmed)
        assertTrue(consent.toString(), consent.toString().contains("CONSENT_REQUIRED"))
        val stale = engine.confirm(draft, 0, true, safe, 1234L)
        assertFalse(stale.toString(), stale is ConfirmationResult.Confirmed)
        assertTrue(stale.toString(), stale.toString().contains("STALE_REVISION"))
        val confirmed = (engine.confirm(draft, 1, true, safe, 1234L) as ConfirmationResult.Confirmed).plan
        assertFalse(engine.confirm(confirmed, 1, true, safe, 1234L) is ConfirmationResult.Confirmed)
        assertFalse(engine.confirm(draft, 1, true, safe, -1L) is ConfirmationResult.Confirmed)
    }

    @Test fun explicitConfirmationReturnsANewConfirmedSnapshot() {
        val draft = generated()
        val result = engine.confirm(draft, expectedRevision = 1, userAccepted = true,
            safety = SafetyFeedback(screenCompleted = true), confirmedAtEpochMillis = 1234L)
        assertTrue(result.toString(), result is ConfirmationResult.Confirmed)
        val confirmed = (result as ConfirmationResult.Confirmed).plan
        assertEquals(PlanStatus.CONFIRMED, confirmed.status)
        val confirmation = confirmed.confirmation!!
        assertEquals(1234L, confirmation.confirmedAtEpochMillis)
        assertEquals(1, confirmation.reviewedRevision)
        assertEquals(1, confirmed.revision)
        assertEquals(PlanStatus.DRAFT, draft.status)
        assertNull(draft.confirmation)
        assertNotSame(draft, confirmed)
    }

    @Test fun draftMetadataSeparatesGeneralGuidelinesFromUnreviewedPersonalizedTemplate() {
        val draft = generated()
        assertEquals(PlanStatus.DRAFT, draft.status)
        assertEquals(PlanningMode.PERSONALIZED_RULE_TEMPLATE, draft.mode)
        assertEquals(ReviewStatus.NOT_PROFESSIONALLY_REVIEWED, draft.reviewStatus)
        assertEquals(profile(), draft.profile)
        assertEquals(preferences(), draft.preferences)
        assertTrue(draft.sources.any { it.id == "cdc-adults" && it.url == "https://www.cdc.gov/physical-activity-basics/guidelines/adults.html" })
        assertTrue(draft.sources.any { it.id == "cdc-strength" && it.scope.contains("8–12") })
        assertTrue(draft.notices.any { it.contains("150") && it.contains("不等于") })
        assertTrue(draft.notices.any { it.contains("未经专业审核") && it.contains("安全认证") })
        val refs = draft.sources.map { it.id }.toSet()
        assertTrue(draft.week.all { refs.containsAll(it.explanation.sourceIds) })
    }

    @Test fun fourDayScheduleActuallyAlternatesUpperLowerFocus() {
        val draft = generated(profile(setOf(Equipment.DUMBBELL)), preferences(days = 4))
        val lower = setOf(MovementPattern.SQUAT, MovementPattern.HIP_EXTENSION, MovementPattern.ANTI_EXTENSION)
        val upper = setOf(MovementPattern.HORIZONTAL_PUSH, MovementPattern.HORIZONTAL_PULL, MovementPattern.ANTI_EXTENSION)
        for (day in draft.week.filter { it.session != null }) {
            val patterns = day.session!!.exercises.map { it.exercise.movementPattern }
            assertTrue(patterns.isNotEmpty())
            assertTrue(if (day.dayOfWeek in listOf(1, 4)) patterns.all { it in lower } else patterns.all { it in upper })
        }
    }

    @Test fun declaredMovementAndDifficultyConstraintsFilterCatalogWithoutInventedFallbacks() {
        val constraints = TrainingConstraints(excludedPatterns = setOf(MovementPattern.SQUAT), maxDifficulty = Difficulty.FOUNDATION)
        val draft = generated(profile(setOf(Equipment.DUMBBELL)), preferences().copy(constraints = constraints))
        val actions = draft.week.mapNotNull { it.session }.flatMap { it.exercises }.map { it.exercise }
        assertTrue(actions.none { it.movementPattern == MovementPattern.SQUAT })
        assertTrue(actions.all { it.difficulty == Difficulty.FOUNDATION })
        val none = preferences().copy(constraints = TrainingConstraints(excludedPatterns = MovementPattern.entries.toSet()))
        val result = engine.generate(profile(), none, "test-plan")
        assertFalse(result.toString(), result is GenerationResult.Generated)
        assertTrue(result.toString(), result.toString().contains("NO_ELIGIBLE_EXERCISES"))
    }

    @Test fun returningExperienceReducesStartingVolumeAndDurationChangesDose() {
        val beginner = generated(profile(goal = Goal.MUSCLE), preferences(minutes = 45)).week.first().session!!
        val returning = generated(profile(goal = Goal.MUSCLE), preferences(minutes = 45, experience = TrainingExperience.RETURNING)).week.first().session!!
        assertTrue(returning.exercises.sumOf { it.sets } < beginner.exercises.sumOf { it.sets })
        assertTrue(returning.exercises.filter { it.reps != null }.all { it.reps!!.max == 10 })
        val short = generated(profile(goal = Goal.MUSCLE), preferences(minutes = 20)).week.first().session!!
        assertTrue(short.exercises.sumOf { it.sets } < beginner.exercises.sumOf { it.sets })
    }

    @Test fun goalChangesConservativeDoseRatherThanEscalatingFatLossIntensity() {
        val health = generated(profile(goal = Goal.HEALTH), preferences(minutes = 45)).week.first().session!!
        val muscle = generated(profile(goal = Goal.MUSCLE), preferences(minutes = 45)).week.first().session!!
        val fatLoss = generated(profile(goal = Goal.FAT_LOSS), preferences(minutes = 45)).week.first().session!!
        assertTrue(muscle.exercises.sumOf { it.sets } > health.exercises.sumOf { it.sets })
        assertEquals(health.exercises, fatLoss.exercises)
        assertTrue(generated(profile(goal = Goal.FAT_LOSS)).notices.any { it.contains("不自动") && it.contains("减脂") })
    }

    @Test fun estimatedWorkRestAndTransitionsFitEveryDeclaredBudget() {
        for (days in listOf(2, 3, 4)) for (minutes in listOf(20, 30, 45, 60)) {
            val draft = generated(profile(setOf(Equipment.DUMBBELL, Equipment.BARBELL)), preferences(days, minutes))
            for (session in draft.week.mapNotNull { it.session }) {
                assertEquals(minutes * 60, session.budgetSeconds)
                val expected = session.warmUpSeconds + session.coolDownSeconds + session.exercises.sumOf { item ->
                    item.sets * (item.seconds ?: (item.reps!!.max * 4)) +
                        (item.sets - 1) * item.restSeconds + session.transitionSecondsPerExercise
                }
                assertEquals(expected, session.estimatedSeconds)
                assertTrue(session.estimatedSeconds in 1..session.budgetSeconds)
                assertTrue(session.durationNotice.contains("估算"))
                assertTrue(session.durationNotice.contains("不保证"))
            }
        }
    }

    @Test fun usableEquipmentSelectsRealPullActionsWithoutAssumingMachinesOrBenches() {
        for (equipment in listOf(setOf(Equipment.DUMBBELL), setOf(Equipment.BARBELL),
            setOf(Equipment.MACHINE), setOf(Equipment.BODYWEIGHT))) {
            val draft = generated(profile(equipment))
            val exercises = draft.week.first().session!!.exercises.map { it.exercise }
            assertTrue(exercises.all { equipment.containsAll(it.equipment) })
            if (Equipment.DUMBBELL in equipment) assertTrue(exercises.any { it.stableId == "rg-v1-dumbbell-row" })
            if (Equipment.BARBELL in equipment) assertTrue(exercises.any { it.stableId == "rg-v1-barbell-row" })
            if (Equipment.MACHINE in equipment) {
                assertTrue(exercises.none { Equipment.MACHINE in it.equipment })
                assertTrue(draft.notices.any { it.contains("固定器械") })
            }
            assertTrue(draft.notices.any { it.contains("无需外部器械") })
        }
    }

    @Test fun everyTrainingDayContainsStructuredStableActionsWithDoseAndUnreviewedProvenance() {
        val draft = generated()
        for (session in draft.week.mapNotNull { it.session }) {
            assertTrue(session.exercises.isNotEmpty())
            for (item in session.exercises) {
                assertTrue(item.exercise.stableId.startsWith("rg-v1-"))
                assertTrue(item.exercise.name.isNotBlank())
                assertTrue(item.exercise.primaryMuscles.isNotEmpty())
                assertEquals("ready-gym-authored-v1", item.exercise.source.id)
                assertEquals(ReviewStatus.NOT_PROFESSIONALLY_REVIEWED, item.exercise.reviewStatus)
                assertTrue(item.sets in 1..3)
                assertTrue((item.reps != null) xor (item.seconds != null))
                assertTrue(item.restSeconds >= 60)
                assertTrue(item.exercise.difficultyHint.isNotBlank())
                assertTrue(item.loadHint.contains("不推算"))
                assertNull(item.loadKg)
            }
        }
    }

    @Test fun selfReportedMedicalRestrictionsOrUnclassifiedDiscomfortRequireAssessment() {
        for (feedback in listOf(
            SafetyFeedback(screenCompleted = true, medicalRestrictions = "医生要求避免负重"),
            SafetyFeedback(screenCompleted = true, currentDiscomfort = "肩膀不舒服"))) {
            val result = engine.generate(profile(), preferences().copy(safety = feedback), "test-plan")
            assertTrue(result.toString(), result is GenerationResult.SafetyBlocked)
            assertEquals(SafetyRoute.PROFESSIONAL_REVIEW, (result as GenerationResult.SafetyBlocked).decision.route)
        }
    }

    @Test fun urgentSelfReportedSignalsStopPlanningBeforeAnyOtherValidation() {
        for (signal in listOf(SafetySignal.CHEST_PAIN, SafetySignal.DIZZINESS, SafetySignal.ACUTE_JOINT_PAIN,
            SafetySignal.ACUTE_INJURY, SafetySignal.PERSISTENT_JOINT_PAIN)) {
            val feedback = SafetyFeedback(screenCompleted = false, signals = setOf(signal))
            val result = engine.generate(profile(), preferences(days = 99).copy(safety = feedback), "test-plan")
            assertTrue(result.toString(), result is GenerationResult.SafetyBlocked)
            val decision = (result as GenerationResult.SafetyBlocked).decision
            assertEquals(SafetyRoute.STOP_AND_SEEK_CARE, decision.route)
            assertTrue(decision.message.contains("停止"))
            assertTrue(decision.message.contains("专业"))
        }
    }

    @Test fun unfinishedSafetyScreenDoesNotProduceAnOrdinaryPlan() {
        val result = engine.generate(profile(), preferences().copy(safety = SafetyFeedback()), "test-plan")
        assertFalse(result.toString(), result is GenerationResult.Generated)
        assertTrue(result.toString(), result.toString().contains("SCREEN_REQUIRED"))
    }

    @Test fun generatorRevalidatesImportedProfilesAndIdentifiers() {
        val invalid = listOf(profile().copy(age = 17), profile().copy(age = 121),
            profile().copy(heightCm = Double.NaN), profile().copy(weightKg = Double.POSITIVE_INFINITY),
            profile().copy(heightCm = 99.0), profile().copy(weightKg = 301.0),
            profile().copy(schemaVersion = 9), profile(emptySet()))
        for (p in invalid) {
            val result = engine.generate(p, preferences(), "test-plan")
            assertFalse(result.toString(), result is GenerationResult.Generated)
            assertTrue(result.toString(), result.toString().contains("INVALID_PROFILE"))
        }
        assertFalse(engine.generate(profile(), preferences(), " ") is GenerationResult.Generated)
    }

    @Test fun advancedExperienceIsExplicitlyUnsupported() {
        val result = engine.generate(profile(), preferences(experience = TrainingExperience.ADVANCED), "test-plan")
        assertFalse(result.toString(), result is GenerationResult.Generated)
        assertTrue(result.toString(), result.toString().contains("UNSUPPORTED_EXPERIENCE"))
    }

    @Test fun unsupportedFrequencyOrDurationReturnsErrorsInsteadOfAnInventedPlan() {
        for (days in listOf(-1, 0, 1, 5, 7)) {
            val result = engine.generate(profile(), preferences(days = days), "test-plan")
            assertFalse(result.toString(), result is GenerationResult.Generated)
            assertTrue(result.toString(), result.toString().contains("INVALID_FREQUENCY"))
        }
        for (minutes in listOf(0, 10, 25, 90)) {
            val result = engine.generate(profile(), preferences(minutes = minutes), "test-plan")
            assertFalse(result.toString(), result is GenerationResult.Generated)
            assertTrue(result.toString(), result.toString().contains("INVALID_DURATION"))
        }
    }

    @Test fun threeAndFourDaysUseRecoveryAwareSchedules() {
        assertEquals(listOf(1, 3, 5), generated(preferences = preferences(days = 3)).week.filter { it.session != null }.map { it.dayOfWeek })
        val four = generated(preferences = preferences(days = 4))
        assertEquals(listOf(1, 2, 4, 5), four.week.filter { it.session != null }.map { it.dayOfWeek })
        assertTrue(four.week.filter { it.session != null }.all { it.explanation.text.contains("上下肢") })
    }

    @Test fun twoDayDraftContainsSevenTraceableScheduledDays() {
        val draft = generated()
        assertEquals(1, draft.schemaVersion)
        assertEquals(1, draft.revision)
        assertEquals("test-plan", draft.id)
        assertEquals(listOf(1, 4), draft.week.filter { it.session != null }.map { it.dayOfWeek })
        assertEquals((1..7).toList(), draft.week.map { it.dayOfWeek })
        assertTrue(draft.week.all { it.explanation.sourceIds.isNotEmpty() })
        assertTrue(draft.week.filter { it.session == null }.all { it.kind == DayKind.REST })
    }
}
