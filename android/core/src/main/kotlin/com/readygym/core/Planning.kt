package com.readygym.core

import java.util.Collections

/** Pure Kotlin rules, not an LLM or a medical prescription. */
enum class TrainingExperience { BEGINNER, RETURNING, ADVANCED }
enum class SafetySignal { CHEST_PAIN, DIZZINESS, ACUTE_JOINT_PAIN, ACUTE_INJURY, PERSISTENT_JOINT_PAIN }
enum class SelfReportedRecovery { NONE, ORDINARY_FATIGUE, MILD_MUSCLE_SORENESS }
data class SafetyFeedback(val screenCompleted: Boolean = false, val signals: Set<SafetySignal> = emptySet(),
    val medicalRestrictions: String = "", val currentDiscomfort: String = "", val recovery: SelfReportedRecovery = SelfReportedRecovery.NONE)
data class PlanningPreferences(
    val daysPerWeek: Int,
    val minutesPerSession: Int,
    val experience: TrainingExperience,
    val safety: SafetyFeedback = SafetyFeedback(),
    val constraints: TrainingConstraints = TrainingConstraints(),
)
enum class DayKind { TRAINING, REST }
data class RuleExplanation(val text: String, val sourceIds: List<String>)
enum class ReviewStatus { NOT_PROFESSIONALLY_REVIEWED }
enum class MuscleGroup { QUADRICEPS, GLUTES, CHEST, BACK, SHOULDERS, CORE }
enum class MovementPattern { SQUAT, HIP_EXTENSION, HORIZONTAL_PUSH, HORIZONTAL_PULL, ANTI_EXTENSION }
enum class Difficulty { FOUNDATION, BASIC }
/** Non-medical preferences only. Medical restrictions belong in SafetyFeedback and block normal planning. */
data class TrainingConstraints(val excludedPatterns: Set<MovementPattern> = emptySet(), val maxDifficulty: Difficulty = Difficulty.BASIC)
data class SourceReference(val id: String, val title: String, val url: String? = null, val version: String = "1",
    val scope: String = "自编模板，未经专业审核")
data class ExerciseDefinition(val stableId: String, val name: String, val primaryMuscles: Set<MuscleGroup>,
    val equipment: Set<Equipment>, val movementPattern: MovementPattern, val difficulty: Difficulty,
    val difficultyHint: String, val source: SourceReference,
    val reviewStatus: ReviewStatus = ReviewStatus.NOT_PROFESSIONALLY_REVIEWED)
data class RepetitionRange(val min: Int, val max: Int)
data class PlannedExercise(val slotId: String, val exercise: ExerciseDefinition, val sets: Int,
    val reps: RepetitionRange? = null, val seconds: Int? = null, val restSeconds: Int = 60,
    val loadKg: Double? = null, val loadHint: String = "不推算负重；从轻负重/徒手尝试，保留2–3次余力，不追求力竭")
data class TrainingSession(val id: String, val exercises: List<PlannedExercise> = emptyList(),
    val budgetSeconds: Int = 0, val warmUpSeconds: Int = 180, val coolDownSeconds: Int = 120,
    val transitionSecondsPerExercise: Int = 30, val estimatedSeconds: Int = 0,
    val durationNotice: String = "时长为估算；学习动作、排队和个人休息速度会改变实际时长，不保证准时完成，勿为赶时间跳过休息")
data class PlannedDay(val dayOfWeek: Int, val kind: DayKind, val session: TrainingSession?, val explanation: RuleExplanation)
enum class PlanStatus { DRAFT, CONFIRMED }
enum class PlanningMode { PERSONALIZED_RULE_TEMPLATE }
data class PlanConfirmation(val reviewedRevision: Int, val confirmedAtEpochMillis: Long)
data class PlanChange(val sessionId: String, val slotId: String, val fromExerciseId: String, val toExerciseId: String, val reason: String)
data class PlanDraft(val schemaVersion: Int = 1, val id: String, val revision: Int = 1, val week: List<PlannedDay>,
    val profile: TrainingProfile, val preferences: PlanningPreferences,
    val status: PlanStatus = PlanStatus.DRAFT, val mode: PlanningMode = PlanningMode.PERSONALIZED_RULE_TEMPLATE,
    val reviewStatus: ReviewStatus = ReviewStatus.NOT_PROFESSIONALLY_REVIEWED,
    val sources: List<SourceReference> = emptyList(), val notices: List<String> = emptyList(),
    val confirmation: PlanConfirmation? = null, val basedOnRevision: Int? = null, val changes: List<PlanChange> = emptyList())
enum class PlanningErrorCode { INVALID_FREQUENCY, INVALID_DURATION, UNSUPPORTED_EXPERIENCE, INVALID_PROFILE, INVALID_ID, NO_ELIGIBLE_EXERCISES,
    CONSENT_REQUIRED, STALE_REVISION, ALREADY_CONFIRMED, INVALID_CONFIRMATION_TIME, NOT_FOUND, INVALID_REPLACEMENT, INVALID_DRAFT }
data class PlanningError(val code: PlanningErrorCode, val field: String, val message: String)
enum class SafetyRoute { SCREEN_REQUIRED, TEMPLATE_ALLOWED, STOP_AND_SEEK_CARE, PROFESSIONAL_REVIEW, PAUSE_AND_REASSESS }
data class SafetyDecision(val route: SafetyRoute, val message: String)
sealed class GenerationResult {
    data class Generated(val draft: PlanDraft) : GenerationResult()
    data class Invalid(val errors: List<PlanningError>) : GenerationResult()
    data class SafetyBlocked(val decision: SafetyDecision) : GenerationResult()
}
sealed class ConfirmationResult {
    data class Confirmed(val plan: PlanDraft) : ConfirmationResult()
    data class Rejected(val errors: List<PlanningError>) : ConfirmationResult()
    data class SafetyBlocked(val decision: SafetyDecision) : ConfirmationResult()
}
sealed class ReplacementCandidatesResult {
    data class Available(val candidates: List<ExerciseDefinition>) : ReplacementCandidatesResult()
    data class NoCandidate(val reason: String) : ReplacementCandidatesResult()
    data class Rejected(val error: PlanningError) : ReplacementCandidatesResult()
    data class SafetyBlocked(val decision: SafetyDecision) : ReplacementCandidatesResult()
}
sealed class RevisionResult {
    data class Proposed(val draft: PlanDraft) : RevisionResult()
    data class Rejected(val error: PlanningError) : RevisionResult()
    data class NoCandidate(val reason: String) : RevisionResult()
    data class SafetyBlocked(val decision: SafetyDecision) : RevisionResult()
}
class PlanningEngine {
    fun proposeReplacement(draft: PlanDraft, expectedRevision: Int, sessionId: String, slotId: String,
        replacementExerciseId: String, availableEquipment: Set<Equipment>, safety: SafetyFeedback): RevisionResult {
        val decision = evaluateSafety(safety)
        if (decision.route != SafetyRoute.TEMPLATE_ALLOWED) return RevisionResult.SafetyBlocked(decision)
        if (expectedRevision != draft.revision) return RevisionResult.Rejected(PlanningError(
            PlanningErrorCode.STALE_REVISION, "expectedRevision", "计划版本已变化，请重新预览"))
        val lookup = replacementCandidates(draft, sessionId, slotId, availableEquipment, safety)
        val candidates = when (lookup) {
            is ReplacementCandidatesResult.Available -> lookup.candidates
            is ReplacementCandidatesResult.NoCandidate -> return RevisionResult.NoCandidate(lookup.reason)
            is ReplacementCandidatesResult.Rejected -> return RevisionResult.Rejected(lookup.error)
            is ReplacementCandidatesResult.SafetyBlocked -> return RevisionResult.SafetyBlocked(lookup.decision)
        }
        val replacement = candidates.firstOrNull { it.stableId == replacementExerciseId }
            ?: return RevisionResult.Rejected(PlanningError(PlanningErrorCode.INVALID_REPLACEMENT,
                "replacementExerciseId", "所选动作不是满足器械、模式、主肌群和难度的候选"))
        val original = draft.week.mapNotNull { it.session }.first { it.id == sessionId }.exercises.first { it.slotId == slotId }
        val changedWeek = draft.week.map { day ->
            val session = day.session
            if (session?.id != sessionId) day else {
                val items = session.exercises.map { if (it.slotId == slotId) it.copy(exercise = replacement) else it }
                day.copy(session = session.copy(exercises = items, estimatedSeconds = estimate(items)))
            }
        }
        return RevisionResult.Proposed(snapshot(draft.copy(revision = expectedRevision + 1, basedOnRevision = expectedRevision,
            status = PlanStatus.DRAFT, confirmation = null, week = changedWeek,
            changes = draft.changes + PlanChange(sessionId, slotId, original.exercise.stableId, replacement.stableId,
                "当前器械可用性变化；保持动作模式、主肌群、组次和休息，替换需重新确认"))))
    }
    fun replacementCandidates(draft: PlanDraft, sessionId: String, slotId: String, availableEquipment: Set<Equipment>,
        safety: SafetyFeedback): ReplacementCandidatesResult {
        val decision = evaluateSafety(safety)
        if (decision.route != SafetyRoute.TEMPLATE_ALLOWED) return ReplacementCandidatesResult.SafetyBlocked(decision)
        val original = draft.week.mapNotNull { it.session }.firstOrNull { it.id == sessionId }?.exercises?.firstOrNull { it.slotId == slotId }?.exercise
            ?: return ReplacementCandidatesResult.Rejected(PlanningError(PlanningErrorCode.NOT_FOUND, "sessionId/slotId", "未找到该训练日或动作槽位"))
        val constraints = draft.preferences.constraints
        val candidates = catalog.filter {
            it.stableId != original.stableId && it.movementPattern == original.movementPattern &&
                it.primaryMuscles == original.primaryMuscles && availableEquipment.containsAll(it.equipment) &&
                it.difficulty <= original.difficulty && it.difficulty <= constraints.maxDifficulty &&
                it.movementPattern !in constraints.excludedPatterns
        }
        return if (candidates.isEmpty()) ReplacementCandidatesResult.NoCandidate(
            "当前器械、模式、主肌群和难度约束下没有合适候选；不编造动作，不仅凭同肌群替换")
        else ReplacementCandidatesResult.Available(readOnlyList(candidates.map { snapshotExercise(it) }))
    }
    fun confirm(draft: PlanDraft, expectedRevision: Int, userAccepted: Boolean, safety: SafetyFeedback,
        confirmedAtEpochMillis: Long): ConfirmationResult {
        val decision = evaluateSafety(safety)
        if (decision.route != SafetyRoute.TEMPLATE_ALLOWED) return ConfirmationResult.SafetyBlocked(decision)
        val errors = mutableListOf<PlanningError>()
        if (!validDraftStructure(draft)) errors += PlanningError(PlanningErrorCode.INVALID_DRAFT, "draft", "草稿结构或时长预算无效，请重新生成")
        if (!userAccepted) errors += PlanningError(PlanningErrorCode.CONSENT_REQUIRED, "userAccepted", "需要明确确认此版本草稿")
        if (expectedRevision != draft.revision) errors += PlanningError(PlanningErrorCode.STALE_REVISION, "expectedRevision", "版本已变化，请重新预览")
        if (draft.status == PlanStatus.CONFIRMED) errors += PlanningError(PlanningErrorCode.ALREADY_CONFIRMED, "status", "此版本已经确认")
        if (confirmedAtEpochMillis < 0) errors += PlanningError(PlanningErrorCode.INVALID_CONFIRMATION_TIME, "confirmedAtEpochMillis", "确认时间不能为负数")
        if (errors.isNotEmpty()) return ConfirmationResult.Rejected(errors)
        return ConfirmationResult.Confirmed(snapshot(draft.copy(status = PlanStatus.CONFIRMED,
            confirmation = PlanConfirmation(expectedRevision, confirmedAtEpochMillis))))
    }
    private val authoredSource = SourceReference("ready-gym-authored-v1", "Ready Gym自编动作元数据", version = "1")
    private val catalog = listOf(
        ExerciseDefinition("rg-v1-bodyweight-squat", "徒手深蹲", setOf(MuscleGroup.QUADRICEPS), emptySet(), MovementPattern.SQUAT,
            Difficulty.FOUNDATION, "从舒适幅度慢速尝试；疼痛时停止", authoredSource),
        ExerciseDefinition("rg-v1-knee-push-up", "跪姿俯卧撑", setOf(MuscleGroup.CHEST), emptySet(), MovementPattern.HORIZONTAL_PUSH,
            Difficulty.FOUNDATION, "不要求标准俯卧撑；舒适幅度，支撑不适时停止", authoredSource),
        ExerciseDefinition("rg-v1-glute-bridge", "徒手臀桥", setOf(MuscleGroup.GLUTES), emptySet(), MovementPattern.HIP_EXTENSION,
            Difficulty.FOUNDATION, "舒适幅度，避免憋气和勉强抬高", authoredSource),
        ExerciseDefinition("rg-v1-dead-bug", "死虫式核心练习", setOf(MuscleGroup.CORE), emptySet(), MovementPattern.ANTI_EXTENSION,
            Difficulty.FOUNDATION, "小幅慢速交替，不能控制时缩小幅度", authoredSource),
        ExerciseDefinition("rg-v1-dumbbell-row", "双侧俯身哑铃划船", setOf(MuscleGroup.BACK), setOf(Equipment.DUMBBELL), MovementPattern.HORIZONTAL_PULL,
            Difficulty.BASIC, "轻负重学习俯身支撑，不借助椅子或训练凳；无法控制时不做", authoredSource),
        ExerciseDefinition("rg-v1-barbell-row", "俯身杠铃划船", setOf(MuscleGroup.BACK), setOf(Equipment.BARBELL), MovementPattern.HORIZONTAL_PULL,
            Difficulty.BASIC, "先由专业人士检查俯身姿势；空杆仍过重或无法控制时不做", authoredSource),
    )
    fun evaluateSafety(feedback: SafetyFeedback): SafetyDecision {
        // Conservative keyword guard only: not NLP/diagnosis; other nonempty descriptions also block.
        val description = (feedback.currentDiscomfort + " " + feedback.medicalRestrictions).lowercase(java.util.Locale.ROOT)
        val urgentWords = listOf("胸痛", "头晕", "急性关节", "急性损伤", "持续关节疼痛", "chest pain", "dizziness", "acute joint", "acute injury", "persistent joint pain")
        if (feedback.signals.isNotEmpty() || urgentWords.any { it in description }) return SafetyDecision(SafetyRoute.STOP_AND_SEEK_CARE,
            "停止训练，不用替换动作继续；请及时寻求专业医疗评估。胸痛或严重/持续症状请联系当地急救。不作诊断。")
        if (feedback.medicalRestrictions.isNotBlank() || feedback.currentDiscomfort.isNotBlank()) {
            return SafetyDecision(SafetyRoute.PROFESSIONAL_REVIEW,
                "暂停普通模板生成；自述医学限制或当前不适需要专业评估，不据此诊断或通过换动作绕过。")
        }
        if (!feedback.screenCompleted) return SafetyDecision(SafetyRoute.SCREEN_REQUIRED,
            "请先填写当前不适和医学训练限制；未填写不等于没有限制")
        if (feedback.recovery != SelfReportedRecovery.NONE) return SafetyDecision(SafetyRoute.PAUSE_AND_REASSESS,
            "自述疲劳/肌肉酸痛先暂停、恢复并重新评估；不自动加量，也不认定为正常。若加重、持续或出现关节疼痛等信号，请专业评估。")
        return SafetyDecision(SafetyRoute.TEMPLATE_ALLOWED, "仅允许预览未经专业审核的普通规则模板，不是安全认证")
    }

    fun generate(profile: TrainingProfile, preferences: PlanningPreferences, draftId: String): GenerationResult {
        val decision = evaluateSafety(preferences.safety)
        if (decision.route != SafetyRoute.TEMPLATE_ALLOWED) return GenerationResult.SafetyBlocked(decision)
        val errors = mutableListOf<PlanningError>()
        if (profile.schemaVersion != 1 || profile.age !in 18..120 ||
            !profile.heightCm.isFinite() || profile.heightCm !in 100.0..250.0 ||
            !profile.weightKg.isFinite() || profile.weightKg !in 30.0..300.0 || profile.equipment.isEmpty()) {
            errors += PlanningError(PlanningErrorCode.INVALID_PROFILE, "profile", "需要有效的成人建档资料和至少一种已选择器械")
        }
        if (draftId.isBlank()) errors += PlanningError(PlanningErrorCode.INVALID_ID, "draftId", "草稿ID不能为空")
        if (preferences.daysPerWeek !in listOf(2, 3, 4)) errors += PlanningError(
            PlanningErrorCode.INVALID_FREQUENCY, "daysPerWeek", "第一版只支持每周2、3或4天")
        if (preferences.minutesPerSession !in listOf(20, 30, 45, 60)) errors += PlanningError(
            PlanningErrorCode.INVALID_DURATION, "minutesPerSession", "请选择20、30、45或60分钟")
        if (preferences.experience == TrainingExperience.ADVANCED) errors += PlanningError(
            PlanningErrorCode.UNSUPPORTED_EXPERIENCE, "experience", "第一版仅有新手/恢复训练模板，不支持进阶或竞技计划")
        if (errors.isNotEmpty()) return GenerationResult.Invalid(errors)
        val trainingDays = when (preferences.daysPerWeek) {
            3 -> listOf(1, 3, 5)
            4 -> listOf(1, 2, 4, 5)
            else -> listOf(1, 4)
        }
        val eligible = catalog.filter { profile.equipment.containsAll(it.equipment) &&
            it.movementPattern !in preferences.constraints.excludedPatterns && it.difficulty <= preferences.constraints.maxDifficulty }
        val selected = eligible.filter { it.equipment.isEmpty() } + listOfNotNull(
            eligible.firstOrNull { it.movementPattern == MovementPattern.HORIZONTAL_PULL })
        if (selected.isEmpty()) return GenerationResult.Invalid(listOf(PlanningError(
            PlanningErrorCode.NO_ELIGIBLE_EXERCISES, "constraints", "当前约束下没有可用模板动作；请检查条件，不编造动作")))
        val week = (1..7).map { day ->
            val training = day in trainingDays
            PlannedDay(day, if (training) DayKind.TRAINING else DayKind.REST,
                if (training) createSession(day, selected, preferences, profile.goal) else null,
                RuleExplanation(if (!training) "休息与恢复，不自动加练"
                    else if (preferences.daysPerWeek == 4) "上下肢交替模板；相同重点留出恢复日"
                    else "每周${preferences.daysPerWeek}天基础抗阻模板",
                    listOf("ready-gym-template-v1", "cdc-adults")))
        }
        if (week.mapNotNull { it.session }.any { it.exercises.isEmpty() }) return GenerationResult.Invalid(listOf(
            PlanningError(PlanningErrorCode.NO_ELIGIBLE_EXERCISES, "constraints", "约束使某训练日没有可用动作；请调整频率或条件")))
        val notices = mutableListOf("徒手动作无需外部器械，可以作为所选器械的补充；不代表把用户的器械选择改为仅徒手")
        notices += when (profile.goal) {
            Goal.MUSCLE -> "增肌目标在时间允许时最多3组，仍需先学动作，不追求力竭或推算重量"
            Goal.FAT_LOSS -> "减脂目标保持基础抗阻，不自动增加高强度训练或给出极端减脂/饮食建议"
            Goal.HEALTH -> "健康目标优先规律、学习动作与基础抗阻"
        }
        if (Equipment.MACHINE in profile.equipment) notices += "固定器械仅为类别，尚未确认具体库存，第一版不默认腿推、史密斯或其他机器存在"
        if (selected.none { it.movementPattern == MovementPattern.HORIZONTAL_PULL }) notices += "当前器械/动作库没有合适的拉类动作，背部训练覆盖不完整；不编造替代动作"
        notices += "此个性化规则模板未经专业审核，不是医疗处方或安全认证；无模型调用"
        notices += "CDC建议成年人每周至少2天肌力训练、150分钟中等强度有氧；本短时抗阻模板不等于达到完整健康指南"
        val sources = listOf(authoredSource,
            SourceReference("ready-gym-template-v1", "Ready Gym规则模板v1", scope = "排期、余力、休息和时长估算是本地保守规则，不是CDC逐项处方"),
            SourceReference("cdc-adults", "CDC成年人活动指南", "https://www.cdc.gov/physical-activity-basics/guidelines/adults.html",
                scope = "一般成人至少2天肌力训练与150分钟中等强度有氧，不为此模板背书"),
            SourceReference("cdc-strength", "CDC肌力活动说明", "https://www.cdc.gov/physical-activity-basics/adding-adults/what-counts.html",
                scope = "一般参考8–12次、至少1组；2–3组可能增加收益；本模板不建议力竭"))
        return GenerationResult.Generated(snapshot(PlanDraft(id = draftId, week = week, profile = profile, preferences = preferences,
            sources = sources, notices = notices)))
    }

    private fun createSession(day: Int, selected: List<ExerciseDefinition>, preferences: PlanningPreferences, goal: Goal): TrainingSession {
        val startingSets = if (preferences.experience == TrainingExperience.RETURNING) 1
            else if (goal == Goal.MUSCLE && preferences.minutesPerSession >= 30) 3 else 2
        val maxReps = if (preferences.experience == TrainingExperience.RETURNING) 10 else 12
        val sessionExercises = if (preferences.daysPerWeek != 4) selected else selected.filter {
            it.movementPattern == MovementPattern.ANTI_EXTENSION ||
                if (day in listOf(1, 4)) it.movementPattern in setOf(MovementPattern.SQUAT, MovementPattern.HIP_EXTENSION)
                else it.movementPattern in setOf(MovementPattern.HORIZONTAL_PUSH, MovementPattern.HORIZONTAL_PULL)
        }
        var items = sessionExercises.mapIndexed { index, exercise ->
            PlannedExercise("day-$day-slot-$index", exercise, sets = startingSets,
                reps = if (exercise.movementPattern == MovementPattern.ANTI_EXTENSION) null else RepetitionRange(8, maxReps),
                seconds = if (exercise.movementPattern == MovementPattern.ANTI_EXTENSION) 20 else null)
        }
        val budget = preferences.minutesPerSession * 60
        while (estimate(items) > budget && items.any { it.sets > 1 }) {
            items = items.map { it.copy(sets = (it.sets - 1).coerceAtLeast(1)) }
        }
        return TrainingSession("day-$day", items, budgetSeconds = budget, estimatedSeconds = estimate(items))
    }

    private fun estimate(items: List<PlannedExercise>): Int = 300 + items.sumOf {
        it.sets * (it.seconds ?: (it.reps!!.max * 4)) + (it.sets - 1) * it.restSeconds + 30
    }

    private fun validDraftStructure(draft: PlanDraft): Boolean {
        if (draft.schemaVersion != 1 || draft.revision < 1 || draft.id.isBlank() ||
            draft.week.map { it.dayOfWeek } != (1..7).toList()) return false
        val sessions = draft.week.mapNotNull { it.session }
        if (sessions.size != draft.preferences.daysPerWeek || sessions.isEmpty()) return false
        return sessions.all { session ->
            session.exercises.isNotEmpty() && session.budgetSeconds == draft.preferences.minutesPerSession * 60 &&
                session.estimatedSeconds in 1..session.budgetSeconds &&
                session.exercises.all { (it.reps != null) xor (it.seconds != null) } &&
                session.estimatedSeconds == estimate(session.exercises)
        }
    }

    private fun <T> readOnlyList(items: Collection<T>): List<T> = Collections.unmodifiableList(ArrayList(items))
    private fun <T> readOnlySet(items: Collection<T>): Set<T> = Collections.unmodifiableSet(LinkedHashSet(items))
    private fun snapshotExercise(exercise: ExerciseDefinition): ExerciseDefinition = exercise.copy(
        primaryMuscles = readOnlySet(exercise.primaryMuscles), equipment = readOnlySet(exercise.equipment))
    private fun snapshot(plan: PlanDraft): PlanDraft = plan.copy(
        profile = plan.profile.copy(equipment = readOnlySet(plan.profile.equipment)),
        preferences = plan.preferences.copy(safety = plan.preferences.safety.copy(signals = readOnlySet(plan.preferences.safety.signals)),
            constraints = plan.preferences.constraints.copy(excludedPatterns = readOnlySet(plan.preferences.constraints.excludedPatterns))),
        week = readOnlyList(plan.week.map { day -> day.copy(
            explanation = day.explanation.copy(sourceIds = readOnlyList(day.explanation.sourceIds)),
            session = day.session?.let { session -> session.copy(exercises = readOnlyList(session.exercises.map {
                it.copy(exercise = snapshotExercise(it.exercise))
            })) }) }),
        sources = readOnlyList(plan.sources), notices = readOnlyList(plan.notices), changes = readOnlyList(plan.changes))
}
