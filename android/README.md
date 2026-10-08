# Ready Gym Android · 本地建档纵向流程

Kotlin + Jetpack Compose，独立于保留的 `../frontend/` React实验。

## 当前交付边界

- `core/`：无Android依赖的建档状态机与带 `schemaVersion=1` 的 `TrainingProfile`。年龄18–120整数，身高100–250cm、体重30–300kg有限数值；这些仅是本版输入范围，不代表健康范围或能力判断。
- `app/`：基本资料 → 健身目标（增肌/减脂/保持健康） → 地点（家/健身房）和器械多选 → 摘要确认，可返回修改、清空。性别允许不透露，当前仅作记录。
- 本版**不持久化**：仅Compose `remember` 会话内存，无帐号、无服务器、无网络权限、无遥测、不写个人数据到日志。清空按钮重置；退出/Activity重建/进程回收会丢失资料（旋转也会重置）。确认仅确认本次会话，不表示保存到数据库。
- 没有AI、训练处方、BMI能力推断、动作GIF、支付、品牌推荐。业务模型为下一阶段接入保留边界，不空建后端服务。
- 品牌推荐属于后续商业化：教练需求判断独立于商业排名；赞助/佣金明示，补剂和护具不是人人必需。

## 环境与构建

锁定 Gradle 8.9 / AGP 8.7.3 / Kotlin 2.0.21 / Compose BOM 2024.12.01，JDK17，compile/target SDK35，min SDK26。版本为本次选择，非“最新版”宣称。

```sh
cd '/home/ubuntu/ready gym /android'
export JAVA_HOME="$HOME/.local/share/ready-gym-tools/jdk/usr/lib/jvm/java-17-openjdk-amd64"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/.local/share/ready-gym-tools/android-sdk"
# 必须由用户本人阅读并实际接受 SDK 许可；不使用 yes 管道、手写许可哈希或自动代接受。
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
./gradlew :core:test :app:assembleDebug
# 构建成功后的预期输出（当前尚不存在）：app/build/outputs/apk/debug/app-debug.apk
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.readygym.app/.MainActivity
./gradlew :app:connectedDebugAndroidTest
```

本机Gradle发行版若wrapper下载慢，可使用已解压的 `$HOME/.local/share/ready-gym-tools/gradle-8.9/bin/gradle` 替代 `./gradlew`。SDK下载未完成前不要将APK预期路径当成实际产物。

## 可独立执行的业务测试

不需要Android SDK：

```sh
# Gradle正规的JUnit任务；-b 为Gradle8支持但已弃用的独立构建入口
./gradlew -p core -b build-standalone.gradle.kts test --no-daemon -Dorg.gradle.jvmargs=-Xmx512m -Dorg.gradle.workers.max=1 -Dkotlin.compiler.execution.strategy=in-process
# 低内存应急入口：使用已下载Gradle内置Kotlin1.9.23编译器及JUnit4
bash scripts/test-core.sh
```

应急脚本依赖 `$HOME/.local/share/ready-gym-tools/gradle-8.9/lib`、`downloads/junit.jar` 和 `downloads/hamcrest.jar`，可用 `READY_GYM_TOOLS` 更改工具根目录。该入口只验证业务Kotlin，不验证Compose与APK。

## 实际验证与阻塞

- 业务行为逐轮执行预期失败 → 最小实现 → 回归：成年人资料、无效数值、目标必选、地点/器械必选、返回编辑、独立资料快照、确认不循环。边界值另加回归测试。
- `bash scripts/test-core.sh`：JUnit `OK (8 tests)`；独立Gradle test任务也真实运行成功。XML报告在 `core/build/test-results/test/`。
- Compose测试文件 `app/src/androidTest/kotlin/com/readygym/app/OnboardingUiTest.kt` 先于UI编写，但**未能运行RED/GREEN**；不能称为已验证UI TDD或设备测试。
- `:app:assembleDebug` 实际执行失败：`Failed to find target with hash string 'android-35'`。SDK manager已显示实际许可并因未接受跳过 platform35/build-tools/platform-tools。等待用户阅读接受；没有代接受或绕过许可，当前无APK。
- 设备发现仅见Linux USB根集线器；adb因平台工具许可未接受尚不可用，未真机/模拟器验证，也未安装模拟器。
- UI源代码尚未由Android编译器验证；需完成SDK安装后执行上面构建和设备测试。IME、小屏、字体缩放、旋转重建与系统返回仍需设备验收。
- 工具只放用户目录，未改Hermes环境、未启动代理/常驻服务、未commit/push、未扩展或删除frontend。
