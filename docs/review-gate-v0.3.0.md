# v0.3.0 开发切片验收

**状态：已通过限定独立复验，可交付开发测试包；真实DeepSeek、生产部署和设备验收未完成。**

## 最终验证

- 最后生产修改后的完整构建：`--rerun-tasks`，77个Gradle任务执行，主APK与设备测试APK均构建成功。
- Android/core JUnit XML：130项，129通过，0失败/错误，1个Android26/27 legacy Movie宿主探针明确跳过。
- FastAPI/DeepSeek适配器：41个test方法通过，外部供应商网络仅使用明确标记的MockTransport；不能据此声称真实AI已接通。
- 数据导入4项、原创资源2项、旧Commons媒体1项、新仓库GIF/JPG文件契约1项均通过。
- 实际回环HTTP核验未同意403、未配置Key503、导出OpenAPI与运行时精确一致；临时服务已关闭。
- 主APK签名、ZIP完整性、zipalign、13个媒体asset的字节一致性及新增异常边界DEX标识均核验。

机器可读来源hash、APK/ZIP hash及限制见 [verification-v0.3.0.json](verification-v0.3.0.json)。原始测试日志、XML、离屏图片和构建产物保存在忽略Git的 `android/build/verification/v0.3.0/`。

## 已完成的修复与审查轨迹

第一轮只读审查 `deleg_f4507fb3` 揭示安全否认歧义、provider最高风险未锁存、修正后仍能确认旧资料、巨大数字异常、安卓旧摘要风险绕过、删除取证/恢复及401恢复。限定修复以真实运行时RED→GREEN完成；第二轮审查 `deleg_0fdbf9ea` 又发现整句矛盾/第三人回答、已知原始眩晕遇空提案或额度限制时保留旧安全、否定数字/撤回枚举解除澄清，以及服务端风险转离线绕过。

第二次限定修复分别添加完整句证据、原始/provider风险优先级、Activity级风险传播、真实HTTP→Compose离线建档→PlanningEngine组合回归。复验 `deleg_eb899e43` 进一步揭示后置整体待核实尚未被安全否认共用、2xx JSON构造失败未携带安全反馈。达到原限定周期后已向用户明确报告未通过；用户选择统一判定/异常入口继续，未把失败包假称交付。

用户批准后共用 `untrusted_answer_context` 处理撤回与所有採用路径，`checkedIntakeResponse` 统一包住建档及确认POST/GET的解码/契约错误。完整安全补丁与单字段补丁、初填/已确认、前后待核实/待确认/待澄清、失败/额度、无歧义新回答恢复均先运行时失败再通过。损坏JSON、root-array、截断响应也有真实HTTP与离线规划组合回归，不从破碎片段臆测已验证STOP。

复验 `deleg_3476b474` 确认服务端41方法及精准补验通过，同时发现返回非法会话ID/令牌的异常仍按本地invalid_session分类而丢失已解析风险。现将 `parseReply` 内全部校验异常归为响应契约失败并携带已解析路线；本地请求前校验与删除实际404取证保持独立。新增8个非法ID/token HTTP用例、3种失败首问→Activity→离线规划路径与本地校验对照，运行时RED为2失败/1通过，GREEN三项通过，最后全量全部重跑。

**最后限定复验 `deleg_b7a4e3aa` 通过，security_concerns与logic_errors均空。** 复验核对当前生产源码hash、编译字节、8个用例、三个新增方法的全量XML及实际APKhash；未重新执行Gradle，不将只读核对写成真机或真实模型调用。

## 尚未完成的产品验收

- Ready Gym专用DeepSeek Key、安全HTTPS地址/生产访问控制未配置，默认APK服务地址为空。
- 真正AI个性化计划生成、长期训练记录、自动进阶、账号同步尚未实现。
- Android26/27真实GIF、手机软键盘、TalkBack、OEM系统栏及完整设备适配尚未验收。
- 规则和语言守卫没有专业训练/医疗审核；它们是保守业务阻断，不是诊断、医学筛查或安全认证。
- 风险保留仅Activity内存，不跨进程结束；数据存储边界见Android与服务README。

最终源码交付要求普通fast-forward推送既有私有 `Erfan817/ready-gym`，并读取远端精确提交、树与blob核验。密钥、签名私钥、构建缓存、健康聊天及未授权用途不进入提交；GitHub已备份不等于上述真实AI或设备验收完成。
