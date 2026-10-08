# Ready Gym 安卓 UI 设计基线

## 依据与适用范围

使用 UI UX Pro Max 的设计检索与 Jetpack Compose 指南作为辅助，而非自动套模板。来源：https://github.com/nextlevelbuilder/ui-ux-pro-max-skill ，已核验固定提交 `1a2c459b35f26116fd165b0a0f30597f252749ff`、MIT 许可、脚本仅本地检索等行为，并安装到当前 Hermes profile。

实际检索：
- `fitness tracker --domain product`：匹配 Fitness/Gym App，建议大胆分块、深色背景。
- `fitness app --design-system`：匹配运动字体与高对比分块；其默认橙色配色和营销落地页结构不自动采用。
- `selection semantics touch --stack jetpack-compose`：交互选择需明确 semantics。
- `theming typography --stack jetpack-compose`：集中主题与文字样式。
- `touch target safe area --domain web`：此域包含原生界面指南，Android 使用48dp触控区、至少8dp间距与系统栏避让。
- `error summary validation --domain ux`：逐字段错误与可发现错误反馈；其中Web aria示例只提炼语义，安卓使用Compose等效能力。

Skill部分输出偏营销网页（Hero/社交证明/重复CTA），不适合本APP建档，不照搬。资料库中的版本标注也不作为升级现有Compose的依据。

## Ready Gym 自身设计选择

- 视觉：深炭黑底、灰绿层级卡片、青柠强调色；不用默认紫色chips堆叠，也不同时混入橙色/蓝色。
- 中文：优先Android本地中文字体，统一Material typography；运动西文字体建议不直接套到中文，不下载字体作为本轮前置。
- 信息层级：品牌、当前进度、大标题、分组内容、一项主操作；每个屏幕只有一个主要CTA。
- 资料：已实现持续可见字段名、单位后缀与输入范围；目前校验失败仍在底部汇总提示。逐字段错误和首次无效字段的焦点定位属于后续改进目标，不视为本版已实现能力。
- 目标：独立大卡片，目标标题与简短说明。
- 地点：家/健身房，单选；语义为RadioButton，换选后旧地点必须取消选中。
- 器械：徒手/哑铃/杠铃/固定器械，两列图片卡片、多选；原创且能辨识的器械图，不用emoji冒充器械图片。
- 选择反馈：勾选或radio标记＋边框＋文字/读屏状态，不能只靠青柠色。
- 图标：原创哑铃结合勾选的品牌符号，实际Android launcher/adaptive icon与应用内品牌统一。
- 说明：重要18+、会话资料不保存保持可发现；完整说明可展开，不在每屏重复大段文字。

## 验收标准

- 普通文字和次要文字对比度至少4.5:1；有语义的图标/控件边界对比度至少3:1。实测，不凭视觉估计。
- Android触控区至少48dp，屏幕边缘/状态栏/手势栏/键盘不遮挡关键操作。
- 小屏、横屏和放大字体可滚动且核心标签完整；不要把省略号当适配完成。
- 地点单选、器械多选、返回不丢当前输入、修改后摘要一致，有执行过的交互测试支撑。
- 资源授权和来源真实，清晰区分原创器械插画和真实动作GIF；没有GIF就不声称已接入。
- 实际渲染检查与设备测试状态分开说明；仅编译通过不能证明UI好看或真机可用。
- 使用既有签名升级版本号，交付新APK，不发旧产物。

本文件记录设计依据，不代表所有验收项已完成；完成状态以构建、测试、图像检查和设备验证的真实结果为准。
