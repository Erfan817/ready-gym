package com.readygym.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readygym.core.*
import java.util.UUID

/** The offline form is opt-in; chat is the official entry. */
@Composable
fun ReadyGymApp(client: CoachClient? = null, serviceConfigured: Boolean = BuildConfig.DEBUG && BuildConfig.SERVICE_BASE_URL.isNotBlank()) {
    val actualClient = remember(client) { client ?: HttpCoachClient(BuildConfig.SERVICE_BASE_URL, BuildConfig.DEBUG) }
    val activity = androidx.compose.ui.platform.LocalContext.current as androidx.activity.ComponentActivity
    val coach = remember(activity, actualClient) {
        androidx.lifecycle.ViewModelProvider(activity, object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = CoachConversation(actualClient) as T
        })[if (client == null) "coach-default" else "coach-test-${System.identityHashCode(actualClient)}", CoachConversation::class.java]
    }
    var preferences by remember { mutableStateOf<PlanningPreferences?>(null) }
    var offline by remember { mutableStateOf(false) }
    var profile by remember { mutableStateOf<TrainingProfile?>(null) }
    androidx.activity.compose.BackHandler(offline || profile != null) {
        if (profile != null) profile = null else offline = false
    }
    val current = profile
    when {
        current != null -> TrainingPlanScreen(current, preferences, coach.retainedSafety, coach::recordSafety)
        offline -> OnboardingScreen(onStartPlanning = { preferences = null; profile = it })
        else -> CoachChatScreen(coach, serviceConfigured, onOffline = { offline = true }, onConfirmed = { preferences = it.preferences; profile = it.profile })
    }
}

private data class ReplacementTarget(val plan: PlanDraft, val sessionId: String, val item: PlannedExercise)

private enum class DiscomfortChoice { NONE, OTHER, URGENT }
private enum class MedicalChoice { NONE, RESTRICTIONS }

internal fun SafetyRoute.localRiskPriority() = when (this) {
    SafetyRoute.TEMPLATE_ALLOWED -> 0
    SafetyRoute.SCREEN_REQUIRED -> 1
    SafetyRoute.PAUSE_AND_REASSESS -> 2
    SafetyRoute.PROFESSIONAL_REVIEW -> 3
    SafetyRoute.STOP_AND_SEEK_CARE -> 4
}

/** Never treat untouched or blank fields as a completed, symptom-free self-report. */
private class SafetyForm {
    var discomfort by mutableStateOf<DiscomfortChoice?>(null)
    var medical by mutableStateOf<MedicalChoice?>(null)
    var description by mutableStateOf("")
    var restrictions by mutableStateOf("")
    val complete: Boolean get() = discomfort != null && medical != null
    fun feedback() = SafetyFeedback(
        screenCompleted = complete,
        currentDiscomfort = when (discomfort) {
            DiscomfortChoice.OTHER -> description.ifBlank { "用户自述当前有不适，尚未具体描述" }
            DiscomfortChoice.URGENT -> "用户选择胸痛、头晕、急性损伤或关节疼痛中的情况；未细分，不作诊断"
            else -> ""
        },
        medicalRestrictions = if (medical == MedicalChoice.RESTRICTIONS) restrictions.ifBlank { "用户自述存在医学训练限制" } else "",
    )
}

@Composable
fun TrainingPlanScreen(profile: TrainingProfile, initialPreferences: PlanningPreferences? = null,
    retainedSafety: SafetyFeedback? = null, onSafetyChanged: (SafetyFeedback) -> Unit = {}) {
    val engine = remember { PlanningEngine() }
    val safety = remember { SafetyForm().apply {
        // Only a validated, explicitly confirmed chat self-report may seed untouched fields.
        initialPreferences?.safety?.takeIf { retainedSafety == null && it.screenCompleted && engine.evaluateSafety(it).route == SafetyRoute.TEMPLATE_ALLOWED }?.let {
            discomfort = DiscomfortChoice.NONE; medical = MedicalChoice.NONE
        }
    } }
    var frequency by remember { mutableIntStateOf(initialPreferences?.daysPerWeek ?: 3) }
    var minutes by remember { mutableIntStateOf(initialPreferences?.minutesPerSession ?: 30) }
    var experience by remember { mutableStateOf(initialPreferences?.experience ?: TrainingExperience.BEGINNER) }
    var draft by remember { mutableStateOf<PlanDraft?>(null) }
    var confirmed by remember { mutableStateOf<PlanDraft?>(null) }
    var editing by remember { mutableStateOf(true) }
    var nextVersion by remember { mutableIntStateOf(0) }
    var draftVersion by remember { mutableIntStateOf(0) }
    var confirmedVersion by remember { mutableIntStateOf(0) }
    val displayed = draft ?: confirmed
    var dayIndex by remember { mutableIntStateOf(0) }
    var dayPicker by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<PlannedExercise?>(null) }
    var replacementTarget by remember { mutableStateOf<ReplacementTarget?>(null) }
    var availableEquipment by remember { mutableStateOf(profile.equipment) }
    var candidates by remember { mutableStateOf<ReplacementCandidatesResult?>(null) }
    var replacementMessage by remember { mutableStateOf<String?>(null) }
    var previousDraft by remember { mutableStateOf<PlanDraft?>(null) }
    var previousDraftVersion by remember { mutableIntStateOf(0) }
    var replacementProposal by remember { mutableStateOf(false) }
    var replacementFromName by remember { mutableStateOf("") }
    var safetyDialog by remember { mutableStateOf(false) }
    var reportForm by remember { mutableStateOf(SafetyForm()) }
    var latestSafety by remember { mutableStateOf(retainedSafety) }
    var safetyDecision by remember { mutableStateOf(retainedSafety?.let(engine::evaluateSafety)) }
    fun recordSafety(feedback: SafetyFeedback) {
        if (engine.evaluateSafety(feedback).route.localRiskPriority() >= 2 &&
            (latestSafety == null || engine.evaluateSafety(feedback).route.localRiskPriority() >= engine.evaluateSafety(requireNotNull(latestSafety)).route.localRiskPriority())) {
            latestSafety = feedback
            safetyDecision = engine.evaluateSafety(feedback)
        }
        onSafetyChanged(feedback)
    }
    // Preferences may escalate a retained report, never silently downgrade its block.
    // Even a fresh symptom-free report cannot erase recorded risk within this Activity session.
    // Event callbacks must reread mutable reports, not a safe snapshot captured before recomposition.
    fun activeSafety() = listOfNotNull(safety.feedback(), latestSafety, retainedSafety).maxBy { engine.evaluateSafety(it).route.localRiskPriority() }
    val currentSafety = activeSafety()
    val safetyAllowed = engine.evaluateSafety(currentSafety).route == SafetyRoute.TEMPLATE_ALLOWED
    var message by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    LaunchedEffect(editing, draft?.id) { scroll.scrollTo(0) }
    Surface(color = ReadyGymColors.Background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Column(Modifier.weight(1f).verticalScroll(scroll).padding(22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("READY GYM / 规则计划", color = ReadyGymColors.Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(if (editing) "安排你的训练周" else if (draft != null) "本周训练草稿" else "本周训练计划", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text("${profile.age}岁 · ${profile.goal.label} · ${profile.location.label} · ${Equipment.entries.filter { it in profile.equipment }.joinToString("、") { it.label }}", color = ReadyGymColors.MutedText)
                if (editing) {
                    PlanSection("01 / 训练偏好") {
                        Text("每周训练天数", fontWeight = FontWeight.Bold)
                        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(2, 3, 4).forEach { value -> PlanChoice("每周${value}天", frequency == value, Modifier.weight(1f)) { frequency = value } }
                        }
                        Text("单次时间预算", fontWeight = FontWeight.Bold)
                        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(20, 30, 45, 60).chunked(2).forEach { values -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                values.forEach { value -> PlanChoice("${value}分钟", minutes == value, Modifier.weight(1f)) { minutes = value } }
                            } }
                        }
                        Text("训练经验", fontWeight = FontWeight.Bold)
                        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TrainingExperience.entries.forEach { value -> PlanChoice(experienceLabel(value), experience == value) { experience = value } }
                        }
                        if (experience == TrainingExperience.ADVANCED) Text("进阶模板暂不支持；不会自动改成初学。", color = MaterialTheme.colorScheme.error)
                    }
                    PlanSection("02 / 当前安全自述") { SafetyFields(safety, ::recordSafety) }
                    if (message == null && engine.evaluateSafety(currentSafety).route.localRiskPriority() >= 2) Text(engine.evaluateSafety(currentSafety).message, color = MaterialTheme.colorScheme.error)
                    Text("资料和计划仅在本次页面内存；身体风险在本次Activity会话内保留，返回、旋转或重新建档不会清除。不保存、不上传。", fontSize = 12.sp, lineHeight = 20.sp, color = ReadyGymColors.MutedText)
                } else {
                    val plan = requireNotNull(displayed)
                    Text(confirmed?.let { "当前生效 v$confirmedVersion" } ?: "尚无生效计划", fontWeight = FontWeight.Bold)
                    if (!safetyDialog) safetyDecision?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
                    PlanSection(if (draft != null) "待确认 v$draftVersion" else "已确认的周计划",
                        Modifier.testTag("plan-revision-${plan.revision}-changes-${plan.changes.size}")) {
                        Text("RULE TEMPLATE · 未接模型 · 未专业审核", color = ReadyGymColors.Accent, fontSize = 12.sp)
                        Text("每周${plan.preferences.daysPerWeek}天 · 每次${plan.preferences.minutesPerSession}分钟 · ${experienceLabel(plan.preferences.experience)}")
                        Text(if (draft != null) "草稿尚未采用；确认不是安全认证。" else "本次确认仅在会话内有效，不是保存或安全认证。", color = ReadyGymColors.MutedText, fontSize = 13.sp)
                    }
                    if (replacementProposal) plan.changes.lastOrNull()?.let { change ->
                        val oldName = replacementFromName
                        val newName = plan.week.mapNotNull { it.session }.flatMap { it.exercises }.first { it.exercise.stableId == change.toExerciseId }.exercise.name
                        PlanSection("缺器械替换提案") { Text("$oldName → $newName"); Text(change.reason); Text("原计划未改变；确认后才采用。") }
                    }
                    TextButton(onClick = { dayPicker = true }) { Text("选择训练日") }
                    val day = plan.week[dayIndex]
                    PlanSection("${dayLabel(day.dayOfWeek)} / ${if (day.session == null) "休息" else "训练"}") {
                        Text(day.explanation.text)
                        day.session?.let { session ->
                            Text("估算${session.estimatedSeconds / 60}分钟 · 预算${session.budgetSeconds / 60}分钟")
                            Text(session.durationNotice, color = ReadyGymColors.MutedText, fontSize = 13.sp)
                            Text("热身${session.warmUpSeconds}秒 · 放松${session.coolDownSeconds}秒")
                            session.exercises.forEach { item ->
                                Column(Modifier.fillMaxWidth().testTag("exercise-${item.exercise.stableId}")
                                    .border(1.dp, ReadyGymColors.Outline, RoundedCornerShape(14.dp))
                                    .clickable(onClickLabel = "查看${item.exercise.name}详情") { detail = item }.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("${item.exercise.name} · ${prescription(item)}", fontWeight = FontWeight.SemiBold)
                                    ExerciseFacts(item)
                                    Text("查看动作详情与演示 →", color = ReadyGymColors.Action)
                                }
                                TextButton(onClick = { replacementTarget = ReplacementTarget(plan, session.id, item); availableEquipment = profile.equipment; candidates = null; replacementMessage = null }, enabled = safetyAllowed,
                                    modifier = Modifier.testTag("replace-${item.exercise.stableId}")) { Text("换动作 · ${item.exercise.name}") }
                            }
                        }
                    }
                    PlanSection("规则依据与边界") {
                        plan.notices.forEach { Text(it, fontSize = 13.sp, color = ReadyGymColors.MutedText) }
                        plan.sources.forEach { Text("${it.title} · ${it.id}\n${it.scope}" + (it.url?.let { url -> "\n$url" } ?: ""), fontSize = 12.sp) }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                message?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
                if (editing) Button(onClick = {
                    val submittedSafety = activeSafety()
                    recordSafety(submittedSafety)
                    when (val result = engine.generate(profile, PlanningPreferences(frequency, minutes, experience, submittedSafety), UUID.randomUUID().toString())) {
                        is GenerationResult.Generated -> { draft = result.draft; previousDraft = null; replacementProposal = false; nextVersion++; draftVersion = nextVersion; latestSafety = submittedSafety; safetyDecision = null; editing = false; message = null }
                        is GenerationResult.Invalid -> message = result.errors.joinToString("\n") { it.message }
                        is GenerationResult.SafetyBlocked -> { latestSafety = submittedSafety; safetyDecision = result.decision; message = result.decision.message }
                    }
                }, enabled = safety.complete, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
                    Text("生成规则草稿", fontWeight = FontWeight.Bold)
                }
                if (!editing) TextButton(onClick = { reportForm = SafetyForm(); safetyDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("身体不适") }
                if (!editing) TextButton(onClick = { editing = true; message = null }, modifier = Modifier.fillMaxWidth()) { Text("调整偏好 / 重新生成") }
                if (!editing && draft != null && (confirmed != null || replacementProposal)) TextButton(onClick = { draft = previousDraft; draftVersion = previousDraftVersion; previousDraft = null; replacementProposal = false; message = null }, modifier = Modifier.fillMaxWidth()) { Text("放弃草稿") }
                if (!editing) draft?.let { pending -> Button(onClick = {
                    when (val result = engine.confirm(pending, pending.revision, userAccepted = true,
                        safety = activeSafety(), confirmedAtEpochMillis = System.currentTimeMillis())) {
                        is ConfirmationResult.Confirmed -> { confirmed = result.plan; confirmedVersion = draftVersion; draft = null; previousDraft = null; replacementProposal = false; message = null }
                        is ConfirmationResult.Rejected -> message = result.errors.joinToString("\n") { it.message }
                        is ConfirmationResult.SafetyBlocked -> message = result.decision.message
                    }
                }, enabled = safetyAllowed, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
                    Text("确认采用 v$draftVersion", fontWeight = FontWeight.Bold)
                } }
            }
        }
    }
    replacementTarget?.let { target -> Dialog(onDismissRequest = { replacementTarget = null }) {
        Surface(shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(16.dp).heightIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("缺器械 / 换动作", fontWeight = FontWeight.Bold)
                Text("仅调整本次可用器械，不改建档库存。")
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Equipment.entries.forEach { equipment ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(equipment in availableEquipment, role = Role.Checkbox) { selected ->
                            availableEquipment = if (selected) availableEquipment + equipment else availableEquipment - equipment
                            candidates = null; replacementMessage = null
                        }.padding(12.dp)) {
                            Checkbox(equipment in availableEquipment, onCheckedChange = null)
                            Text("当前可用：${equipment.label}", modifier = Modifier.padding(12.dp))
                        }
                    }
                    replacementMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    when (val result = candidates) {
                        is ReplacementCandidatesResult.Available -> result.candidates.forEach { candidate -> TextButton(onClick = {
                            when (val proposal = engine.proposeReplacement(target.plan, target.plan.revision, target.sessionId, target.item.slotId,
                                candidate.stableId, availableEquipment, activeSafety())) {
                                is RevisionResult.Proposed -> {
                                    // Cancel the entire unconfirmed replacement round, not just its last step.
                                    if (!replacementProposal) { previousDraft = draft; previousDraftVersion = draftVersion }
                                    replacementFromName = target.item.exercise.name
                                    draft = proposal.draft; nextVersion++; draftVersion = nextVersion
                                    replacementProposal = true; replacementTarget = null
                                }
                                is RevisionResult.NoCandidate -> replacementMessage = proposal.reason
                                is RevisionResult.Rejected -> replacementMessage = proposal.error.message
                                is RevisionResult.SafetyBlocked -> { replacementMessage = proposal.decision.message; safetyDecision = proposal.decision }
                            }
                        }) { Text("候选：${candidate.name}") } }
                        is ReplacementCandidatesResult.NoCandidate -> Text(result.reason)
                        is ReplacementCandidatesResult.Rejected -> Text(result.error.message, color = MaterialTheme.colorScheme.error)
                        is ReplacementCandidatesResult.SafetyBlocked -> Text(result.decision.message, color = MaterialTheme.colorScheme.error)
                        null -> Unit
                    }
                }
                Button(onClick = { candidates = engine.replacementCandidates(target.plan, target.sessionId, target.item.slotId, availableEquipment, activeSafety()) },
                    enabled = safetyAllowed, modifier = Modifier.fillMaxWidth()) { Text("查找替换候选") }
                TextButton(onClick = { replacementTarget = null }, modifier = Modifier.fillMaxWidth()) { Text("取消替换") }
            }
        }
    } }
    if (safetyDialog) Dialog(onDismissRequest = { safetyDialog = false }) {
        Surface(shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(16.dp).heightIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("身体不适 / 安全反馈", fontWeight = FontWeight.Bold)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SafetyFields(reportForm, ::recordSafety)
                    safetyDecision?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
                }
                Button(onClick = {
                    recordSafety(reportForm.feedback())
                    safetyDecision = engine.evaluateSafety(listOfNotNull(reportForm.feedback(), latestSafety, retainedSafety).maxBy { engine.evaluateSafety(it).route.localRiskPriority() })
                },
                    enabled = reportForm.complete, modifier = Modifier.fillMaxWidth()) { Text("评估安全反馈") }
                TextButton(onClick = { safetyDialog = false }, modifier = Modifier.fillMaxWidth()) { Text("关闭安全反馈") }
            }
        }
    }
    if (dayPicker) AlertDialog(onDismissRequest = { dayPicker = false }, title = { Text("选择一周中的一天") },
        text = { Column { displayed?.week?.forEach { day -> TextButton(onClick = { dayIndex = day.dayOfWeek - 1; dayPicker = false }, modifier = Modifier.fillMaxWidth()) {
            Text("${dayLabel(day.dayOfWeek)} · ${if (day.session == null) "休息" else "训练"}")
        } } } }, confirmButton = { TextButton(onClick = { dayPicker = false }) { Text("关闭") } })
    detail?.let { item -> Dialog(onDismissRequest = { detail = null }) {
        Surface(shape = RoundedCornerShape(22.dp), color = ReadyGymColors.Surface) {
            Column(Modifier.fillMaxWidth().heightIn(max = 720.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("动作详情：${item.exercise.name}", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(prescription(item))
                    ExerciseFacts(item)
                    ExerciseDemo(stableId = item.exercise.stableId)
                }
                Button(onClick = { detail = null }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("关闭详情") }
            }
        }
    } }
}

@Composable
private fun ExerciseFacts(item: PlannedExercise) {
    Text("主肌群：${item.exercise.primaryMuscles.joinToString("、") { muscleLabel(it) }} · 难度：${if (item.exercise.difficulty == Difficulty.FOUNDATION) "基础" else "入门"}", fontSize = 13.sp)
    Text(item.exercise.difficultyHint, color = ReadyGymColors.MutedText, fontSize = 13.sp)
    Text(item.loadHint, fontSize = 13.sp)
    Text("动作来源：${item.exercise.source.title} · ${item.exercise.source.id}", color = ReadyGymColors.MutedText, fontSize = 12.sp)
}
private fun dayLabel(day: Int) = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[day - 1]
private fun muscleLabel(muscle: MuscleGroup) = when (muscle) {
    MuscleGroup.QUADRICEPS -> "股四头肌"; MuscleGroup.GLUTES -> "臀部"; MuscleGroup.CHEST -> "胸部"
    MuscleGroup.BACK -> "背部"; MuscleGroup.SHOULDERS -> "肩部"; MuscleGroup.CORE -> "核心"
}

@Composable
private fun SafetyFields(form: SafetyForm, onChanged: (SafetyFeedback) -> Unit = {}) {
    fun change(action: () -> Unit) { action(); onChanged(form.feedback()) }
    Text("请明确选择，两项均不默认勾选。空白不是没有风险。", color = ReadyGymColors.MutedText, fontSize = 13.sp)
    Text("当前身体情况", fontWeight = FontWeight.Bold)
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PlanChoice("没有当前不适或危险信号", form.discomfort == DiscomfortChoice.NONE) { change { form.discomfort = DiscomfortChoice.NONE } }
        PlanChoice("当前有其他不适", form.discomfort == DiscomfortChoice.OTHER) { change { form.discomfort = DiscomfortChoice.OTHER } }
        PlanChoice("胸痛、头晕、急性损伤或关节疼痛", form.discomfort == DiscomfortChoice.URGENT) { change { form.discomfort = DiscomfortChoice.URGENT } }
    }
    if (form.discomfort == DiscomfortChoice.OTHER) OutlinedTextField(form.description, { change { form.description = it } },
        label = { Text("具体不适（可补充）") }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "具体不适" })
    Text("医学训练限制", fontWeight = FontWeight.Bold)
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PlanChoice("没有医学训练限制", form.medical == MedicalChoice.NONE) { change { form.medical = MedicalChoice.NONE } }
        PlanChoice("有医学训练限制或尚不确定", form.medical == MedicalChoice.RESTRICTIONS) { change { form.medical = MedicalChoice.RESTRICTIONS } }
    }
    if (form.medical == MedicalChoice.RESTRICTIONS) OutlinedTextField(form.restrictions, { change { form.restrictions = it } },
        label = { Text("限制说明（可补充）") }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "医学限制说明" })
}

@Composable
private fun PlanSection(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(ReadyGymColors.Surface, RoundedCornerShape(22.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ReadyGymColors.Accent)
        content()
    }
}

@Composable
private fun PlanChoice(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp)
        .border(if (selected) 2.dp else 1.dp, if (selected) ReadyGymColors.Action else ReadyGymColors.Outline, RoundedCornerShape(14.dp))
        .background(if (selected) ReadyGymColors.SelectedSurface else ReadyGymColors.Surface, RoundedCornerShape(14.dp))
        .selectable(selected, role = Role.RadioButton, onClick = onClick).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (selected) "●" else "○", color = if (selected) ReadyGymColors.Action else ReadyGymColors.MutedText, modifier = Modifier.clearAndSetSemantics {})
        Text(label, fontSize = 14.sp)
    }
}

private fun experienceLabel(value: TrainingExperience) = when (value) {
    TrainingExperience.BEGINNER -> "初学"
    TrainingExperience.RETURNING -> "恢复训练"
    TrainingExperience.ADVANCED -> "进阶"
}
private fun prescription(item: PlannedExercise) = "${item.sets}组 × " + (item.reps?.let { "${it.min}–${it.max}次" } ?: "${item.seconds}秒") + " · 组间休息${item.restSeconds}秒"
