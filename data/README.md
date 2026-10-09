# 固定版本真实动作数据与少量已授权素材

来源：[hasaneyldrm/exercises-dataset](https://github.com/hasaneyldrm/exercises-dataset)，固定提交 `7455efae41b330c265e7cd4b78dfa848e7ce5ebd`。

## 已取得与校验

- 完整 `data/exercises.json`：**1,324 条**，1,324 个唯一字符串动作 ID、1,324 个唯一媒体 ID，所有记录均有 **10 种语言**指导与分步指导。
- 原文件 **17,391,530 字节**；Git blob SHA-1 `3cbb77b678c40d7342897d38e4c61e9502f829a7`；SHA-256 `656634224b8977b99a6d765470ee123260d4979715eaa4e7c0b7c8bb0d79f93d`。
- JSON 实际经固定提交 jsDelivr 公共镜像取得；原字节按 Git blob 算法核验后才导入。原始文件留在 `/home/ubuntu/.hermes/cache/scratch/exercises-dataset-pinned/exercises.json`，不将 17 MB 原文件放入项目。scratch 可能被清理。
- GitHub `images/`、`videos/` 两个目录树分别实际取得 1,324 个文件，未截断；对全部记录逐条核验 `id-media_id` 路径和目录树存在性。
- 本目录 **30 条精选元数据**不是完整数据集接入；现有 Android/core/后端运行时未修改。

## 文件

| 文件 | 内容 |
|---|---|
| `source_manifest.json` | 实际大小、固定提交、blob/sha256、记录计数、许可/媒体范围、完整性与边界 |
| `exercise-catalog.json` | 30 条原始 ID/name/equipment/target/肌群/次要肌群，保留原文中文和英文指导、媒体路径、署名 |
| `media-reference-index.json` | 全部 1,324 条 ID/media_id/图片/GIF 路径与各媒体 Git blob SHA-1；不是媒体批量下载 |
| `exercise-mapping.json` | 当前 6 个 stableId 的源记录候选与文本差异；不自动启用 |
| `current-plan-media-mapping.json` | 六 stableId 对应具体变式、原文中文指导、器械/目标、local/image/GIF 路径、实际媒体校验与抽帧判断 |
| `selection.json` | 可审阅精选 ID、映射与质量警告；未编造源 ID 或中文名称 |
| `SOURCE/LICENSE`、`SOURCE/NOTICE.md` | 固定提交上游原文，Git blob 核验通过，MIT 与媒体例外保留 |
| `source-media/current-plan/` | 7 个候选（当前六项加 2398 窄距跪姿候选）的 7 GIF+7 thumbnail；不覆盖父任务的 `repo-samples/` |
| `source-media/current-plan-verification.json` | 14 个文件实际 blob 校验、大小、180×180、帧数/独立帧数 |
| `source-media/current-plan/contact-sheet.jpg` | 每个GIF取5帧，供变式审阅；非新的动作源素材 |
| `scripts/import_exercises.py` | stdlib 离线校验与导出，不联网、不自动下载媒体 |
| `tests/test_import_exercises.py` | TDD 核验源真实性、导出忠实性、具体变式、许可声明边界与媒体证据 |

## 安卓接入后的增量（v0.3.0）

本导入任务原本不改Android/core/backend，以下导入验收与保守映射标志保持原样。父整合已另外将当前6动作的原始180×180 GIF与JPG接入Android详情播放器/静态预览。3211不沿用矛盾的脚掌指导；0276明确标固定手臂单腿伸展，而非对侧手脚版本；3013标低幅臀桥。运行时来源与取舍见 `../android/docs/media-attribution.md`；原映射不匹配标志不是“当前App没有GIF”的断言。

## 当前六项对应

| Ready Gym stableId | 上游 ID / name | 差异或缺口 |
|---|---|---|
| `rg-v1-bodyweight-squat` | `3119` / potty squat | 原始英文名保留；抽帧可见无器械双脚深蹲，无额外支撑 |
| `rg-v1-knee-push-up` | `3211` / kneeling push-up (male) | GIF抽帧可见膝盖支撑，但上游中英文指导写脚掌支撑，**不能直接用矛盾指导**；2398 为窄距膝支撑，仅候选，未替换 |
| `rg-v1-glute-bridge` | `3013` / low glute bridge on floor | 抽帧可见双侧地面臀桥，无凳/外加负重 |
| `rg-v1-dead-bug` | `0276` / dead bug | 源指导描述交替对侧手脚；五帧联系图尚不能确认手臂交替与对侧配合，媒体变式确认留作明确缺口 |
| `rg-v1-dumbbell-row` | `0293` / dumbbell bent over row | 抽帧可见双手各持哑铃、站姿同时划船，无凳；**不用 0292 单臂凳支撑变式** |
| `rg-v1-barbell-row` | `0027` / barbell bent over row | 抽帧可见双手站姿俯身杠铃划船 |

所有7个GIF均为原始180×180、12帧，其中10个不同画面；thumbnail 均180×180。视觉判断只覆盖抽样帧，不是动作正确性、临床安全或适用性认证。

## 授权与署名

用户已明确声明这些图片/GIF获授权用于 Ready Gym；按该声明取得当前小批素材，不再把未提交许可文件视为阻塞。**未声称审阅过许可书**，不推定可转授权、广泛再分发或热链许可。元数据/指导使用上游 MIT；媒体仍是独立 Gym visual 权利，保留原始大小与署名：

> © Gym visual — https://gymvisual.com/

`source-media/current-plan/` 是待父任务接入的资源，尚未打包进入 Android APK；`runtime_enabled: false`/`bundled: false` 仅表示运行时未接入，不否认用户授权。

## 离线重建与测试

导入器仅依赖 Python 标准库；测试中的媒体解码核验另需 Pillow（本机已实际可用）。

```sh
python3 data/scripts/import_exercises.py --source-dir /home/ubuntu/.hermes/cache/scratch/exercises-dataset-pinned
python3 -m unittest discover -s data/tests -v
```

输入目录要求完整原始 JSON、实际 `images-tree.json`/`videos-tree.json` 与原文 `LICENSE`/`NOTICE.md`。若存在 `selected-media-verification.json`，导出器会重算对应本地媒体 Git blob 后附上小批媒体证据；实际二进制必须仍存在。未取得数据不得自造 fixture 作为真实源。网络获取只需要公开 JSON/目录树/许可和选定少量媒体，不要 clone 整仓库或下载所有媒体。

上游中文指导质量尚未逐条审核；没有上游中文动作名称字段，映射里的中文显示名来自现有 Ready Gym 标签并显式区分来源。
