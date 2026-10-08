package com.readygym.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readygym.core.*

private val Round = RoundedCornerShape(22.dp)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        setContent { ReadyGymTheme { ReadyGymApp() } }
    }
}

// Deliberately session-memory only. No savedInstanceState, disk or network.
@Composable
fun OnboardingScreen(onStartPlanning: (TrainingProfile) -> Unit = {}) {
    var flow by remember { mutableStateOf(Onboarding()) }
    var revision by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmed by remember { mutableStateOf(false) }
    val step = remember(revision, flow) { flow.step }
    val focus = LocalFocusManager.current
    val scroll = rememberScrollState()
    LaunchedEffect(step, flow) { scroll.scrollTo(0) }
    fun change(action: () -> Unit) { action(); revision++; error = null; confirmed = false }
    BackHandler(step != Step.PROFILE) { focus.clearFocus(); change { flow.back() } }
    val title = when (step) {
        Step.PROFILE -> "先认识一下你"
        Step.GOAL -> "你想练成什么样"
        Step.CONDITIONS -> "你的训练主场"
        Step.CONFIRM -> "检查一下，准备就绪"
    }
    val subtitle = when (step) {
        Step.PROFILE -> "从基础资料开始，建立本次健身档案。"
        Step.GOAL -> "选一个当前最重要的方向。"
        Step.CONDITIONS -> "选定地点，再勾选你能使用的器械。"
        Step.CONFIRM -> "这些是你刚填写的资料，随时可以修改。"
    }
    Surface(color = ReadyGymColors.Background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Column(Modifier.weight(1f).verticalScroll(scroll)
                .padding(horizontal = 22.dp).padding(top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).background(ReadyGymColors.Surface, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(46.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("READY GYM", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                    Spacer(Modifier.weight(1f))
                    Text("本地建档", fontSize = 12.sp, color = ReadyGymColors.MutedText)
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${step.ordinal + 1} / 4", color = ReadyGymColors.Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(when (step) { Step.PROFILE -> "基本资料"; Step.GOAL -> "健身目标"; Step.CONDITIONS -> "训练条件"; Step.CONFIRM -> "确认资料" }, color = ReadyGymColors.MutedText, fontSize = 13.sp)
                    }
                    Row(Modifier.fillMaxWidth().semantics { contentDescription = "建档进度，第${step.ordinal + 1}步，共4步" }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Step.entries.forEach { s -> Box(Modifier.weight(1f).height(4.dp).background(if (s.ordinal <= step.ordinal) ReadyGymColors.Accent else ReadyGymColors.Outline, RoundedCornerShape(4.dp))) }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(title, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold)
                    Text(subtitle, color = ReadyGymColors.MutedText, fontSize = 14.sp, lineHeight = 22.sp)
                }
                when (step) {
                    Step.PROFILE -> {
                        Column(Modifier.fillMaxWidth().background(ReadyGymColors.Surface, Round).padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            SectionLabel("01", "身体资料")
                            NumberField("年龄（岁）", "年龄", "岁", "18–120", flow.age, false) { change { flow.age = it } }
                            NumberField("身高（cm）", "身高", "cm", "100–250", flow.height, true) { change { flow.height = it } }
                            NumberField("体重（kg）", "体重", "kg", "30–300", flow.weight, true, true) { change { flow.weight = it } }
                            Text("以上为本版输入范围，不代表健康判断。", color = ReadyGymColors.MutedText, fontSize = 12.sp, lineHeight = 18.sp)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("性别", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Gender.entries.chunked(2).forEach { pair ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        pair.forEach { g ->
                                            Box(Modifier.weight(1f).heightIn(min = 52.dp).border(if (flow.gender == g) 2.dp else 1.dp, if (flow.gender == g) ReadyGymColors.Action else ReadyGymColors.Outline, RoundedCornerShape(14.dp))
                                                .background(if (flow.gender == g) ReadyGymColors.SelectedSurface else ReadyGymColors.Surface, RoundedCornerShape(14.dp)).selectable(flow.gender == g, role = Role.RadioButton) { change { flow.gender = g } }.padding(14.dp)) {
                                                Text((if (flow.gender == g) "✓  " else "") + g.label, fontSize = 14.sp)
                                            }
                                        }
                                    }
                                }
                            }
                            Text("可不透露 · 当前仅记录，不用于推荐", color = ReadyGymColors.MutedText, fontSize = 12.sp)
                        }
                        Text("仅限18岁及以上 · 资料仅在本次会话使用", color = ReadyGymColors.MutedText, fontSize = 12.sp, lineHeight = 20.sp)
                    }
                    Step.GOAL -> {
                        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Goal.entries.forEachIndexed { index, g ->
                                ChoiceCard(g.label, when(g) { Goal.MUSCLE -> "以肌肉增长为主要目标"; Goal.FAT_LOSS -> "以减少体脂为主要目标"; Goal.HEALTH -> "把日常运动变成习惯" }, flow.goal == g, Modifier.fillMaxWidth(), "0${index + 1}") { change { flow.goal = g } }
                            }
                        }
                    }
                    Step.CONDITIONS -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionLabel("01", "训练地点", "单选")
                            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Location.entries.forEach { l ->
                                    ChoiceCard(l.label, if(l == Location.HOME) "熟悉的空间" else "专注的场地", flow.location == l, Modifier.weight(1f), if(l == Location.HOME) "HOME" else "GYM") { change { flow.location = l } }
                                }
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionLabel("02", "可用器械", "多选 · 至少一项")
                            Equipment.entries.chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    pair.forEach { e -> EquipmentCard(e, e in flow.equipment, Modifier.weight(1f)) {
                                        change { flow.equipment = if (e in flow.equipment) flow.equipment - e else flow.equipment + e }
                                    } }
                                }
                            }
                        }
                    }
                    Step.CONFIRM -> {
                        val profile = flow.confirmedProfile()
                        if (profile != null) {
                            Column(Modifier.fillMaxWidth().background(ReadyGymColors.Surface, Round).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                SectionLabel("01", "基本资料")
                                SummaryRow("年龄", "${profile.age} 岁")
                                SummaryRow("身高", "${number(profile.heightCm)} cm")
                                SummaryRow("体重", "${number(profile.weightKg)} kg")
                                SummaryRow("性别", profile.gender.label)
                            }
                            Column(Modifier.fillMaxWidth().background(ReadyGymColors.Surface, Round).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                SectionLabel("02", "目标与条件")
                                SummaryRow("目标", profile.goal.label)
                                SummaryRow("地点", profile.location.label)
                                SummaryRow("器械", Equipment.entries.filter { it in profile.equipment }.joinToString("、") { it.label })
                            }
                            Text(if (confirmed) "✓  资料已确认，仅本次会话有效。" else "确认仅用于本次会话，不会保存或上传。", color = if (confirmed) ReadyGymColors.Accent else ReadyGymColors.MutedText, fontSize = 13.sp, lineHeight = 21.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            Text("下一步可制定本地规则草稿，尚未接入AI。", color = ReadyGymColors.MutedText, fontSize = 12.sp)
                        }
                    }
                }
                if(step != Step.PROFILE) Text("资料仅在本次会话使用 · 不保存、不上传", color = ReadyGymColors.MutedText, fontSize = 12.sp)
                UsageNotes()
                TextButton(onClick = { focus.clearFocus(); change { flow = Onboarding() } }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("清空本次资料", color = ReadyGymColors.MutedText, fontSize = 13.sp) }
            }
            Column(Modifier.fillMaxWidth().background(ReadyGymColors.Background).padding(horizontal = 22.dp).padding(top = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let { Text("!  $it", color = MaterialTheme.colorScheme.error, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
                Button(onClick = {
                    focus.clearFocus()
                    if (step == Step.CONFIRM) confirmed = true
                    else if (flow.next()) change {} else error = when (step) {
                        Step.PROFILE -> "请填写有效的年龄、身高和体重；仅支持18岁及以上。"
                        Step.GOAL -> "请选择一个健身目标。"
                        else -> "请选择训练地点，并至少选择一项器械（可选徒手）。"
                    }
                }, enabled = !confirmed, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ReadyGymColors.Action, contentColor = ReadyGymColors.OnAction,
                        disabledContainerColor = ReadyGymColors.AccentSurface, disabledContentColor = ReadyGymColors.Accent)) {
                    Text(if(step == Step.CONFIRM) { if (confirmed) "已确认" else "确认本次资料" } else "下一步", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    if (!confirmed) { Spacer(Modifier.width(14.dp)); Text("→", fontSize = 22.sp) }
                }
                if (confirmed) Button(onClick = { flow.confirmedProfile()?.let(onStartPlanning) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
                    Text("制定训练计划", fontWeight = FontWeight.Bold)
                }
                if (step != Step.PROFILE) TextButton(onClick = { focus.clearFocus(); change { flow.back() } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("返回修改", color = ReadyGymColors.MutedText) }
            }
        }
    }
}

@Composable
private fun UsageNotes() {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().border(1.dp, ReadyGymColors.Outline, RoundedCornerShape(16.dp))) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { stateDescription = if(expanded) "已展开" else "已收起" }) {
            Text("使用说明", color = ReadyGymColors.MutedText, fontSize = 13.sp)
            Spacer(Modifier.weight(1f)); Text(if(expanded) "−" else "+", color = ReadyGymColors.MutedText, fontSize = 20.sp)
        }
        if(expanded) Text("仅面向18岁及以上的一般健身建档。本版提供未专业审核的规则草稿，未接AI；不提供医疗建议，不根据BMI推断能力。资料不保存、不上传；旋转、退出或Activity重建后会清空。性别当前仅作记录，不用于推荐。", color = ReadyGymColors.MutedText, fontSize = 12.sp, lineHeight = 21.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp))
    }
}

@Composable
private fun NumberField(accessibilityLabel: String, label: String, unit: String, range: String, value: String, decimal: Boolean, last: Boolean = false, onChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        OutlinedTextField(value, onChange, placeholder = { Text(range, color = ReadyGymColors.MutedText) },
            suffix = { Text(unit, color = ReadyGymColors.MutedText) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = if(decimal) KeyboardType.Decimal else KeyboardType.Number, imeAction = if(last) ImeAction.Done else ImeAction.Next),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            shape = RoundedCornerShape(14.dp), textStyle = LocalTextStyle.current.copy(fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = ReadyGymColors.Outline, focusedBorderColor = ReadyGymColors.Accent, focusedLabelColor = ReadyGymColors.Accent), modifier = Modifier.fillMaxWidth().semantics { contentDescription = accessibilityLabel })
    }
}

@Composable
private fun SectionLabel(index: String, title: String, hint: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(index, color = ReadyGymColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(9.dp)); Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        if(hint != null) { Spacer(Modifier.weight(1f)); Text(hint, fontSize = 11.sp, color = ReadyGymColors.MutedText) }
    }
}

@Composable
private fun ChoiceCard(title: String, detail: String, selected: Boolean, modifier: Modifier, marker: String, onClick: () -> Unit) {
    Column(modifier.border(if(selected) 2.dp else 1.dp, if(selected) ReadyGymColors.Action else ReadyGymColors.Outline, Round)
        .background(if(selected) ReadyGymColors.SelectedSurface else ReadyGymColors.Surface, Round)
        .selectable(selected, role = Role.RadioButton, onClick = onClick).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(marker, color = if(selected) ReadyGymColors.Action else ReadyGymColors.MutedText, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            SelectionMark(selected, radio = true)
        }
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(detail, color = ReadyGymColors.MutedText, fontSize = 12.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun EquipmentCard(e: Equipment, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val art = when(e) {
        Equipment.BODYWEIGHT -> R.drawable.equipment_bodyweight
        Equipment.DUMBBELL -> R.drawable.equipment_dumbbell
        Equipment.BARBELL -> R.drawable.equipment_barbell
        Equipment.MACHINE -> R.drawable.equipment_machine
    }
    Column(modifier.border(if(selected) 2.dp else 1.dp, if(selected) ReadyGymColors.Action else ReadyGymColors.Outline, Round)
        .background(if(selected) ReadyGymColors.SelectedSurface else ReadyGymColors.Surface, Round)
        .toggleable(selected, role = Role.Checkbox, onValueChange = { onClick() }).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.CenterEnd) { SelectionMark(selected, radio = false) }
        Image(painterResource(art), "${e.label}器械插画", Modifier.fillMaxWidth().height(100.dp))
        Text(e.label, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(when(e) { Equipment.BODYWEIGHT -> "以自身体重训练"; Equipment.DUMBBELL -> "自由重量 · 哑铃"; Equipment.BARBELL -> "自由重量 · 杠铃"; Equipment.MACHINE -> "绳索或固定轨迹" }, color = ReadyGymColors.MutedText, fontSize = 11.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun SelectionMark(selected: Boolean, radio: Boolean) {
    Box(Modifier.size(24.dp).background(if(selected) ReadyGymColors.Accent else Color.Transparent, if(radio) RoundedCornerShape(12.dp) else RoundedCornerShape(6.dp))
        .border(if(selected) 0.dp else 1.dp, if(selected) ReadyGymColors.Accent else ReadyGymColors.MutedText, if(radio) RoundedCornerShape(12.dp) else RoundedCornerShape(6.dp)).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        if(selected) Text("✓", color = ReadyGymColors.Surface, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$label：$value" }, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(label, color = ReadyGymColors.MutedText, fontSize = 14.sp, modifier = Modifier.width(44.dp))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}
private fun number(value: Double) = if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
