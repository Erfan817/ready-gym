# Ready Gym Android · v0.3.0

Kotlin + Jetpack Compose；包名 `com.readygym.app`，versionCode4，min SDK26（Android8.0），target/compile SDK35。沿用既有Debug签名，可作为旧测试版的更新包；不是商店发布版。

## 可操作流程

正式入口为**单屏教练对话**，保留已验收的SunnyTheme暖白/橙/绿配色。产品欢迎说明不是模拟AI回复。明确同意向Ready Gym服务端和DeepSeek发送训练资料后，才POST开始会话并显示服务端首问；自然语言回答 → 服务端验证资料 → 可纠正摘要 → 明确确认（确认版本与GET读回验证） → **规则计划底座**。DeepSeek提取与服务端策略整理的问句区分显示；不是AI个性化计划生成。

**交付包默认没有服务地址；明确显示“服务未配置，不能连接AI”。没有DeepSeek Key、生产HTTPS或用户访问控制，真实AI验收未完成。** 不使用regex/本地模拟聊天作为接通AI的回退。详见 [`docs/coach-api.md`](docs/coach-api.md)。

可选“离线规则模式（不使用AI）”保留原流程：建档 → 目标 → 地点/器械 → 确认资料 → 训练频率/时间/经验与明确安全自述 → 规则草稿 → 七日/动作详情 → 明确确认。草稿和当前生效版分开，重新生成不覆盖旧版；缺器械时检查候选，先显示替换提案再确认。身体不适反馈阻断普通确认/替换，不用换动作绕过风险。

浅色UI为暖白/白卡片/橙色CTA/绿色强调；地点Radio单选、器械Checkbox多选，原创插画、标记和描边兼顾色弱。图标同步浅色。

### 动作演示覆盖

当前六个计划动作已接入指定仓库的180×180原始GIF与JPG缩略图；用户已声明取得使用授权，保留Gym visual署名与媒体独立许可。GIF点击播放、暂停/继续；静态预览不冒称动画。臀桥为低幅版、死虫为固定手臂单腿伸展版，跪姿俯卧撑不采用矛盾的上游文字；具体映射与验证见 [`docs/media-attribution.md`](docs/media-attribution.md)。旧Commons深蹲GIF保留资源与CC BY-SA4.0署名，不再用于当前映射。

## 重要边界

- 对话、会话token、输入和错误仅在Activity ViewModel内存；旋转/Activity配置重建保留，不落盘，结束Activity或进程回收后清空。服务端会话另有过期；可主动删除并GET读回确认后清空。本地规则资料和计划仍仅页面内存，旋转/退出可清空。
- AI路径用户同意后需要INTERNET权限访问服务端；不直接访问DeepSeek，不含模型Key。Debug仅localhost/127.0.0.1契约测试可明文，其余必须HTTPS；默认地址为空，Release地址强制空，当前不得公开部署开发API。
- 无生产账号、训练日志、自动进阶、广告或支付；AI个性化计划provider后续工程未做。规则模板未经专业审核，不是医疗处方或安全认证。
- 经验/器械/时长是约束；不据身高体重/BMI/性别推算负重。缺动作覆盖或未明确具体固定器械时提示缺口，不假定所有机器都有。
- 当前不适/医学限制需明确自述；高风险反馈停止普通计划。空字段不视为安全。

## 构建与测试

锁定Gradle8.9 / AGP8.7.3 / Kotlin2.0.21 / Compose BOM2024.12.01，JDK17。现有SDK35、Build Tools34/35已够用；没有新增许可或常驻模拟器。低内存构建使用1worker、512MiB heap、384MiB metaspace、进程内Kotlin与no-daemon。

```sh
cd '/home/ubuntu/ready gym /android'
export JAVA_HOME="$HOME/.local/share/ready-gym-tools/jdk/usr/lib/jvm/java-17-openjdk-amd64"
export ANDROID_HOME="$HOME/.local/share/ready-gym-tools/android-sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
# 可用 ./gradlew 替换已下载的Gradle入口
"$HOME/.local/share/ready-gym-tools/gradle-8.9/bin/gradle" \
  :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest \
  --no-daemon -Dorg.gradle.jvmargs='-Xmx512m -XX:MaxMetaspaceSize=384m' \
  -Pkotlin.compiler.execution.strategy=in-process
python scripts/test-artwork.py
python scripts/test-exercise-media.py
python scripts/test-repository-media.py
# 无SDK的真实Kotlin/JUnit入口（运行全部39项core）
bash scripts/test-core.sh
```

普通开发机器将环境变量改为本机SDK/JDK路径即可。源目录末尾含空格，不自动改名。

## v0.3.0 已执行的验收

**独立审查结论更新：不通过，正在修复；以下构建和hash为修复前证据，不能作为最终交付。** 必须补安全/旧摘要/删除恢复等组合回归，再全量构建和复审。最终交付同步 `Erfan817/ready-gym` 私有仓库并回读核验。

父整合后已重新执行 `--rerun-tasks` 全量测试与主APK/设备测试APK构建：**BUILD SUCCESSFUL in 2m 8s，77个任务执行**。全量JUnit XML **97项：96通过、0失败/0错误、1明确跳过**；core39通过、App57通过/1跳过。服务端10项测试、数据导入4项测试、原创资源2项、旧Commons GIF1项、新仓库GIF/JPG文件契约1项均通过。真实回环服务启动后已核验拒绝未同意403和未配置Key503，随即关闭。模型MockTransport与测试client均明确是fixture，不是DeepSeek接通证据。独立代码审查已派发，尚未给出结论。

RED/GREEN证据在 `build/verification/chat-tdd/`，最终整合日志与实物核验在 `build/verification/v0.3.0/`。Compose覆盖同意前0请求、busy禁用、失败保留输入/重试、Activity重建、离线旧流程、320dp/1.6倍字号与横屏scrollTo。父复验发现连续重试会产生第3次重复请求，以现有复现测试先RED再加busy guard并GREEN。仓库6 GIF均实际API35动画解码/start/stop，JPG缩略图与精确映射测试先运行时RED再GREEN。PNG是Robolectric离屏渲染，不是手机或真实AI输出；SunnyTheme和Planning.kt保持原规则。

当前无连接设备/模拟器，未执行connectedDebugAndroidTest；手机IME、TalkBack、OEM系统栏等仍待真机验收。设备测试APK仅编译成功，不等于执行。

## v0.2.0 历史验收（保留记录，非当前APK）

独立审查三项逻辑修复后，以 `--rerun-tasks` 实跑上述全部任务，`BUILD SUCCESSFUL in 1m 53s`（73个任务执行），core与App测试均重新执行。JUnit XML合计70项：**69通过、0失败/0错误、1明确跳过**。父复验与二次独立审查待进行。

| 范围 | 结果 |
|---|---|
| Kotlin core | 39通过（建档8、Planning31） |
| App主机测试 | 30通过、1跳过（建档6、布局2、主题4、Planning UI11、媒体元数据1、GIF decoder2通过1跳过、演示UI4） |
| 原创图标/插画资源契约 | 2通过 |
| GIF原始文件/帧/循环契约 | 1通过 |

主机实际测试覆盖生成、明确安全自述、确认、重生成不覆盖旧版、七日/休息、详情打开关闭、缺器械提案及无候选、风险分流与陈旧报告绕过。日程/替换/预算/只读快照为确定性规则测试，不代表训练专业有效性。

三项审查回归各自先运行时断言RED再GREEN：旧“其他不适”阻断不能遮蔽新危险输入，危险升级后旧无症状偏好仍不能生成；真实GIF延迟解码期间ON_STOP清除播放意图，回前台必须再点继续；连续未确认替换的“放弃草稿”撤销整轮，恢复轮次起始草稿/原生效计划与完整changes，不只退一步。core与SunnyTheme未改动。

浅色主题文字最低4.587:1、控件最低3.641:1；实际离屏图经过查看。建档小屏/大字/横屏已执行；**新增计划屏全尺寸、真机软键盘、TalkBack、OEM图标与系统返回建档未完整验收**。滚动视口截图可裁到下一行，交互可达性以执行过的scrollTo测试为准。

GIF API35 ImageDecoder/AnimatedImageDrawable及Compose点播放/暂停/继续已执行；Android26/27 Movie fallback已实现但主机缺native shadow，探针明确跳过，不伪造成功。不宣称已在旧Android设备验证动画。

无连接设备/模拟器，未执行connectedDebugAndroidTest。设备测试APK只成功编译，不等于执行设备测试。Robolectric View.draw(Canvas)图片是离屏渲染，非真机截图。

## APK实物（v0.3.0 当前整合包，独立审查待结论）

- 实际主APK：`app/build/outputs/apk/debug/app-debug.apk`
- 大小：10,062,211字节。
- SHA-256：`66279b67f3ac3525deeff2eba6e73fdefbada99ea8cbf718e63afa7a84597f28`
- 包名`com.readygym.app`，versionName`0.3.0`，versionCode4；签名、ZIP完整性、zipalign4通过；全部13个媒体asset与本地来源字节一致。
- 新增INTERNET权限仅用于用户同意后的服务端连接；默认URL为空，禁止公网明文。
- 证书SHA-256：`078f0b793c898982522eb535c323c6b8b685221660d41fdc74e845939762add0`，与之前版本一致。不得分发私钥或设备测试APK。
- 实物核验 `build/verification/v0.3.0/artifact.json`；ZIP仅含主APK，9,584,985字节。审查若要求改代码，必须重新构建和核验，不沿用旧hash。

手机可解压收到的ZIP并通过系统安装器安装主APK。USB调试及设备授权后可：

```sh
adb install -r 'app/build/outputs/apk/debug/app-debug.apk'
adb shell am start -n com.readygym.app/.MainActivity
./gradlew :app:connectedDebugAndroidTest
adb kill-server
```

最终修复日志与实物报告在 `build/verification/review-fixes/`（含分项RED/GREEN、全量日志、`final-artifact.json`）；此前日志/渲染仍在 `build/verification/`、`build/planning-ui-tdd/`、`core/build/planning-tdd/`，均忽略提交。规则API及来源见 [`core/README.md`](core/README.md)；真实AI尚未接入，凭据仅服务端并需确认供应商/费用/数据授权。
