package com.readygym.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.readygym.core.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface { OnboardingScreen() } } }
    }
}

// Session memory only: no disk, account, telemetry or server storage.
@Composable
fun OnboardingScreen() {
    var flow by remember { mutableStateOf(Onboarding()) }
    var revision by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmed by remember { mutableStateOf(false) }
    // The domain model is Android-independent; revision publishes its mutations.
    val step = remember(revision, flow) { flow.step }
    fun change(action: () -> Unit) { action(); revision++; error = null; confirmed = false }
    BackHandler(step != Step.PROFILE) { change { flow.back() } }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
        .verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Ready Gym", style = MaterialTheme.typography.headlineLarge)
        Text("${step.ordinal + 1} / 4 · " + when (step) {
            Step.PROFILE -> "基本资料"; Step.GOAL -> "健身目标"
            Step.CONDITIONS -> "训练条件"; Step.CONFIRM -> "确认资料"
        }, style = MaterialTheme.typography.titleLarge)
        Text("仅面向18岁及以上的一般健身建档。本版不提供医疗建议或训练计划；不根据BMI推断能力。")
        Text("资料仅在本次页面会话内存中使用，不上传、不写入磁盘。退出或系统回收后需重新填写；可随时清空。")
        when (step) {
            Step.PROFILE -> {
                OutlinedTextField(flow.age, { change { flow.age = it } }, label = { Text("年龄（岁）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(flow.height, { change { flow.height = it } }, label = { Text("身高（cm）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(flow.weight, { change { flow.weight = it } }, label = { Text("体重（kg）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("输入范围：年龄18–120整数；身高100–250cm；体重30–300kg。范围仅为本版输入边界，不是健康判断。")
                Text("性别（可不透露）：仅建档记录，当前不用于推荐。")
                Gender.entries.forEach { g -> FilterChip(flow.gender == g, { change { flow.gender = g } }, label = { Text(g.label) }) }
            }
            Step.GOAL -> Goal.entries.forEach { g -> FilterChip(flow.goal == g, { change { flow.goal = g } }, label = { Text(g.label) }) }
            Step.CONDITIONS -> {
                Text("训练地点（单选）")
                Location.entries.forEach { l -> FilterChip(flow.location == l, { change { flow.location = l } }, label = { Text(l.label) }) }
                Text("可用器械（多选，至少一项；仅徒手也可）")
                Equipment.entries.forEach { e -> FilterChip(e in flow.equipment, { change { flow.equipment = if (e in flow.equipment) flow.equipment - e else flow.equipment + e } }, label = { Text(e.label) }) }
            }
            Step.CONFIRM -> {
                val profile = flow.confirmedProfile()
                if (profile != null) {
                    Text("年龄：${profile.age} 岁")
                    Text("身高：${profile.heightCm} cm")
                    Text("体重：${profile.weightKg} kg")
                    Text("性别：${profile.gender.label}")
                    Text("目标：${profile.goal.label}")
                    Text("地点：${profile.location.label}")
                    Text("器械：" + Equipment.entries.filter { it in profile.equipment }.joinToString("、") { it.label })
                    Button(onClick = { confirmed = true }, enabled = !confirmed) { Text("确认资料（仅本次会话）") }
                    if (confirmed) Text("资料已确认，仅本次会话有效。训练计划与Agent尚未接入。")
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (step != Step.CONFIRM) Button(onClick = {
            if (flow.next()) change {} else error = when (step) {
                Step.PROFILE -> "请填写有效的年龄、身高和体重；仅支持18岁及以上。"
                Step.GOAL -> "请选择一个健身目标。"
                else -> "请选择训练地点，并至少选择一项器械（可选徒手）。"
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("下一步") }
        if (step != Step.PROFILE) OutlinedButton(onClick = { change { flow.back() } }) { Text("返回修改") }
        TextButton(onClick = { change { flow = Onboarding() } }) { Text("清空本次资料") }
    }
}
