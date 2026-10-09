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

`POST /api/intake/sessions/{session_id}/messages`以会话Bearer token授权，JSON `{"message":"用户原始回答"}`；不裁剪/代写用户内容。最大1000个UTF-16 code units（UI以“字”作简短提示），blank不发送。busy禁用且retry逻辑同样guard，失败保留输入并允许人工重试，无自动模型重试。网络/超时错误明确提示请求可能已计费，只有用户点击才再次请求。连接超时10秒、读超时30秒、响应上限65536字节；不跟随重定向、不记录请求body或token。

仅`provider_mode=deepseek_official`且status为collecting/ready/blocked可采用。所有start/messages/confirm响应及GET确认读回均要求显式`safety_route`键：collecting/ready必须为JSON null；blocked必须精确为`stop_and_seek_care`、`professional_review`或`unsupported_experience`之一，不trim、不猜测、不接受其他类型。缺失、未知值、类型错误或status不一致均拒绝invalid_response；契约不明保守保留专业评估阻断，已辨识急性路线即使其余响应无效也保留更高风险。不能把契约缺失当作没有风险。

ready必须missing_fields空、可选clarification_fields若出现必须为空数组，且完整validated_profile通过App范围/类型/枚举检查。旧档案完整不代表尚待澄清的修改已解决。安全两字段必须明确服务端sentinel `none`，null、blank、含不适文本或“已安全”assistant_text不能产生可确认摘要。成人年龄18–120、身高100–250、体重30–300、频率2/3/4、时间20/30/45/60、新手/恢复经验、真实非空器械；性别可null/未提供，不推断。blocked无确认入口，可自然语言继续说明。`unsupported_experience`仅表示模板不支持该训练经验，不产生急性/医学风险latch，也不显示就医命令。

摘要“这些信息不对”回对话纠正，输入未发送时禁用确认。只有用户明确点击后，POST `/api/intake/sessions/{id}/confirm`携带 `{"confirmed":true,"expected_revision":N}`；核对响应与本次摘要和revision一致，再GET精确会话target读回confirmed=true及同一快照，才映射TrainingProfile/PlanningPreferences进入规则计划底座。服务端必须同样校验完整度、安全、版本；不允许仅模型说ready/安全即生效。

“清空对话并删除服务端会话”DELETE精确target，再GET同一target。DELETE响应丢失或实际404可继续GET核实，不能把DELETE404直接当成功。只有GET的**实际HTTP status=404**且code为session_not_found或明确http_404才清空本地；GET500即使body写session_not_found仍失败，GET200、401、其他错误或无读回证据均保留本地数据。读回暂时失败后，人工重试DELETE404仍必须重新GET取证。CoachException保留httpStatus，不只根据body代码推断成功。会话过期、session_unauthorized或实际401提供重新开始入口与失效提示，保留未发送input且要求再次明确同意；重新开始本身不请求网络。关闭App不是即时服务端删除；依赖服务端过期，不宣称已删除。

规则计划里的安全选择/文字每次更改立即更新本地最高风险，不以关闭反馈弹窗当作撤销自述。经HTTP契约验证的`stop_and_seek_care`与`professional_review`也在每次start/message/confirm结果（包括GET读回）返回时、摘要使用前记入同一Activity latch；confirm拒绝新风险快照时通过CoachException传递安全反馈，不能因抛错丢失风险。路线明确映射固定的本地SafetyFeedback策略标签，STOP标签由真实PlanningEngine急性关键词guard进入STOP，专业评估标签进入medicalRestrictions；不是从assistant_text猜症状，也不复述未验证profile作诊断。后续lower-route、unsupported或safe都不降最高risk。

Activity级CoachConversation保留该风险，TrainingPlanScreen的生成、确认、换动作均读取当前风险；旧的无风险回调也不能在同帧绕过。返回对话明确旧摘要已失效，保留摘要按钮禁用且confirm逻辑也guard。旧摘要、新的无风险AI摘要、离线表单、重新开始/清空服务端会话、旋转均不能降低本次风险；急性风险必须停止训练，App不提供自行清除或以无风险填写解除的入口。这是保守阻断，不是医疗判断或跨进程保护；本地反馈不自动上传服务端或模型。

## 用户批准的统一响应异常边界

`checkedIntakeResponse`统一包住start、messages、confirm及其POST/GET读回。2xx root-array、损坏JSON、截断JSON等解码前错误和结构化契约错误都携带保守专业评估反馈；响应过大、重定向也不能丢失阻断。已验证的更强安全反馈保留，不从未解析片段猜STOP症状。删除的实际404取证独立，不把网络失败伪装成已删除。`parseReply`内部所有校验均属于服务端响应；非法返回session_id/token也统一为`invalid_response`并带已解析STOP或保守PRO反馈，不能以`invalid_session`漏出。请求前本地session校验仍独立返回`invalid_session`、不假造远端风险。

新增两个真实HTTP/Compose回归先运行时RED再GREEN：3种损坏body×4种建档请求，以及HTTP→Activity保留输入/风险→离线建档→真实PlanningEngine拒绝草稿/确认。完整全量由父重新执行；最新统计与artifact见`../README.md`。证据目录`build/verification/v0.3.0/shared-boundary-fixes/`。测试场景循环用Compose `key(client)`隔离各client的remember状态，不改产品逻辑掩盖测试问题；最初编译参数错误和运行时RED日志分开保留。

## 生命周期与测试证据

对话、未发送input、状态/token与最高本地风险在Activity ViewModel内存，配置重建保留；无磁盘持久化、账号或历史同步。进程回收/finish清空；规则资料和计划仍为原页面session-memory，不能声称跨重建保存，不能声称风险跨进程留存。

CoachChatScreenTest使用明确TestOnlyCoachClient执行额外Compose行为；CoachHttpContractTest使用真实ServerSocket loopback HTTP验证方法、路径、Bearer、UTF-8、限长、禁止公网HTTP、响应类型/ready、安全、confirm/delete精确读回。新增CoachStructuredSafetyTest全部通过真实HttpCoachClient + ServerSocket，主slice实际201 start→200 blocked message→离线OnboardingScreen→真实PlanningEngine，断言不出草稿/确认入口；覆盖STOP、professional、unsupported不误latch、未知/缺失/类型/status错误的fail-closed、confirm POST和GET风险丢失、clear/restart/Activity recreate、新无风险摘要及lower-route不能降级。没有新增Retrofit/OkHttp依赖，网络用平台HttpURLConnection/HttpsURLConnection。

原建档实际RED/GREEN、全量build日志和PNG在 `build/verification/chat-tdd/`（忽略Git）。第一轮审查修复证据在 `build/verification/v0.3.0/android-review-fixes/`。第二轮限定安全路线修复的四组运行时RED→GREEN、JUnit XML与results.json在 `build/verification/v0.3.0/second-android-fixes/`；`09-final-targeted-regression`实跑CoachChatScreenTest 18、CoachHttpContractTest 21、CoachStructuredSafetyTest 13、PlanningScreenTest 12，合计64通过，0失败/错误/跳过。保留clarification、计费提醒、真实delete404/500与401恢复、retry busy和同帧安全回归。此过滤运行不是最终全量测试或APK验收；最后fullbuild由集成负责人单独执行。无手机/模拟器，IME/TalkBack/OEM体验与真实DeepSeek一次有限合成档案验收待完成。生产TLS、配对/认证、费用配额、用户隐私政策和真实AI计划provider是下一步，不以契约测试假称已连模型。
