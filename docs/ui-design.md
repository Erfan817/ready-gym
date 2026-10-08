# Ready Gym 安卓 UI 设计基线

## 依据与适用范围

使用 UI UX Pro Max 的设计检索与 Jetpack Compose 指南作为辅助，而非自动套模板。来源：https://github.com/nextlevelbuilder/ui-ux-pro-max-skill ，已核验固定提交 `1a2c459b35f26116fd165b0a0f30597f252749ff`、MIT 许可、脚本仅本地检索等行为，并安装到当前 Hermes profile。

实际检索：
- 初版 `fitness tracker --domain product`：匹配 Fitness/Gym App，建议大胆分块、深色背景；深色只是当时建议，已被用户明确拒绝，不再作为产品要求。
- 初版 `fitness app --design-system`：匹配运动字体与高对比分块；不照搬营销落地页结构。本轮橙绿浅色来自用户选择，并经过实际对比度测试，而非自动套默认配色。
- `selection semantics touch --stack jetpack-compose`：交互选择需明确 semantics。
- `theming typography --stack jetpack-compose`：集中主题与文字样式。
- `touch target safe area --domain web`：此域包含原生界面指南，Android 使用48dp触控区、至少8dp间距与系统栏避让。
- `error summary validation --domain ux`：逐字段错误与可发现错误反馈；其中Web aria示例只提炼语义，安卓使用Compose等效能力。
- 浅色重配 `text color contrast --domain ux`：普通文字4.5:1、选中不能只靠颜色。
- 浅色重配 `color light theme --stack jetpack-compose`：集中Material3语义tokens，不升级依赖、不引入动态配色。

Skill部分输出偏营销网页（Hero/社交证明/重复CTA），不适合本APP建档，不照搬。资料库中的版本标注也不作为升级现有Compose的依据。

## Ready Gym 自身设计选择

- 视觉：固定阳光浅色方向，暖白/奶油底、白卡片、活力橙主CTA、清新绿辅助强调、墨绿深灰中文文字。保留已验收v0.1.1卡片布局、字号、间距、四步流程与底部CTA，不借重配扩大功能。
- 中文：优先Android本地中文字体，统一Material typography；运动西文字体建议不直接套到中文，不下载字体作为本轮前置。
- 信息层级：品牌、当前进度、大标题、分组内容、一项主操作；每个屏幕只有一个主要CTA。
- 资料：已实现持续可见字段名、单位后缀与输入范围；目前校验失败仍在底部汇总提示。逐字段错误和首次无效字段的焦点定位属于后续改进目标，不视为本版已实现能力。
- 目标：独立大卡片，目标标题与简短说明。
- 地点：家/健身房，单选；语义为RadioButton，换选后旧地点必须取消选中。
- 器械：徒手/哑铃/杠铃/固定器械，两列图片卡片、多选；原创且能辨识的器械图，不用emoji冒充器械图片。
- 选择反馈：浅橙选中底＋橙色2dp描边＋绿底白勾；地点Radio与器械Checkbox读屏状态不变，色弱仍能辨识，不能只靠橙绿。
- 图标：原创橙色哑铃结合绿色勾选的品牌符号，暖白adaptive背景，与应用内品牌统一。器械保留原创几何形状，线条与填充重配为深灰绿，适配白色/浅橙卡片。
- 说明：重要18+、会话资料不保存保持可发现；完整说明可展开，不在每屏重复大段文字。

## 浅色语义色与实际验证

主题集中于 `android/app/src/main/kotlin/com/readygym/app/SunnyTheme.kt`；不随系统夜间模式自动变黑，也不跟随动态壁纸配色。XML启动窗口同步暖白，系统栏使用浅色模式下的深色图标。

| Token | 颜色 | 用途 |
| --- | --- | --- |
| Background | `#FFF9F1` | 暖白屏幕与底部操作区 |
| Surface | `#FFFFFF` | 白卡片 |
| SelectedSurface | `#FFF0DF` | 浅橙选中底 |
| Text | `#24362D` | 墨绿正文/标题 |
| MutedText | `#56645B` | 辅助文字、说明、单位 |
| Outline | `#758176` | 未选控件/输入描边 |
| Action / OnAction | `#BE4708` / `#FFFFFF` | 橙色主按钮与白字、选中描边 |
| Accent / AccentSurface | `#28734C` / `#E8F4E9` | 进度、编号、勾选、已确认状态 |
| Error | `#B3261E` | 校验反馈 |

普通文字上使用经过验证的深橙；launcher装饰哑铃使用更明亮的 `#F2781F`，不能替代普通文字色。

本次仅浅色重配的真实主机验证（不是最终计划功能/版本发布验收）：
- `ThemeAccessibilityTest` 先加浅色/对比度测试，实际因仍为炭黑底失败，再实现绿色通过；图标/器械测试也实际因旧黑图标失败后再重配通过。
- `:app:testDebugUnitTest` 已执行12项：既有6项交互、2项小屏/横屏、4项主题/资源测试；0 failures/errors/skipped。未修改既有交互测试或业务core。
- 16组文字配对最低4.59:1（橙色标记/浅橙选中底）；主按钮白字5.13:1，辅助文字最低5.57:1；9组控件配对最低3.64:1。器械全部可见线条/填充在浅橙卡片上也至少3:1。
- 系统夜间配置下实际运行主题测试，仍保持浅色且正文可读。没有新增深色主题；不能把此项写成另一个深色主题验收。
- Robolectric SDK35 native graphics实际Activity `decorView.draw(Canvas)` 导出8张PNG，覆盖各步骤、器械不同滚动位置、320dp/1.6倍字体与640×320dp横屏；已检查实际图像，未发现旧黑色大背景、破损控件或配色回归。视口边缘的下一行裁切为可滚动内容，不冒称所有内容同时可见。
- 产物在 `android/build/verification/screenshots/`；总览 `theme-light-offscreen-contact-sheet.png` 与原创图标/器械预览 `theme-light-original-assets.png` 在 `android/build/verification/`。这些是主机离屏渲染/素材预览，不是手机截图。
- 仍需真实手机核验软键盘、TalkBack、OEM launcher与系统栏；此子任务不改版本号、不做最终APK交付，父流程统一集成计划与构建。

## 验收标准

- 普通文字和次要文字对比度至少4.5:1；有语义的图标/控件边界对比度至少3:1。实测，不凭视觉估计。
- Android触控区至少48dp，屏幕边缘/状态栏/手势栏/键盘不遮挡关键操作。
- 小屏、横屏和放大字体可滚动且核心标签完整；不要把省略号当适配完成。
- 地点单选、器械多选、返回不丢当前输入、修改后摘要一致，有执行过的交互测试支撑。
- 资源授权和来源真实，清晰区分原创器械插画和真实动作GIF；没有GIF就不声称已接入。
- 实际渲染检查与设备测试状态分开说明；仅编译通过不能证明UI好看或真机可用。
- 使用既有签名升级版本号，交付新APK，不发旧产物。

本文件记录设计依据，不代表所有验收项已完成；完成状态以构建、测试、图像检查和设备验证的真实结果为准。
