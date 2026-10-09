# Exercise media provenance · v0.3.0

## 授权与实际使用

用户明确告知“图片、GIF图我已经得到授权了”，本项目据此按其Ready Gym使用授权获取指定仓库素材，不再把先前未确认授权当开发阻塞。未收到或审阅具体授权文件，不虚构编号、期限或可转授范围。

来源：https://github.com/hasaneyldrm/exercises-dataset ，固定提交 `7455efae41b330c265e7cd4b78dfa848e7ce5ebd`。非媒体数据MIT；图片/GIF仍保留 **© Gym visual — https://gymvisual.com/** 及独立媒体来源。不能因为GitHub公开或MIT元数据而把媒体改标MIT。

当前六个计划动作全部有对应180×180原始GIF和JPG，离线打包、原始字节未改，未hotlink；每个GIF有12帧、10个不同画面。文件级Git blob与上游记录匹配，来源证据见 `../../data/current-plan-media-mapping.json` 与 `../../data/source-media/current-plan-verification.json`；APK中与本地来源字节一致的验证由 `scripts/test-repository-media.py` 执行。

| stable ID | 源ID /文件stem | 实际画面与范围 |
|---|---|---|
| `rg-v1-bodyweight-squat` | `3119` / `3119-75Bgtjy` | 上游名potty squat，无器械双脚蹲起、手臂前伸；不规定统一蹲深 |
| `rg-v1-knee-push-up` | `3211` / `3211-ZOuKWir` | 膝盖支撑俯卧撑；上游中英文指导有脚掌支撑错误，**未使用该段文字** |
| `rg-v1-glute-bridge` | `3013` / `3013-u0cNiij` | 地面低幅臀桥，**不冒称大幅、负重或长凳版本** |
| `rg-v1-dead-bug` | `0276` / `0276-iny3m5y` | 双臂固定朝上、单腿伸展循环；**不是对侧手脚交替版本，也未展示完整左右换侧**，详情明确标变式 |
| `rg-v1-dumbbell-row` | `0293` / `0293-BJ0Hz5L` | 双侧俯身哑铃划船，无凳；不借用凳支撑单臂划船 |
| `rg-v1-barbell-row` | `0027` / `0027-eZyBC3j` | 双侧俯身杠铃划船，保留器械要求，不据图推算训练重量 |

这些画面用于动作识别，不是专业教练审核、临床验证或安全认证。中文要点为本地谨慎整理，不把上游误译和矛盾说明当可信训练处方。源数据的保守不匹配标志保留；运行时取舍与以上变式说明独立记录。

## 播放与静态预览

`ExerciseMediaCatalog.find(stableId)`精确映射bundled文件。详情默认显示对应JPG静态预览（无自动动画），**静态图不代表GIF正在播放**。点击才加载GIF；可暂停/继续，离开或STOP清除播放意图，延迟解码也不能在回前台自动恢复。未知ID、缺素材和解码失败不得借用别的动作或伪装成功。

API28+使用`ImageDecoder`/`AnimatedImageDrawable`；API26/27使用time-driven `Movie`分支。API35主机测试实际执行六个新GIF的原生动画解码与start/stop、Compose点击和暂停。**Android26/27真实设备未验收**；host Movie探针明确跳过，不用mock的运动冒充验证。Robolectric离屏渲染不是手机截图。

## 保留的旧版Commons资源

原 `app/src/main/assets/exercise-demos/squats-wensceslao.gif` 仍打包，当前详情改用上表仓库媒体，旧素材不在当前映射。保留其独立署名义务：

- 《Squats.gif》，作者 **Wensceslao**；https://commons.wikimedia.org/wiki/File:Squats.gif
- 原文件：https://upload.wikimedia.org/wikipedia/commons/e/e6/Squats.gif
- **CC BY-SA4.0**：https://creativecommons.org/licenses/by-sa/4.0/
- 原始640×480、66帧、559677字节；SHA-256 `a5d98e1abccc82eaf946bacf65af914c9def49f5e5de8d6e9018ab9f1d38cdaf`。
- 原始动画字节未修改，40ms/帧、2640ms循环；文件契约继续执行。没有作者背书，原文件许可独立于应用代码。编辑衍生物需遵守适用的署名与ShareAlike要求。
