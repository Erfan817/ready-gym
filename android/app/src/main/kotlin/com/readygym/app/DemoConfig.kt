package com.readygym.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import java.net.URI

/** A prefix consists only of literal safe path segments; never resolve an absolute API path against it. */
internal object DemoEndpoint {
    fun validate(value: String, debug: Boolean): URI {
        val root = try { URI(value) } catch (_: Exception) { throw CoachException("insecure_endpoint") }
        if (value.length > 2048 || root.host.isNullOrBlank() || root.rawUserInfo != null || root.rawQuery != null || root.rawFragment != null ||
            root.port !in -1..65535 || root.port == 0 || !root.rawPath.orEmpty().matches(Regex("(?:/[A-Za-z0-9_-]+)*/?")) ||
            (root.scheme != "https" && !(debug && root.scheme == "http" && root.host in setOf("127.0.0.1", "localhost")))) throw CoachException("insecure_endpoint")
        return root
    }
}

/** Activity memory only. No preferences, Bundle, logs, BuildConfig secret, or model credentials. */
internal class DemoConfig : ViewModel(), CoachClient {
    var url by mutableStateOf(BuildConfig.SERVICE_BASE_URL.ifBlank { "https://150.158.125.67:26100/ready-gym-demo" })
        private set
    private var transport: HttpCoachClient? = null
    var configured by mutableStateOf(false)
        private set
    override val supportsFoundation = true
    fun configure(value: String, accessCode: String) {
        DemoEndpoint.validate(value, BuildConfig.DEBUG)
        if (accessCode.isEmpty() || accessCode.length > 256 || accessCode.any { it.code !in 33..126 }) throw CoachException("invalid_demo_config")
        url = value
        transport = HttpCoachClient(value, BuildConfig.DEBUG, supportsFoundation = true, demoAccessCode = accessCode)
        configured = true
    }
    private fun client() = transport ?: throw CoachException("api_config_not_set")
    override suspend fun start() = client().start()
    override suspend fun message(session: CoachSession, text: String) = client().message(session, text)
    override suspend fun confirm(reply: CoachReply) = client().confirm(reply)
    override suspend fun confirmPlan(reply: CoachReply) = client().confirmPlan(reply)
    override suspend fun readFoundation(session: CoachSession) = client().readFoundation(session)
    override suspend fun delete(session: CoachSession) = client().delete(session)
}

@Composable
internal fun DemoConfigDialog(config: DemoConfig, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf(config.url) }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Demo连接设置") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("仅在本次Activity内存保留；进程退出后需重新输入。保存不会联网，仍需明确同意才发送训练资料。")
            OutlinedTextField(url, { url = it; error = null }, label = { Text("HTTPS服务地址（保留路径前缀）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(code, { code = it; error = null }, label = { Text("Demo访问码") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = {
        TextButton(onClick = {
            try { config.configure(url, code); code = ""; onDismiss() }
            catch (e: CoachException) { error = if (e.code == "insecure_endpoint") "仅允许安全HTTPS与字面路径前缀；不允许凭据、查询或路径穿越。" else "请输入有效访问码（不含空白或控制字符）。" }
        }) { Text("保存到本次内存") }
    }, dismissButton = { TextButton(onClick = { code = ""; onDismiss() }) { Text("取消") } })
}
