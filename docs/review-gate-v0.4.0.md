# v0.4.0 Foundation审查与验收

状态：**开发Demo审查门通过，最终主APK已重构建并核验；不是完整商业产品、真机或医学安全认证。** 源码推送和邮件发送另以远端回读为准。

## 最终结果

- 后端61项全部通过，旧非Foundation41项保持。第三独立后端复验`deleg_37341d2b`通过：security_concerns/logic_errors为空；限定重跑9项review regressions，核对全量61项及源码hash。
- Java真实PostgreSQL13项全部通过；草稿不写业务行，用户确认事务保存、版本历史、幂等、风险锁和删除读回被执行验证。
- Android最终无过滤14测试类143项：**142通过、1明确legacy跳过、0失败/错误**。`final-confirm-boundary-full-build.log`为`BUILD SUCCESSFUL in6m23s`，77任务全部执行。
- 最晚Android限定复验`deleg_ffe3c6c2`通过：正常POST与最终GET全部精确核验之后才清除期待快照；原草稿GET失败保留原profile/revision/draft，changedprofile拒绝，重复确认不再POST。核对真实行为RED→GREEN及HTTP10/Chat3通过。未重做全产品审查，不把fixture当live或设备。
- 主APK `com.readygym.app` v0.4.0/code5，10,111,363bytes，SHA256 `ddf3bbc0528c3de40f8486cdfc069d3b39ec01b09840f72d103c54418e811ebd`；原签名证书、ZIP完整性、zipalign、13媒体asset源字节均通过。专用Key/访问码/DB密码/internal token未在解压APK条目发现。
- ZIP只含主APK，9,643,476bytes，SHA256 `650f24dd35294e69439cec88678abd2ba9cebc3e141533d68e95c26d67ac722f`。私密访问码说明单独发送，不进入APK/Git。

## 审查发现与修复轨迹

初始`deleg_d907bc94`后端发现三项：Foundation仍接受旧messages/confirm绕过Java风险与撤销；首snapshot前会话锁窗口；畸形toolargs未处理500。第三独立修复`deleg_5f9ca8ee`逐项真实RED→GREEN：Foundation启用后旧接口409 foundation_required；连续锁从第一个业务await到末次读回，嵌套intake保留外层锁；每provider/tool前重查风险；tool批次严格schema/type/枚举/数组验证与异常归一进入明确fallback。

Android审查`deleg_5f9ca8ee`指出unknown恢复未核对原资料、未发送输入挡住恢复。第三独立修复`deleg_37341d2b`先真实HTTP/Compose行为RED再GREEN，5类69项通过；保留资料revision/candidate与草稿，失败/mismatchGET不清，unknown发送disabled，编辑保留GET恢复action与输入，最高STOP不下降。

后续`deleg_de264ba1`发现正常confirm路径在最终核验之前清期待快照。父新增精确HTTP回归：POSTcorrect→GET原draft→GETchangedprofile→拒绝重复confirm→GETmatching，覆盖revision和candidate变化；真实RED后改normalfinalGET全部统一check通过才clear，13项Foundation GREEN，再全量重构建和`deleg_ffe3c6c2`限定复验通过。RED日志保留，未把首次Compose等待超时/编译问题当行为RED。

## 真实供应商与部署证据

实际官方DeepSeek `deepseek-flash` + Python API + Java17/PG完整闭环已经执行：明确补实际缺项→真实get_profile/check_entitlement/propose_plan→确认v1→自然语言周三没空四天→get_training_history/propose_revision→确认v2/历史→危险反馈不再provider→删样例且404。最近成功运行7calls、已知12,750tokens；早期一轮11,153tokens。中途boundedloop无proposal和资料待补的失败复跑保留日志并删除虚构数据，不当成功、不忽略计费，不自动付费重试。

可信公网HTTPS门控已执行：匿名创建401、有Demo访问码但consentfalse403、授权OpenAPI200且与导出schema精确一致。Java业务/专用PG回环，按需systemd单元无Key、默认disabled，ready health后才宣布启动。

## 主机测试异常与边界

普通共享native Robolectric测试JVM混跑曾AppNotIdle并timeout；GIF独立class全部GREEN。保留`final-full-build.log`、`final-full-build-retry.log`、`gif-isolated-probe.log`；改为每class独立fork、单并发、384MiB测试堆，仍无过滤跑全部14类，新老用例未删/忽略。历史139/141通过APK hash只为当时记录，不指代最终包。

Robolectric图片/HTTPfixture是宿主证据，不是手机/实时AI截图；设备测试APK仅编译，Android26/27 GIF、IME、TalkBack/OEM未真机验收。自编规则和动作未经专业审核；无生产账号/训练组日志/RAG/支付/会员，安全词法有限且可能误报，不作诊断。会话token/对话内存生命周期与PG确认业务持久化不同，未承诺后台准时TTL清理或账号恢复。
