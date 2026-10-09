# Ready Gym

目标产品：**安卓健身 App，以教练对话为入口**，了解用户资料和训练条件，再展示可执行计划、动作演示与受控修订。当前开发测试版 **v0.3.0**，不是已经接通AI的商业产品。

## 本版可操作功能

- 阳光浅色 UI：暖白底、白卡片、橙色操作、绿色点缀；原创器械插画与 adaptive launcher 图标。
- 正式入口为单屏教练会话，不再要求先完成四页表单。用户同意发送资料后才允许开始服务端会话；支持自然语言资料提取、缺项追问、摘要纠正、显式版本确认和删除会话。
- **交付包服务地址为空、Ready Gym专用DeepSeek Key尚未配置。** 入口如实显示不能连接AI，不生成假模型回复。真实DeepSeek请求、HTTPS/访问控制部署仍未完成。HTTP客户端与FastAPI适配器已经实现；通过测试不等于供应商已接通。
- 可选“离线规则模式（不使用AI）”：原卡片建档 → 目标/地点/器械 → 明确安全自述 → 规则草稿 → 七日安排/动作详情 → 用户确认。每周2/3/4天、每次20/30/45/60分钟，初学/恢复经验；进阶明确不支持。
- 草稿与生效版分开，器械替换先出提案，明确确认才采用；风险反馈阻断普通确认和替换，不拿换动作绕过不适。
- **当前六个计划动作已接入指定仓库原始离线GIF及JPG缩略图**：徒手深蹲、跪姿俯卧撑、低幅臀桥、固定手臂单腿伸展死虫式、双侧俯身哑铃划船、杠铃划船。点击播放、暂停/继续，默认不自动播放。变式、来源、注意事项在详情明示，未专业审核。

## 数据来源

`hasaneyldrm/exercises-dataset` 固定提交 `7455efae41b330c265e7cd4b78dfa848e7ce5ebd` 的真实JSON已经取得并按Git blob校验：1,324条、10种语言。项目导出30条精选元数据与1,324条媒体引用映射，不宣称完整动作库都已接入运行时。详见 [`data/README.md`](data/README.md)。

用户已明确声明获得图片/GIF的Ready Gym使用授权；本次按此获取并接入，保留 **© Gym visual** 和上游来源，媒体不重新标为MIT，不虚构审阅过授权文件。跪姿动作上游说明与画面冲突，舍弃该段说明；死虫GIF只展示固定手臂单腿伸展，不按对侧手脚版本描述。详见 [`android/docs/media-attribution.md`](android/docs/media-attribution.md)。

## 验证与限制

**本轮开发切片已通过限定独立复验，测试版可交付；这不代表真实AI或商业产品完成。** 后置整体待核实/待确认/待澄清与第三人上下文由共用判定阻止安全、数字和枚举采用；安卓start/messages/confirm（含GET读回）的2xx JSON解码、契约与非法返回会话字段失败共用安全异常边界，保留专业评估阻断或已验证的更强路线，不从损坏JSON猜诊断。全量实跑：安卓 **129通过、0失败/错误、1明确跳过**，服务端 **41通过**，数据导入 **4通过**；主APK构建、签名、ZIP、zipalign及媒体来源核验通过。验收记录见 [`docs/verification-v0.3.0.json`](docs/verification-v0.3.0.json)，修复历史与复验范围见 [`docs/review-gate-v0.3.0.md`](docs/review-gate-v0.3.0.md)。最终源码交付仍要求GitHub私有仓库推送后回读提交与文件；凭据、签名私钥和构建缓存不提交。

真实回环HTTP已验证未同意403、未配置Key503及OpenAPI一致，临时服务已关闭。一次全量测试出现Robolectric原生ImageDecoder SIGSEGV；隔离探针及随后完整无过滤重跑通过，原失败日志保留，不将其伪装成当时成功。完整计数与候选APK哈希见 [`android/README.md`](android/README.md)。

- Robolectric Compose交互和离屏图不是手机测试；设备测试APK编译不等于运行。Android26/27 GIF分支、软键盘、TalkBack和OEM系统栏仍待真机验证。
- 聊天及未发送输入在Activity ViewModel内存，屏幕旋转保留；结束/进程回收会丢失。规则资料和计划仍是页面内存，可能随重建丢失。无账号、历史同步、训练日志或自动进阶。
- 当前AI只实现**建档适配与契约**，未实现AI个性化计划；确认资料后使用明确标示的规则计划。规则和媒体未专业审核，不是医疗处方或安全认证，不按BMI/性别推算负重。
- 模型Key只放专用服务端配置，不进入APK、Git、日志或聊天。未公开运行无认证开发服务。

## 工程入口

- [安卓构建与验证](android/README.md)
- [DeepSeek建档服务](agent-python/README.md)
- [Android/服务端会话契约](android/docs/coach-api.md) / [OpenAPI](docs/intake-api.json)
- [规则计划核心](android/core/README.md)
- [对话优先决策](docs/conversation-first-ai.md)
- [Agent 当地基设计](docs/agent-as-foundation.md)

`frontend/` 是保留的早期React肌群点选实验，不是正式客户端，不继续扩成Web/PWA。原始灵感和规划保留。

本地目录 `ready gym ` **末尾有空格**，命令中保持完整引用；SDK、缓存、构建产物、密钥和个人记录不提交Git。
