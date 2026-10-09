# Android 教练会话契约（v0.3.0）

## 真实状态

App已具备服务端HTTP会话客户端与单屏Compose对话路径；交付包**服务地址为空、未连接AI**。当前无Ready Gym专用DeepSeek Key、生产HTTPS、用户鉴权或费用配额。测试fixture不满足真实AI验收；客户端没有本地regex问询或模拟model回退。真正AI个性化计划provider未实现，资料确认后的规则计划不是AI计划。

## Debug配置

构建时由Gradle property `readyGymServiceBaseUrl` 或环境变量 `READY_GYM_SERVICE_BASE_URL` 注入Debug `BuildConfig.SERVICE_BASE_URL`；property优先，默认为空。Release值强制为空。无配置仍可使用可选离线入口；不会主动发送任何资料。

URL必须为API origin（无用户名/密码、query、fragment或路径）；客户端自行加 `/api/intake/sessions`。除Debug的`localhost`/`127.0.0.1`外只允许HTTPS。没有硬编码生产IP；session token仅会话级授权，不等于生产用户认证。

本机开发服务默认loopback；`127.0.0.1`指手机自身，**不是服务器地址**。仅在USB授权后通过`adb reverse tcp:8000 tcp:8000`桥接到本机开发服务，或提供另行安全部署的HTTPS origin。不要公开暴露开发FastAPI、不要为调试启用LAN/公网明文或绕过TLS。默认发布测试包不配置loopback URL。

```sh
# 仅用于明确知情的USB loopback开发测试，不代表已提供Key
adb reverse tcp:8000 tcp:8000
./gradlew :app:assembleDebug -PreadyGymServiceBaseUrl=http://127.0.0.1:8000
```

## 接口与防护

用户明确点击“同意并开始”后POST `/api/intake/sessions`，JSON `{"consent":true}`。服务端返回session_id、session_token、assistant_text、status、validated_profile、missing_fields、provider_mode。App显示服务端回答，不客户端预编首问；`response_source=server_policy`显示“教练 · 服务端整理”，不把策略问句当作未经处理的模型文本。

`POST /api/intake/sessions/{session_id}/messages`以会话Bearer token授权，JSON `{"message":"用户原始回答"}`；不裁剪/代写用户内容。最大1000个UTF-16 code units（UI以“字”作简短提示），blank不发送。busy禁用，失败保留输入并允许人工重试，无自动重试。连接超时10秒、读超时30秒、响应上限65536字节；不跟随重定向、不记录请求body或token。

仅`provider_mode=deepseek_official`且status为collecting/ready/blocked可采用；ready必须missing_fields空且完整validated_profile通过App范围/类型/枚举检查。安全两字段必须明确服务端sentinel `none`，null、blank、含不适文本或“已安全”assistant_text不能产生可确认摘要。成人年龄18–120、身高100–250、体重30–300、频率2/3/4、时间20/30/45/60、新手/恢复经验、真实非空器械；性别可null/未提供，不推断。blocked无确认入口，可自然语言继续说明。

摘要“这些信息不对”回对话纠正，输入未发送时禁用确认。只有用户明确点击后，POST `/api/intake/sessions/{id}/confirm`携带 `{"confirmed":true,"expected_revision":N}`；核对响应与本次摘要和revision一致，再GET精确会话target读回confirmed=true及同一快照，才映射TrainingProfile/PlanningPreferences进入规则计划底座。服务端必须同样校验完整度、安全、版本；不允许仅模型说ready/安全即生效。

“清空对话并删除服务端会话”DELETE精确target，GET核实404后清空本地；失败保留数据可重试。会话过期可重新开始，保留未发送input且要求再次同意。关闭App不是即时服务端删除；依赖服务端过期，不宣称已删除。

## 生命周期与测试证据

对话、未发送input、状态/token在Activity ViewModel内存，配置重建保留；无磁盘持久化、账号或历史同步。进程回收/finish清空；规则资料和计划仍为原页面session-memory，不能声称跨重建保存。

CoachChatScreenTest使用明确TestOnlyCoachClient执行Compose行为；CoachHttpContractTest使用真实ServerSocket loopback HTTP验证方法、路径、Bearer、UTF-8、限长、禁止公网HTTP、响应类型/ready、安全、confirm/delete精确读回。没有新增Retrofit/OkHttp依赖，网络用平台HttpURLConnection/HttpsURLConnection。

实际RED/GREEN、全量build日志和PNG在 `build/verification/chat-tdd/`（忽略Git）。无手机/模拟器，IME/TalkBack/OEM体验与真实DeepSeek一次有限合成档案验收待完成。生产TLS、配对/认证、费用配额、用户隐私政策和真实AI计划provider是下一步，不以契约测试假称已连模型。
