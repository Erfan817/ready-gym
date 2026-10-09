package com.readygym.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/** Read-only tool results. Only an explicit button can call the write endpoint. */
@Composable
internal fun FoundationCards(coach: CoachConversation) {
    val reply = coach.reply ?: return
    var detail by remember { mutableStateOf<FoundationExercise?>(null) }
    reply.agentNotice?.let { Text(it, color = ReadyGymColors.MutedText, fontSize = 13.sp) }
    if (reply.toolTrace.isNotEmpty()) {
        Card(colors = CardDefaults.cardColors(containerColor = ReadyGymColors.Surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("工具执行记录 · 服务端结果", fontWeight = FontWeight.Bold)
                reply.toolTrace.forEach { Text("${it.name} · ${if (it.status == "ok") "完成" else "拒绝"}", fontSize = 13.sp) }
            }
        }
    }
    reply.activePlan?.let { plan -> FoundationPlanCard(plan, "当前生效 v${plan.revision}", { detail = it }) }
    reply.planProposal?.let { plan ->
        FoundationPlanCard(plan, "待确认 v${plan.revision}", { detail = it })
        if (plan.changes.isNotEmpty()) Card(colors = CardDefaults.cardColors(containerColor = ReadyGymColors.AccentSurface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("修订差异 · 原生效版未改变", fontWeight = FontWeight.Bold)
                plan.changes.forEach { Text("${fieldLabel(it.field)}：${it.before} → ${it.after}") }
            }
        }
        Text("草稿不会自动落库。明确确认后读回同一会话、资料与计划版本，并核对客户端支持字段；确认不是安全认证。", fontSize = 13.sp, color = ReadyGymColors.MutedText)
        Button(onClick = { coach.confirmPlan() }, enabled = !coach.busy && !coach.localSafetyBlocked && coach.input.isBlank() && !coach.planConfirmationUnknown,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("foundation-confirm-plan")) { Text("确认采用 v${plan.revision}") }
    }
    if (coach.planConfirmationUnknown) Text("上次确认结果未知：请先读回状态，不能重复写入或提出新修改。", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
    if (reply.foundationEnabled && reply.confirmed) TextButton(onClick = { coach.readFoundation() },
        enabled = !coach.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("读回确认状态（不重复写入）") }
    detail?.let { item -> Dialog(onDismissRequest = { detail = null }) {
        Surface(shape = RoundedCornerShape(22.dp), color = ReadyGymColors.Surface) {
            Column(Modifier.fillMaxWidth().heightIn(max = 720.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("动作详情：${FoundationContract.exerciseNames[item.exerciseId]}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${item.sets}组 × ${exerciseDose(item)} · 组间休息${item.restSeconds}秒")
                    Text("未经专业审核；不推算负重。当前身体不适时停止训练。", fontSize = 13.sp)
                    ExerciseDemo(stableId = item.exerciseId)
                }
                Button(onClick = { detail = null }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("关闭详情") }
            }
        }
    } }
}

@Composable
private fun FoundationPlanCard(plan: FoundationPlan, label: String, onExercise: (FoundationExercise) -> Unit) {
    var day by remember(plan.id) { mutableIntStateOf(plan.trainingDays.first()) }
    Card(colors = CardDefaults.cardColors(containerColor = if (plan.status == "draft") ReadyGymColors.AccentSurface else ReadyGymColors.Surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp).testTag("foundation-${plan.status}-v${plan.revision}"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(plan.title, fontWeight = FontWeight.SemiBold)
            Text("每周${plan.daysPerWeek}天 · 每次${plan.minutesPerSession}分钟\n训练日：${plan.trainingDays.joinToString("、") { weekdays[it - 1] }}")
            Text(if (plan.source == "rule_fallback") "来源：规则回退 rule_fallback · 未经专业审核" else "来源：Agent调用规则工具 agent_rule_tool · 未经专业审核", fontSize = 13.sp, color = ReadyGymColors.Accent)
            Text("不是医疗处方或安全认证；不推算负重。", fontSize = 13.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                plan.week.forEach { d -> TextButton(onClick = { day = d.dayOfWeek }, contentPadding = PaddingValues(0.dp), modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text("${d.dayOfWeek}${if (d.training) "练" else "休"}", fontWeight = if (day == d.dayOfWeek) FontWeight.Bold else FontWeight.Normal)
                } }
            }
            Text("${weekdays[day - 1]} / ${if (plan.week[day - 1].training) "训练" else "休息与恢复"}", fontWeight = FontWeight.Bold)
            plan.week[day - 1].exercises.forEach { x ->
                OutlinedCard(Modifier.fillMaxWidth().testTag("foundation-exercise-${x.slotId}").clickable(onClickLabel = "查看准确动作详情") { onExercise(x) }) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(FoundationContract.exerciseNames.getValue(x.exerciseId), fontWeight = FontWeight.Bold)
                        Text("${x.sets}组 × ${exerciseDose(x)} · 休息${x.restSeconds}秒", fontSize = 13.sp)
                        Text("查看动作详情与准确GIF →", color = ReadyGymColors.Action, fontSize = 13.sp)
                    }
                }
            }
            plan.notices.forEach { Text(it, fontSize = 12.sp, color = ReadyGymColors.MutedText) }
        }
    }
}
private fun exerciseDose(x: FoundationExercise) = x.seconds?.let { "${it}秒" } ?: "${x.repsMin}–${x.repsMax}次"
private val weekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
private fun fieldLabel(field: String) = when (field) {
    "days_per_week" -> "每周天数"; "minutes_per_session" -> "每次分钟"; "equipment" -> "可用器械"
    "unavailable_days" -> "没空的日子"; "training_days" -> "训练日"; else -> field
}
