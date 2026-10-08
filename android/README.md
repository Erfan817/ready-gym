# Ready Gym Android · v0.2.0

Kotlin + Jetpack Compose；包名 `com.readygym.app`，versionCode3，min SDK26（Android8.0），target/compile SDK35。沿用既有Debug签名，可作为旧测试版的更新包；不是商店发布版。

## 可操作流程

建档 → 目标 → 地点/器械 → 确认资料 → 训练频率/时间/经验与明确安全自述 → 规则草稿 → 七日/动作详情 → 明确确认。草稿和当前生效版分开，重新生成不覆盖旧版；缺器械时检查候选，先显示替换提案再确认。身体不适反馈阻断普通确认/替换，不用换动作绕过风险。

浅色UI为暖白/白卡片/橙色CTA/绿色强调；地点Radio单选、器械Checkbox多选，原创插画、标记和描边兼顾色弱。图标同步浅色。

### 动作演示覆盖

徒手深蹲：真实离线GIF，CC BY-SA4.0，播放/暂停/继续。跪姿俯卧撑、臀桥、死虫式、双侧俯身哑铃划船、杠铃划船：暂无匹配且已核验许可的GIF，详情明确标缺失，但有本地自编说明。逐项署名与许可见 [`docs/media-attribution.md`](docs/media-attribution.md)。不从数据MIT推断第三方媒体可用。

## 重要边界

- 全部资料和计划仅当前会话内存；不保存、不上传，旋转/退出/Activity重建或进程回收可能清空。
- 无LLM/Agent/网络后端、账号、训练日志、自动进阶、广告或支付。模式明确标为规则模板，未经专业审核，不是医疗处方或安全认证。
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
# 无SDK的真实Kotlin/JUnit入口（运行全部39项core）
bash scripts/test-core.sh
```

普通开发机器将环境变量改为本机SDK/JDK路径即可。源目录末尾含空格，不自动改名。

## 已执行的验收

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

## APK实物

- 实际主APK：`app/build/outputs/apk/debug/app-debug.apk`
- 大小：9,470,795字节。
- SHA-256：`0d4aff1a7a6987934d84c1b270b87a7ec511d3fb1eff1e2cde7c1e6440899bc8`
- 签名v2有效、ZIP完整性和zipalign4通过；离线GIF字节实际包含在APK且与源文件一致；无INTERNET权限。
- 证书SHA-256：`078f0b793c898982522eb535c323c6b8b685221660d41fdc74e845939762add0`，与之前版本一致。不得分发私钥或设备测试APK。

手机可解压收到的ZIP并通过系统安装器安装主APK。USB调试及设备授权后可：

```sh
adb install -r 'app/build/outputs/apk/debug/app-debug.apk'
adb shell am start -n com.readygym.app/.MainActivity
./gradlew :app:connectedDebugAndroidTest
adb kill-server
```

最终修复日志与实物报告在 `build/verification/review-fixes/`（含分项RED/GREEN、全量日志、`final-artifact.json`）；此前日志/渲染仍在 `build/verification/`、`build/planning-ui-tdd/`、`core/build/planning-tdd/`，均忽略提交。规则API及来源见 [`core/README.md`](core/README.md)；真实AI尚未接入，凭据仅服务端并需确认供应商/费用/数据授权。
