# Ready Gym：对话优先的 DeepSeek 接入

## 用户本轮纠正（原话）

“DeepSeek的，我看了一下，我不是给你发仓库了吗？那个仓库里面不是有 GIF 图，还有动图那些，为啥不直接拿过来用，我希望一开始就是一个界面，然后AI就问我基本信息，而不是我来选”

## 当前事实与下一版目标

已交付的 v0.2.0 仍是离线规则版，不调用DeepSeek。新的正式入口改为阳光浅色单屏对话，AI主动问询，不再先要求完成四页表单。原表单保留为可选离线/编辑入口，不再作为使用AI的必经流程。

用户可自然说一句多信息，例如“21岁，175厘米、70公斤，想增肌，在健身房练，有哑铃，每周三次”。服务端将真正的DeepSeek输出当作提案，验证并整理为结构化档案；只追问缺失或矛盾的信息，不重复询问已给出的值。不能默认没有不适或医学限制，不能猜年龄、经验、器械或训练能力。性别非必填、可不透露。

最后给摘要卡：资料、目标、训练条件、频率、时长和安全自述。用户确认后才能采用档案和进入计划流程。AI文字不能隐式确认，也不能跳过风险分流或直接覆盖已有计划。当前确定性计划引擎继续作为验证/规则底座；AI问询、AI计划提案、规则回退模式必须明确区分。

## DeepSeek 接入边界

选用DeepSeek官方API，文档入口 https://api-docs.deepseek.com/ ，base URL `https://api.deepseek.com`。当前官网列出的Flash模型名为 `deepseek-flash`；实施时仍须通过账号可用模型和真实请求核验，不凭旧教程猜名字。

凭据仅从Ready Gym专用服务端配置读取，不读Hermes自身密钥，不进APK、Git、日志或模型上下文。本机创建了私有配置目录 `/home/ubuntu/.config/ready-gym/`（权限700）；Key尚未配置。可由用户通过服务器终端/SFTP保存专用 `.env`（权限600），例如变量名 `DEEPSEEK_API_KEY`；原值不得发进聊天。真实调用会消耗用户DeepSeek额度，先做有限开发验证，不启动公共无认证接口。

App在首次发送训练资料前明确说明会发送至Ready Gym服务端和DeepSeek，并取得用户同意。不会为开发随意转发聊天历史或真实用户健康数据；真实验收先用合成测试档案。

服务尚未部署、远端HTTPS地址/访问控制尚未配置。未提供Key或服务地址时明确显示“未连接/配置未完成”，不能用模拟回复假称DeepSeek已连上。mock只用于契约/失败用例，不作为真实接入验收。

## 当前实现接口（机器契约见intake-api.json）

- `POST /api/intake/sessions`：明确用户同意后开始；返回随机session_id、session_token、首问、档案状态；问句由服务端安全策略整理，provider提取模式和response_source明确区分。
- `POST /api/intake/sessions/{session_id}/messages`：Bearer使用仅该会话的session_token，提交自然语言回答；返回assistant_text、validated_profile、missing_fields、status（collecting/ready/blocked）、usage与provider_mode。
- `GET /api/intake/sessions/{session_id}`：授权读取精确会话快照，无模型调用。
- `POST /api/intake/sessions/{session_id}/confirm`：用户显式确认，校验expected_revision和完整/安全档案；客户端读回精确快照才继续，不把模型文字当确认。
- `DELETE /api/intake/sessions/{session_id}`：删除当前服务端会话；客户端读回404才清除本地。

会话有过期、长度、并发和调用次数限制；日志不记录Key或原始健康文本。配置未完成拒绝真实请求。开发默认仅绑定127.0.0.1，正式对外前另行实现鉴权/配对、TLS和费用配额。会话token不等于生产用户认证，不能直接把开发API暴露公网。

## 原始仓库核验与媒体边界

仓库：https://github.com/hasaneyldrm/exercises-dataset ，固定提交 `7455efae41b330c265e7cd4b78dfa848e7ce5ebd`。

本轮GitHub递归树API返回未截断，代码实际统计videos下1324个GIF、images下1324个文件。主JSON17,391,530字节、blob SHA `3cbb77b678c40d7342897d38e4c61e9502f829a7`现已实际取得并完成完整blob核验；导出30条精选元数据和1324条媒体引用，当前6计划媒体已整合进安卓播放器及缩略图，不代表全部动作库已接入运行时。

重新读取LICENSE和NOTICE：数据/说明MIT，图片/GIF明确排除在MIT外，作者的Gym visual分发许可不自动授予下游项目。NOTICE原文：“cloning this repo is not a license”。

本轮已读取Gym visual条款：https://gymvisual.com/content/3-terms-and-conditions-of-use 。一般条款与仓库媒体例外仍保留为来源记录。

**用户随后明确告知：“图片、GIF图我已经得到授权了”。** 本项目据此按用户已取得的授权开展Ready Gym素材获取与接入，不再把授权缺失作为开发阻塞。这是用户提供的授权状态；尚未收到或审阅具体授权文件，不虚构许可编号、期限或额外用途。素材继续保留Gym visual署名、来源与原始分辨率，不能将媒体重新标为MIT。

数据和媒体按source ID/版本/路径映射，优先下载当前计划所需的具体动作和变式，接入已有播放器；不必为此全量打包1324个GIF。核验文件、帧和动作匹配后再集成，取得授权不等于每项动作映射或播放已经验证。
