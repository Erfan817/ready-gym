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
export ANDROID_HOME="$HOME/.local/share/ready-gym-tools/android-sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
# 新环境安装时仅处理本项目稳定包的许可提示；阅读并接受后才能下载。
# 本次用户明确授权接受 android-sdk-license，已在真实 sdkmanager 提示中回复 y。
# 不使用全量 --licenses、yes 管道、手写哈希或接受无关协议。
# AGP 8.7.3 实际默认要求 build-tools 34.0.0；35.0.0 已安装但不替代该要求。
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" 'platforms;android-35' 'build-tools;34.0.0' 'build-tools;35.0.0' 'platform-tools'
./gradlew :core:test :app:assembleDebug
# 实际产物：app/build/outputs/apk/debug/app-debug.apk
# 可选：仅编译设备测试 APK，不代表执行设备测试
./gradlew :app:assembleDebugAndroidTest
```

本机 wrapper 下载慢时，以上 `./gradlew` 可替换为已解压的 `$HOME/.local/share/ready-gym-tools/gradle-8.9/bin/gradle`。本次使用该真实 Gradle 8.9 构建；配置为512MiB堆、1个worker、Kotlin进程内编译，不启动模拟器。

### 安装与启动（需要自己的 Android 8.0 / API26 及以上设备）

手机开启开发者选项与USB调试，USB连接后在手机上确认调试授权；`adb devices` 必须显示 `device`，不是空列表或 `unauthorized`。在上面的环境和目录执行：

```sh
adb devices -l
adb install -r 'app/build/outputs/apk/debug/app-debug.apk'
adb shell am start -n com.readygym.app/.MainActivity
# 有设备后再执行真正的Compose instrumentation测试
./gradlew :app:connectedDebugAndroidTest
# 不用时关闭临时ADB服务
adb kill-server
```

也可把 `app-debug.apk` 传到手机，通过系统安装器安装，按需允许所用文件管理器/浏览器安装此文件，再打开 **Ready Gym**。本包为Android Debug证书签名的测试包，不是商店发布版；不要把debug私钥作为附件。建档流程为基本资料 → 目标 → 地点和器械 → 确认；旋转/退出/Activity重建/进程回收会丢失资料，请勿依赖它保存信息。

## 可独立执行的业务测试

不需要Android SDK：

```sh
# Gradle正规的JUnit任务；-b 为Gradle8支持但已弃用的独立构建入口
./gradlew -p core -b build-standalone.gradle.kts test --no-daemon -Dorg.gradle.jvmargs=-Xmx512m -Dorg.gradle.workers.max=1 -Dkotlin.compiler.execution.strategy=in-process
# 低内存应急入口：使用已下载Gradle内置Kotlin1.9.23编译器及JUnit4
bash scripts/test-core.sh
```

应急脚本依赖 `$HOME/.local/share/ready-gym-tools/gradle-8.9/lib`、`downloads/junit.jar` 和 `downloads/hamcrest.jar`，可用 `READY_GYM_TOOLS` 更改工具根目录。该入口只验证业务Kotlin，不验证Compose与APK。

## 实际验证与交付状态

- 业务行为逐轮执行预期失败 → 最小实现 → 回归：成年人资料、无效数值、目标必选、地点/器械必选、返回编辑、独立资料快照、确认不循环。边界值另加回归测试；本轮没有发现需要修改生产源码的问题，未扩展功能。
- 本轮真实执行 `:core:test :app:assembleDebug`：`BUILD SUCCESSFUL in 3m 25s`，39个任务执行。Kotlin 2.0.21及Compose编译器已成功编译应用。
- 再次执行 `:core:test --rerun :app:assembleDebug :app:assembleDebugAndroidTest`：`BUILD SUCCESSFUL in 1m 5s`，66个任务中28个执行、38个缓存命中。XML核验：**8 tests，0 failures，0 errors，0 skipped**，报告在 `core/build/test-results/test/`。
- 实际主APK：`/home/ubuntu/ready gym /android/app/build/outputs/apk/debug/app-debug.apk`，**8,723,596字节（8.32MiB）**。SHA-256：`42fbe8d29ebc9828eef8b6d8726ddb1bf69770692b7b87a9a322341c4eb968c5`。
- APK实物经 `apksigner verify --verbose --print-certs` 验证通过，APK Signature Scheme v2，单个Android Debug RSA2048签名；ZIP完整性及 `zipalign -c 4` 通过。包名 `com.readygym.app`，版本 `0.1.0`/code1，min SDK26，compile/target SDK35，启动Activity `com.readygym.app.MainActivity`；没有INTERNET权限。
- Compose测试源码及测试APK已编译成功：`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`。**编译不等于执行设备测试**；测试文件虽先于UI编写，但未能运行RED/GREEN，不能称为已验证UI TDD。
- `adb devices -l` 实际为空；没有可连接真机，也未安装/启动模拟器。**未真机或模拟器安装运行，未执行connectedDebugAndroidTest**；IME、小屏、字体缩放、旋转重建、系统返回和完整建档交互仍需设备验收。
- 初次构建发现AGP实际要求Build Tools34.0.0，补装后成功。仅在本次用户授权范围内于真实sdkmanager回复y接受 `android-sdk-license`；SDK实际安装platform35 revision2、Build Tools35.0.0及34.0.0、platform-tools37.0.1，没有全量接受无关协议。
- 环境修改限于用户工具目录SDK、Gradle依赖/项目构建缓存、自动生成的 `~/.android/debug.keystore`；项目Gradle堆从640MiB改为512MiB（1worker、Kotlin进程内）。JAVA_HOME/ANDROID_HOME/PATH仅供构建命令使用，未修改Hermes或全局shell环境；临时ADB服务已关闭，无Gradle/Kotlin常驻进程。未开代理、VNC或模拟器，未改frontend、未接模型或持久化。
- 可审计构建日志：`build/verification/build.log`；APK核验摘要：`build/verification/apk-verification.json`（均为本地忽略的构建产物）。
