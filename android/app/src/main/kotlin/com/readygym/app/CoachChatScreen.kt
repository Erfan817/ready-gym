package com.readygym.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import java.io.IOException
import kotlinx.coroutines.*

/** Presentation source is provided by the server; never pretend a policy string is model prose. */
data class CoachBubble(val user: Boolean, val text: String, val source: String? = null)

/** Memory only; ViewModel retains this conversation over rotation, not process death. */
class CoachConversation(private val client: CoachClient) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    var input by mutableStateOf("")
        private set
    var busy by mutableStateOf(false)
        private set
    var session by mutableStateOf<CoachSession?>(null)
        private set
    var error by mutableStateOf<CoachException?>(null)
        private set
    var reply by mutableStateOf<CoachReply?>(null)
        private set
    var messages by mutableStateOf<List<CoachBubble>>(emptyList())
        private set
    var correction by mutableStateOf(false)
        private set
    private var retryAction: (() -> Unit)? = null
    fun edit(text: String) { if (!busy) { input = text; error = null; retryAction = null } }
    fun correct() { if (!busy) { reply = reply?.copy(confirmedCandidate = null); correction = true } }
    fun start() { if (busy || session != null) return; launch(null) }
    fun send() {
        if (busy || session == null) return
        if (input.isBlank() || input.length > 1000) { error = CoachException("invalid_input"); return }
        launch(input)
    }
    fun retry() {
        if (busy) return
        retryAction?.invoke() ?: if (session == null) start() else send()
    }
    fun confirm(onConfirmed: (CoachCandidate) -> Unit) {
        val reviewed = reply ?: return
        if (busy || input.isNotBlank() || reviewed.confirmedCandidate == null) return
        busy = true; error = null; retryAction = { confirm(onConfirmed) }
        scope.launch {
            try {
                val accepted = client.confirm(reviewed)
                if (accepted.status != "ready" || accepted.confirmedCandidate != reviewed.confirmedCandidate) throw CoachException("invalid_response")
                reply = accepted; retryAction = null
                onConfirmed(requireNotNull(accepted.confirmedCandidate))
            } catch (e: CoachException) { error = e }
            catch (_: IOException) { error = CoachException("network_error", true) }
            finally { busy = false }
        }
    }
    fun clear() {
        val current = session ?: return
        if (busy) return
        busy = true; error = null; retryAction = { clear() }
        scope.launch {
            try {
                client.delete(current)
                session = null; reply = null; messages = emptyList(); input = ""; correction = false; retryAction = null
            } catch (e: CoachException) { error = e }
            catch (_: IOException) { error = CoachException("network_error", true) }
            finally { busy = false }
        }
    }
    fun restart() {
        if (busy) return
        session = null; reply = null; error = null; messages = emptyList(); correction = false; retryAction = null
        // Keep unsent input. No network until another explicit consent tap.
    }
    private fun launch(text: String?) {
        busy = true; error = null; retryAction = { launch(text) }
        reply = reply?.copy(confirmedCandidate = null)
        scope.launch {
            try {
                val result = if (text == null) client.start() else client.message(requireNotNull(session), text)
                session = result.session
                if (text != null) { messages = messages + CoachBubble(true, text); input = "" }
                messages = messages + CoachBubble(false, result.assistantText, result.responseSource)
                reply = result; correction = false; retryAction = null
            } catch (e: CoachException) { error = e }
            catch (_: IOException) { error = CoachException("network_error", true) }
            finally { busy = false }
        }
    }
    override fun onCleared() { scope.cancel() }
}

/** Welcome copy is product guidance, never a pretend model question. */
@Composable
fun CoachChatScreen(coach: CoachConversation, serviceConfigured: Boolean, onOffline: () -> Unit, onConfirmed: (CoachCandidate) -> Unit) {
    val scroll = rememberScrollState()
    LaunchedEffect(coach.messages.size, coach.reply?.confirmedCandidate) { scroll.animateScrollTo(scroll.maxValue) }
    Surface(color = ReadyGymColors.Background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp)) {
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(36.dp))
                Column(Modifier.padding(start = 10.dp)) {
                    Text("READY GYM", fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                    Text(if (coach.session == null) "AI建档 · 尚未开始" else "AI建档 · 服务端会话", fontSize = 12.sp, color = ReadyGymColors.Accent)
                }
            }
            Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 22.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("你的AI训练教练", fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold)
                if (coach.session == null) {
                    Text("用自己的话聊聊就好。我会了解你的目标、训练条件和当前身体状态，再请你确认资料。", color = ReadyGymColors.MutedText, lineHeight = 24.sp)
                    Card(colors = CardDefaults.cardColors(containerColor = ReadyGymColors.Surface)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("开始前，请你知情", fontWeight = FontWeight.Bold)
                            Text("同意后，你的回答与训练资料会发送至Ready Gym服务端和DeepSeek，用于AI问询。仅限18岁及以上；不要发送姓名、联系方式等无关资料。", fontSize = 14.sp, lineHeight = 22.sp)
                            Text(if (serviceConfigured) "服务地址已配置，尚未连接；点击后才请求AI首问。" else "服务未配置，不能连接AI", color = if (serviceConfigured) ReadyGymColors.MutedText else MaterialTheme.colorScheme.error, fontSize = 13.sp)
                            Button(onClick = { coach.start() }, enabled = serviceConfigured && !coach.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
                                Text(if (coach.busy) "正在连接…" else "同意并开始")
                            }
                        }
                    }
                    Text("不会用本地规则假装模型回答。AI个性化计划尚未接入，确认后可预览规则计划底座。", fontSize = 13.sp, lineHeight = 21.sp, color = ReadyGymColors.MutedText)
                }
                coach.messages.forEachIndexed { index, bubble ->
                    val user = bubble.user; val text = bubble.text
                    Column(Modifier.fillMaxWidth().testTag("coach-message-$index")
                        .background(if (user) ReadyGymColors.SelectedSurface else ReadyGymColors.Surface, RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (user) "你" else if (bubble.source == "server_policy") "教练 · 服务端整理" else "AI教练 · 服务端回复", color = ReadyGymColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(text, lineHeight = 24.sp)
                    }
                }
                if (coach.reply?.status == "blocked") Text("当前资料不适合普通规则计划。请停止训练并按服务端提示寻求专业评估；可继续说明或纠正资料。", color = MaterialTheme.colorScheme.error)
                if (coach.correction) Text("直接告诉教练哪里不对，例如“我是在家训练”。修改后会重新整理摘要。", color = ReadyGymColors.MutedText)
                coach.reply?.confirmedCandidate?.let { candidate ->
                    Card(colors = CardDefaults.cardColors(containerColor = ReadyGymColors.AccentSurface)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val p = candidate.profile; val pref = candidate.preferences
                            Text("请确认你的资料", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("年龄：${p.age}岁 · 身高：${p.heightCm} cm · 体重：${p.weightKg} kg\n性别：${p.gender.label}")
                            Text("目标：${p.goal.label} · 地点：${p.location.label}\n器械：${p.equipment.joinToString("、") { it.label }}")
                            Text("每周${pref.daysPerWeek}天 · 每次${pref.minutesPerSession}分钟 · ${if (pref.experience == com.readygym.core.TrainingExperience.BEGINNER) "初学" else "恢复训练"}")
                            Text("你明确自述：没有当前不适、没有医学训练限制。不是安全认证。", fontSize = 13.sp)
                            Text("下一步是未经专业审核的规则计划底座，不是AI生成计划。", fontSize = 13.sp, color = ReadyGymColors.MutedText)
                            Button(onClick = { coach.confirm(onConfirmed) }, enabled = !coach.busy && coach.input.isBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("确认资料，进入规则计划") }
                            TextButton(onClick = { coach.correct() }, enabled = !coach.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("这些信息不对") }
                        }
                    }
                }
                if (coach.session != null) TextButton(onClick = { coach.clear() }, enabled = !coach.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("清空对话并删除服务端会话") }
                OutlinedButton(onClick = onOffline, enabled = !coach.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("离线规则模式（不使用AI）") }
                Text("对话与输入旋转后保留，关闭或进程回收会清空；规则计划仅本次页面内存。服务端会话受过期限制，未实现账号或历史同步。", fontSize = 12.sp, lineHeight = 20.sp, color = ReadyGymColors.MutedText)
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                coach.error?.let { error ->
                    Text(coachErrorText(error.code), color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
                    if (error.retryable) TextButton(onClick = { coach.retry() }, enabled = !coach.busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("重试") }
                    if (error.code in setOf("session_expired", "session_not_found", "invalid_session_token", "http_401", "http_404", "http_410")) TextButton(onClick = { coach.restart() }, enabled = !coach.busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("重新开始会话") }
                }
                if (coach.busy) Text("正在等待服务端…", fontSize = 13.sp, color = ReadyGymColors.MutedText, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                OutlinedTextField(coach.input, { coach.edit(it) }, enabled = coach.session != null && !coach.busy, label = { Text("告诉教练（最多1000字）") },
                    minLines = 1, maxLines = 3, isError = coach.input.length > 1000, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("coach-input"))
                if (coach.input.length > 1000) Text("请缩短到1000字以内；输入仍保留。", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                Button(onClick = { coach.send() }, enabled = coach.session != null && !coach.busy && coach.input.isNotBlank() && coach.input.length <= 1000,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp)) { Text("发送") }
            }
        }
    }
}

private fun coachErrorText(code: String) = when (code) {
    "api_config_not_set" -> "服务未配置，不能连接AI"
    "provider_not_configured" -> "服务端未配置DeepSeek，尚未连接AI。"
    "insecure_endpoint" -> "服务地址不安全：只允许HTTPS或Debug本机测试。"
    "invalid_input" -> "请填写1–1000字，输入未丢失。"
    "invalid_response", "response_too_large", "unexpected_redirect" -> "服务端响应不符合契约，不能采用资料；输入已保留。"
    "session_expired", "session_not_found", "invalid_session_token", "http_401", "http_404", "http_410" -> "会话已失效，请重新开始；本次输入仍保留。"
    else -> "暂时无法取得AI回复，输入已保留；可重试。"
}
