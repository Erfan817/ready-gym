# Ready Gym

目标产品：安卓健身 APP，围绕建档、器械条件、训练计划、动作演示与反馈调整。当前交付 **v0.2.0 测试版**，不是完整商业产品。

## 当前安卓功能

- 阳光浅色 UI：暖白底、白卡片、橙色按钮、绿色点缀；原创器械插画与 adaptive launcher 图标。
- 建档：基本资料 → 目标 → 地点单选 / 器械多选 → 明确确认。
- 训练偏好：每周2/3/4天、20/30/45/60分钟、初学/恢复训练；进阶明确不支持。
- 用户明确填写当前不适与医学限制后，生成确定性的规则草稿；可查看七日安排、休息日、组次/秒数、休息、肌群与估算时长。
- 草稿与生效计划分开，确认后才采用。重新生成、器械替换先出提案，不默默覆盖旧版；无候选与风险反馈有明示。
- 动作详情有中文要点和注意事项。**真实离线GIF目前仅徒手深蹲**，支持用户发起播放、暂停和继续；其他五项明确缺素材，未用相似动作冒充。

**边界**：未接模型/Agent、无账号或服务器、无训练日志或自动进阶、未专业审核。资料与计划仅在当前会话内存，旋转/退出/Activity重建可能清空；“确认”不是持久保存或安全认证。不联网，不依据BMI或性别推算训练负重。

## 实际验证

- 独立审查三项逻辑修复后全量重新执行：core 39项通过；App主机测试31项中30通过、1个legacy Movie解码探针跳过；合计 **69通过、0失败/0错误、1跳过**。
- 原创资源2项与真实GIF文件契约1项通过。
- `:core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --rerun-tasks` 构建成功；主APK签名、ZIP完整性、zipalign、包名和版本核验通过。最终日志与实物报告见 `android/build/verification/review-fixes/`；父复验与二次独立审查待进行。
- 已执行Robolectric Compose交互与离屏渲染检查，**不是手机截图或真机验收**；真实软键盘、系统栏、TalkBack、计划屏全尺寸适配及Android26/27 GIF仍待设备验证。

构建与安装见 [`android/README.md`](android/README.md)。规则API见 [`android/core/README.md`](android/core/README.md)；媒体出处见 [`android/docs/media-attribution.md`](android/docs/media-attribution.md)。

## 后续开发

按用户要求，当前版本验收后继续真实AI接入：服务端模型调用 → 结构化提案 → 规则校验 → 用户确认。供应商、凭据、费用与最小数据授权尚待确认；不将Hermes自身凭据放进产品或APK。设计/范围见 [`docs/ui-design.md`](docs/ui-design.md) 与 [`docs/plan-system.md`](docs/plan-system.md)。

## 动作与素材来源

hasaneyldrm/exercises-dataset 固定提交JSON下载仍超时，**未导入该仓库动作或媒体**；它的数据许可与Gym visual媒体许可分离。当前规则目录是明确自编、未专业审核的6个动作元数据，不伪造上游ID。

深蹲GIF来自 Wensceslao《Squats.gif》，**CC BY-SA 4.0**；保留原字节、作者及许可链接，独立于应用代码许可。其余媒体缺口如实列出，器械静态插画是原创资源。

## 保留的 Web 交互实验

`frontend/` 是早期React肌群点选实验，不是正式客户端，也不继续作为Web/PWA扩展。人工演示数据不是上游数据；没有计划/Agent/GIF。

```sh
cd '/home/ubuntu/ready gym /frontend'
npm ci
npm test
npm run typecheck
npm run build
```

本地目录 `ready gym ` 末尾有空格，命令中保持完整引用。SDK、缓存、构建产物、密钥和个人记录不提交Git。
