# Core 个性化规则计划 API v1

这是纯 Kotlin、确定性的 **personalized rule template**，未调用 LLM，没有数据库、网络或写入副作用。计划及动作信息未经训练专业人士审核，不是医疗处方或安全认证。不得把 `CONFIRMED` 展示为“已通过安全认证”。

## Android 接入

包名 `com.readygym.core`，保留现有 `TrainingProfile` 和 `Onboarding.kt` 不变。入口：

```kotlin
val engine = PlanningEngine()
val feedback = SafetyFeedback(screenCompleted = true) // 只能在用户明确完成安全自述后设置
val preferences = PlanningPreferences(
    daysPerWeek = 3,
    minutesPerSession = 30,
    experience = TrainingExperience.BEGINNER,
    safety = feedback,
    constraints = TrainingConstraints(),
)
val result = engine.generate(profile, preferences, draftId = "ui-generated-id")
// when (result): Generated(draft) / Invalid(errors) / SafetyBlocked(decision)
```

- `daysPerWeek`: 2、3、4；`minutesPerSession`: 20、30、45、60。
- `TrainingExperience`: `BEGINNER`、`RETURNING`；`ADVANCED` 明确返回 `UNSUPPORTED_EXPERIENCE`。
- 非医学偏好用 `TrainingConstraints(excludedPatterns, maxDifficulty)`。医学训练限制必须放 `SafetyFeedback.medicalRestrictions`，不能通过普通动作过滤绕过安全分流。
- `PlanDraft.schemaVersion=1`；`id` 由调用方给出，保持原值；`revision=1` 起。
- `week`: 周一=1至周日=7，七个 `PlannedDay`。2天为1/4；3天为1/3/5；4天为1/2/4/5，下肢/上肢交替。休息日 `session=null`，都有规则解释和 sourceIds。
- `TrainingSession.exercises`: `PlannedExercise.slotId` 标识该日槽位；`exercise.stableId` 标识动作，不要以中文名字或列表下标映射媒体。
- 每动作包含名称、主肌群、必要的外部器械集合、动作模式、难度提示、来源、审核状态；参数包含组数、`reps(min,max)` 或 `seconds`（二选一）、休息秒数。`loadKg=null`，不依据 BMI、性别或体重计算重量。
- 热身180秒、放松120秒、每动作转换30秒、每次重复4秒参与估算。`estimatedSeconds <= budgetSeconds`；`durationNotice` 必须同时展示。实际学习动作、器械排队和休息可能更长，不保证准时完成；勿缩短休息赶预算。
- 增肌在预算允许且非 returning 时至多3组；健康/减脂基础2组；恢复训练从1组、8–10次开始；减脂不自动开高强度或极端饮食建议。45/60分钟是预算上限，不强行填满。
- `sources`, `notices`, `reviewStatus`, `mode` 必须保留。动作库覆盖有限（尤其仅徒手时缺拉类动作），不代表完整全身处方或满足全部健康指南。

### 确认

```kotlin
val confirmed = engine.confirm(
    draft, expectedRevision = draft.revision,
    userAccepted = true, safety = freshSelfReport,
    confirmedAtEpochMillis = nowFromUi,
)
// Confirmed(plan) / Rejected(errors) / SafetyBlocked(decision)
```

明确同意、版本匹配且新安全自述允许时才返回新的确认快照；原 draft 不改。重复确认、未同意、过期版本、负数时间、明显不完整草稿返回错误。输出集合做防御复制和只读包装。

本模块不保存计划。UI需自己保留当前版本、处理分支和保存；`expectedRevision` 只核对传入对象，不能替代数据库乐观锁，也不能识别另一个同版本但不同内容的分支。不要直接修改/拼装草稿再确认；普通流程应使用引擎返回对象。

### 缺器械候选与修订

```kotlin
val candidates = engine.replacementCandidates(
    plan, sessionId, slotId, currentlyAvailableEquipment, freshSelfReport,
)
// Available(candidates) / NoCandidate(reason) / Rejected(error) / SafetyBlocked(decision)
val proposal = engine.proposeReplacement(
    plan, expectedRevision = plan.revision, sessionId, slotId,
    replacementExerciseId, currentlyAvailableEquipment, freshSelfReport,
)
// Proposed(draft) / NoCandidate(reason) / Rejected(error) / SafetyBlocked(decision)
```

候选必须同动作模式、同主肌群、器械可用、难度不升级，并符合非医学约束。无候选明确返回，不编造动作。只替换指定日的指定槽位；保留组次和休息。修订增加 revision、记录 basedOnRevision/changes，重新进入 DRAFT 并清除 confirmation；旧确认版保留不变。UI展示原/新动作与理由，重新调用 `confirm` 才能采用。临时缺器械不改档案库存；长期变更应重建资料再生成。

## 安全反馈分流

`evaluateSafety(feedback)` 可用于动作中或练后即时显示提醒，生成/确认/替换同样强制调用：

| route | UI行为 |
|---|---|
| `STOP_AND_SEEK_CARE` | 胸痛、头晕、急性关节痛/伤、持续关节痛：停止，不用换动作继续；提示及时专业医疗评估，胸痛或严重/持续症状联系当地急救 |
| `PROFESSIONAL_REVIEW` | 非空医学限制或未分类当前不适：暂停普通模板，专业评估 |
| `SCREEN_REQUIRED` | 尚未完成安全自述，不把空字段当作无风险 |
| `PAUSE_AND_REASSESS` | 用户自述普通疲劳/轻微肌肉酸痛：暂停恢复并重新评估；不诊断为正常，不自动加量 |
| `TEMPLATE_ALLOWED` | 仅允许普通未审核模板，绝非安全认证 |

结构化 `signals` 为主要入口；自由文本做有限的中英文危险词保守拦截，不进行 NLP/诊断，可能误报（例如否定句），其余非空描述同样阻断普通计划。UI应使用明确“没有这些情况”的单选/确认，不往 `medicalRestrictions` 写入字符串“无”，而是在用户明确选择没有时保持空字符串并设 `screenCompleted=true`。

## 动作及数据来源

完整上游 JSON 本轮仍未取得：固定提交 `7455efae41b330c265e7cd4b78dfa848e7ce5ebd` 的 Contents API 成功返回 `data/exercises.json` 元信息，blob SHA 为 `3cbb77b678c40d7342897d38e4c61e9502f829a7`，大小17,391,530字节。git blobs两次75秒超时，raw-accept备用请求40秒超时后停止。LICENSE/NOTICE 可读，但 **未导入任何上游动作记录，以下ID不是上游ID**。没有伪造上游内容，也没有下载/复制/热链其图片或GIF。MIT数据许可不涵盖 Gym visual 媒体。

当前 Kotlin catalog 是明确自编的小型元数据，来源 `ready-gym-authored-v1`；仅用于首版规则草稿和UI联调，未专业审核。媒体组件独立按以下ID映射，不在core捆绑GIF链接：

| stableId | 名称 | 必要外部器械 | 模式/主肌群 |
|---|---|---|---|
| `rg-v1-bodyweight-squat` | 徒手深蹲 | 无 | SQUAT/QUADRICEPS |
| `rg-v1-knee-push-up` | 跪姿俯卧撑 | 无 | HORIZONTAL_PUSH/CHEST |
| `rg-v1-glute-bridge` | 徒手臀桥 | 无 | HIP_EXTENSION/GLUTES |
| `rg-v1-dead-bug` | 死虫式核心练习 | 无 | ANTI_EXTENSION/CORE |
| `rg-v1-dumbbell-row` | 双侧俯身哑铃划船 | DUMBBELL | HORIZONTAL_PULL/BACK |
| `rg-v1-barbell-row` | 俯身杠铃划船 | BARBELL | HORIZONTAL_PULL/BACK |

`exercise.equipment=emptySet()` 意味着无需外部器械，不是用户只选徒手。固定器械只知道类别，不能据此假定有史密斯、腿推等具体机器；现版只补徒手并提示覆盖缺口。不暗中要求训练凳、椅子或弹力带。

检索核实的一般规则出处：
- CDC [Adult Activity: An Overview](https://www.cdc.gov/physical-activity-basics/guidelines/adults.html)：成年人至少2天肌力训练、150分钟中等强度有氧。
- CDC [What Counts as Physical Activity for Adults](https://www.cdc.gov/physical-activity-basics/adding-adults/what-counts.html)：8–12次、至少1组，2–3组可能增加收益。

本地模板的休息、余力、动作选择、排期、估算规则均自编；不归因给CDC、不伪称专业审核。此短时抗阻模板不等于满足整体指南。

## 真实测试

```sh
cd '/home/ubuntu/ready gym /android/core'
python3 scripts/test-core.py final
# SDK-free Gradle独立模块（不修改app/根脚本）
JAVA_HOME="$HOME/.local/share/ready-gym-tools/jdk/usr/lib/jvm/java-17-openjdk-amd64" \
  "$HOME/.local/share/ready-gym-tools/gradle-8.9/bin/gradle" \
  -p . -b build-standalone.gradle.kts test --offline --no-daemon --max-workers=1 \
  -Dorg.gradle.jvmargs='-Xmx384m -XX:MaxMetaspaceSize=256m' \
  -Pkotlin.compiler.execution.strategy=in-process --rerun-tasks
```

逐行为红/绿实跑日志在 `build/planning-tdd/`（build目录忽略提交）；已有8项 Onboarding测试未改，新增31项 Planning测试，最终SDK-free Gradle新鲜执行 **39项全部通过，0失败/0错误/0跳过**。程序核对了31个周期均有真实JUnit运行失败与后续全套通过日志，结果在 `build/planning-verification.json`。测试涵盖排期、器械/难度/模式限制、预算、安全优先、确认、替换无候选和immutable版本。没有UI或真机测试；父流程负责安卓UI与独立媒体资源组件。

剩余：专业训练规则/动作审核、真实上游元数据导入、媒体来源与许可/表现核验、UI接入、持久保存/并发版本控制、训练日志和自动进阶。暂不支持进阶、康复或医学特殊场景。
