# Ready Gym Android · v0.1.1

Kotlin + Jetpack Compose，本地建档纵向流程，独立于保留的 `../frontend/` React实验。

## 当前交付边界

- 四步：基本资料 → 目标 → 地点和器械 → 分组摘要确认，可返回修改、清空。
- 深炭黑与少量荧光青柠运动风；四段进度、大标题、圆角卡片、可滚动内容与独立底部CTA，IME避让与数字/小数键盘、单位后缀和输入范围。
- 地点两张Radio单选卡；器械为2列Checkbox多选插画卡：徒手、哑铃、杠铃、绳索固定器械。选中同时有描边、勾选和语义状态，不只依赖颜色。原创矢量图与青柠哑铃+勾选adaptive launcher由 `scripts/generate-artwork.py` 生成，没有外部照片或素材版权依赖。
- 使用说明默认收起；保留18+与会话内不保存的短提示。确认页明确不是训练计划。
- `core/`：无Android依赖的状态机和 `schemaVersion=1` 的TrainingProfile。年龄18–120整数，身高100–250cm、体重30–300kg有限数值，仅为输入范围而非健康判断。性别允许不透露，当前仅记录。
- **仅会话内存，不持久化**：无帐号、服务器、网络权限、遥测、个人资料日志。旋转、退出、Activity重建或进程回收可能清空；确认不表示数据库保存。每次进入新步骤重置滚动位置到顶部，未修改业务状态机或生命周期持久化策略。
- 没有AI、训练处方、BMI能力推断、动作GIF、支付、品牌推荐，不虚构计划。

## 构建与测试

锁定Gradle8.9 / AGP8.7.3 / Kotlin2.0.21 / Compose BOM2024.12.01，JDK17，compile/target SDK35，min SDK26（Android8.0）。512MiB Gradle堆、1worker、Kotlin进程内、no-daemon，不启动模拟器。

```sh
cd '/home/ubuntu/ready gym /android'
export JAVA_HOME="$HOME/.local/share/ready-gym-tools/jdk/usr/lib/jvm/java-17-openjdk-amd64"
export ANDROID_HOME="$HOME/.local/share/ready-gym-tools/android-sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
# 可用 ./gradlew 替换已下载的Gradle入口
"$HOME/.local/share/ready-gym-tools/gradle-8.9/bin/gradle" \
  :core:test --rerun :app:testDebugUnitTest --rerun \
  :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon
python scripts/test-artwork.py
# 可选重新导出原创矢量与PNG，需要Pillow，不需要网络
python scripts/generate-artwork.py
# 无SDK的业务应急入口（只验证core）
bash scripts/test-core.sh
```

既有SDK35与Build Tools34/35已足够，本轮未安装新SDK或接受新协议。Robolectric4.14.1及其Android测试运行依赖实际从Maven下载；它们仅用于主机测试，不打入主应用APK。

## 实际验证（v0.1.1）

- 最终命令 `:core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` 真实成功：`BUILD SUCCESSFUL in 43s`。业务测试结果8项，Compose/Robolectric主机测试9项（原交互6项、小屏/横屏2项、主题对比度1项），均0 failures/errors/skipped；原创资源契约另2项通过。
- 父会话按UI skill补验：320dp小屏＋1.6倍字体、640×320dp横屏可滚动到最后器械且主操作可达；主题控件边界对比度先测试失败，再提高边界色到 `#748179`，最低3.93:1（卡片背景）。未选状态和次要文字仍有明确边界，不仅依赖颜色。
- UI红→绿实际运行：地点单选Radio语义；器械独立Checkbox与插画；使用说明折叠；切页回到标题；未聚焦空表单显示范围与单位。保留 `build/verification/ready-gym-ui-*-red.log` 与green日志。确认/返回/清空为既有行为回归测试，不冒称新TDD行为。
- 离屏真实Activity/Compose在Robolectric SDK35 native graphics运行，411×891dp手机布局；主机默认320×470布局也曾运行通过。`build/verification/screenshots/` 保存实际View.draw(Canvas) PNG（含各步骤与器械滚动位置），**不是手机或模拟器截图**。PixelCopy captureToImage在本主机超时，改用真实View.draw，没有合成假UI。
- 数字输入采用Number/Decimal和Next/Done；表单标签与单位范围可访问，测试真实输入与Done。**真实手机软键盘、系统栏、字体缩放、旋转、TalkBack及OEM launcher仍待设备验收**。
- 小屏内容正常位于可滚动视口而非固定覆盖层；测试已滚到最下固定器械并验证可见，底部CTA独立可点击。部分截图会截到下一行，表示滚动位置而非全部内容同时显示。
- 文本对比度按主题sRGB计算：次要文字/背景10.27:1，次要文字/卡片8.92:1，正文/卡片14.67:1，主按钮15.40:1；这不代表完成全部无障碍认证。
- `adb devices -l`为空，未执行connectedDebugAndroidTest，未启动模拟器。设备测试APK编译成功，**编译不等于设备执行**。临时adb已关闭。

## APK实物核验与安装

- 路径：`/home/ubuntu/ready gym /android/app/build/outputs/apk/debug/app-debug.apk`
- 实际主APK大小：**8,844,247字节**。
- SHA-256：`d1a9f08db9ec9145849f8916e429a96240441a1b8e7a00c319fdf447fabc5cd7`
- 包名：`com.readygym.app`；versionName **0.1.1**，versionCode **2**；显示名Ready Gym；min26/target35。
- apksigner v2验证通过、ZIP完整性通过、zipalign4通过、无INTERNET权限。
- 沿用既有 `~/.android/debug.keystore`；证书SHA-256仍为 `078f0b793c898982522eb535c323c6b8b685221660d41fdc74e845939762add0`，与v0.1.0一致，可覆盖原测试包。为Debug签名测试APK，不是商店发布版；不要发送私钥。
- 手机可直接用系统安装器覆盖安装；USB调试设备授权后：

```sh
adb install -r 'app/build/outputs/apk/debug/app-debug.apk'
adb shell am start -n com.readygym.app/.MainActivity
./gradlew :app:connectedDebugAndroidTest
adb kill-server
```

构建日志：`build/verification/build-v0.1.1.log`；最终实物核验：`build/verification/final-verification-v0.1.1.json`（通用核验摘要亦已同步更新）；图标/器械PNG：`build/verification/artwork/`。这些均为本地忽略产物；文档中的主APK大小、哈希和测试数量对应最终父会话验收重建结果。未改frontend。
