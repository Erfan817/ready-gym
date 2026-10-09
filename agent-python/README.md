# Ready Gym 对话建档开发服务

**仅收集资料与显式确认，不生成训练计划、不代替医疗评估。** 使用真正的DeepSeek官方HTTP适配器，无模拟兜底；未配置Key返回503。本轮没有读取任何真实Key，也没有执行真实DeepSeek付费调用，账号可用性/真实模型响应仍待有限验收。

## 本地运行（单进程、仅回环）

项目目录实际是`/home/ubuntu/ready gym /`，`gym`后有一个空格，命令必须保留。

```bash
cd '/home/ubuntu/ready gym /agent-python'
uv venv --python /usr/bin/python3 .venv
uv pip install --python .venv/bin/python -r requirements.txt
.venv/bin/python -W error -m unittest discover -s tests -v
# 默认工厂不读取任何Key或.env，建档请求明确503：
.venv/bin/python -m uvicorn ready_gym_intake:create_app --factory --host 127.0.0.1 --port 8787 --workers 1 --no-access-log
```

已固定FastAPI、Pydantic、httpx、uvicorn版本，`.venv/`已忽略。不修改Hermes运行环境。若系统`python -m venv`缺ensurepip可使用上面的uv；若PyPI超时，可显式选择可信镜像安装相同固定版本。不要运行任意安装脚本。

仅在用户授权配置后选择以下工厂之一（不要把Key放进命令、聊天、APK或Git）：

- `ready_gym_intake:create_app_from_env`：只在显式调用工厂时读取专用进程`DEEPSEEK_API_KEY`；不自动搜dotenv文件。
- `ready_gym_intake:create_app_from_private_config`：**显式选择时**才读取固定`~/.config/ready-gym/.env`；要求当前用户拥有的700目录和600普通文件，拒绝符号链接与超过4096字节配置。只支持`DEEPSEEK_API_KEY=...`、可选引号/注释，不执行变量替换或shell。`.env.example`是空模板。不存在Hermes凭据搜索。

两种启动都保持`--host 127.0.0.1 --workers 1 --no-access-log`。开发内存会话不跨进程/重启、不持久，不承诺后台可靠存储。不要用`--reload`做验收或把这个未配对的创建接口开放公网。

## Android 本地联调

服务与adb必须在同一台开发主机，或先另行建立获授权的安全隧道，不能把远程服务器当成手机的localhost。

```bash
adb reverse tcp:8787 tcp:8787
```

调试包使用`http://127.0.0.1:8787`，需**仅debug、仅回环地址**的cleartext允许；无需0.0.0.0或公开端口。release继续只允许HTTPS，正式服务地址、配对/用户认证、TLS与费用配额不在本轮范围。服务没有新增CORS通配许可；原生Android无需CORS。

## 客户端契约

完整机器可读OpenAPI：[`../docs/intake-api.json`](../docs/intake-api.json)。离线生成：`.venv/bin/python scripts/export_contract.py`（无Key读取/网络）。运行时`GET /openapi.json`同一契约。

| 方法/路径 | 请求 | 返回 |
|---|---|---|
| `POST /api/intake/sessions` | `{"consent":true}` | 201 `StartResponse`，额外含`session_token` |
| `POST /api/intake/sessions/{id}/messages` | `{"message":"21岁，175厘米、70公斤，想增肌"}` | 200 `IntakeResponse` |
| `GET /api/intake/sessions/{id}` | 无正文 | 200当前已验证快照，无模型调用 |
| `POST /api/intake/sessions/{id}/confirm` | `{"confirmed":true,"expected_revision":3}` | 200当前快照`confirmed:true`，无模型调用 |
| `DELETE /api/intake/sessions/{id}` | 无正文 | 204无正文 |

除创建外全部要求`Authorization: Bearer <仅该会话的session_token>`。token随机生成256-bit，仅创建返回一次；服务仅存SHA-256摘要，恒时比较。不可跨会话使用，删除失效，不记录token。创建返回session_id是URL安全随机标识。会话token不是生产用户认证。

`IntakeResponse`始终含：`session_id, assistant_text, profile, validated_profile, missing_fields, status, provider_mode, response_source, usage, revision, confirmed, safety_route, calls_used, expires_in_seconds`。

- `profile`与`validated_profile`内容一致（兼容别名）；未回答字段均为JSON null。
- `status`只取`collecting/ready/blocked`；`ready`不等于已采用，必须独立confirm。改资料后revision递增，confirmed恢复false；旧revision确认409。文字说“确认”不代替该请求。
- `provider_mode="deepseek_official"`标明**提取提供方**；`response_source="server_policy"`明确assistant_text由服务器安全策略合成，**不是原样模型聊天文本**。模型提供`assistant_text/profile_patch` JSON提案，验证后只采用patch；故不会把AI自创处方、广告、诊断文字直接显示。只有成功真实适配调用或明确的本地安全分流返回成功建档响应，错误不假装模型已回答。
- 每次只问最先缺失的最多3项；下一请求带当前已验证档案、上次追问字段、最新用户句子，无额外历史健康聊天转发。允许一句多项/改值；null补丁不清除已知值。遇歧义应追问而不是猜。
- `usage`是**已知、已验证上游usage**累加的`prompt_tokens/completion_tokens/total_tokens`，不是账单、实时费用或全部失败计费。超时/无效usage可能收费未知。`calls_used`计实际尝试，失败计入；不自动重试避免重复收费。

### 档案schema（与现有Kotlin支持范围对齐）

| 字段 | 非null值 |
|---|---|
| age | 严格整数18..120（当前仅成人） |
| height_cm | 有限JSON number100..250 |
| weight_kg | 有限JSON number30..300 |
| gender | 可选female/male/other/undisclosed，可始终null |
| goal | muscle/fat_loss/health |
| location | home/gym |
| equipment | 1..4个互异值：bodyweight/dumbbell/barbell/machine，不从gym默认库存 |
| days_per_week | 严格整数2/3/4 |
| minutes_per_session | 严格整数20/30/45/60 |
| experience | beginner/returning/advanced；advanced收集后blocked，不改成新手 |
| current_discomfort | 用户明确否认且通过文本证据守卫时为`"none"`；否则保留1..500字自述，未答null |
| medical_restrictions | 同上，必须针对医学限制明确回答；未答null |

不支持的数字/枚举不能自动“修正”为支持值。布尔不当数字、NaN/Infinity、空数组/重复器械、未知键、过长文本、重复JSON key、模型添加计划字段均拒绝。用户HTTP请求不接受额外profile/URL/provider参数。服务不计算BMI、不指定负重、没有计划工具；以后计划只能在现有Kotlin/knownId底座校验后另行落地。

### 安全优先

- 明显胸痛/头晕/急性损伤等关键词：先本地`blocked + stop_and_seek_care`，不花模型调用且本会话急性标记不会被后续AI否认清除；文本仅提示停止/求助、不作诊断。
- 自述不适/医学限制非`none`：`blocked + professional_review`，优先于缺项。
- 进阶经验：`blocked + unsupported_experience`。
- 安全字段null必须继续问，沉默/空值绝不等同没有。“都没有”只有上次追问明确且仅涉及这两项时允许；矛盾/疼痛等文本不会被“none”吞掉。

守卫是保守关键词/否认证据检查，不是完整自然语言医学筛查；可产生误报（例如提到“没有胸痛”也会保守阻断）。不存在安全认证。部署前仍需专业审核与更严格产品分流；不要靠重开会话绕过。

### 限额/失败

默认单进程：64会话、固定TTL1800秒、不滑动续期；全球最多2个provider调用并发，同会话只允许1条在途消息，冲突409，不排队付费。每会话最多12次**包含首问**、已知usage达20000 tokens后拒绝新调用；单进程总预算120次，删除/过期不重置。budget是一轮调用前阈值，最后一次可能超出usage阈值；无效/缺usage只有尝试计数能保证。request上限16KiB（含chunked）；message1..2000字非空；上游响应32KiB、JSON内容10000字、assistant_text1200字；max_tokens1024，网络超时20秒，无重定向/自动重试。

统一错误：`{"error":{"code":"provider_not_configured","message":"..."}}`，不含伪AI回复、原始上游body、Key或健康输入。

| HTTP | code |
|---|---|
| 401 | session_unauthorized |
| 403 | consent_required / confirmation_required |
| 404 | session_not_found |
| 409 | session_busy / stale_revision / profile_not_ready |
| 410 | session_expired（经匹配token确认过期后回收；已回收再请求404） |
| 413/422 | body_too_large / invalid_request |
| 429 | session_capacity / provider_busy / session_call_limit / session_token_limit / app_call_limit |
| 502 | provider_invalid_response / provider_unavailable |
| 503 | provider_not_configured / provider_auth_failed / provider_rate_limited / provider_unavailable |
| 504 | provider_timeout |

失败不改变validated_profile、revision或confirmed；次数和已知usage仍可能增加。客户端不自动重试付费消息（超时也不自动重试），可GET查看旧快照，让用户明确决定重发。日志仅少量受限request-id/usage/错误code，无请求/响应正文。禁用httpx/httpcore DEBUG日志与uvicorn访问日志。

## 官方API依据与验收边界

- 固定endpoint `https://api.deepseek.com/chat/completions`，`model=deepseek-flash`，`thinking={"type":"disabled"}`，`response_format={"type":"json_object"}`，非stream；不用旧deepseek-chat、beta strict或工具调用。
- 官方JSON模式与空content注意事项：https://api-docs.deepseek.com/guides/json_mode
- endpoint/model/thinking参数：https://api-docs.deepseek.com/api/create-chat-completion
- thinking开关：https://api-docs.deepseek.com/guides/thinking_mode
- 工具文档（本轮不启用）：https://api-docs.deepseek.com/guides/tool_calls

`tests/test_intake.py`执行实际FastAPI与HTTP适配器，仅外部网络用httpx MockTransport，覆盖建档闭环、授权、限额、失效/删除、修正确认、安全、JSON/字段/网络失败与配置隔离。**通过它们不等于真实DeepSeek已连接。**

真实验收尚未做：用户专用Key配置并授权后，先用合成成人资料有限首问/多字段/缺项/安全否认/确认闭环，核验真实账号支持`deepseek-flash`和非thinking JSON，记录仅状态/usage/request-id；绝不把模拟回包当验收。公网/HTTPS/生产认证、跨进程存储、AI计划均未做。
