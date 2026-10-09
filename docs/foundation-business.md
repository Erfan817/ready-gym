# Java Foundation business service · v0.4.0

Java17 HttpServer，loopback8766；JDBC PostgreSQL业务唯一写入者。用户明确确认profile/plan后写入，模型工具只有读取与提案。实际运行真实PG profile→草稿→confirmv1→四天修订→confirmv2/历史与危险反馈。草稿仅服务内存，不写业务行；confirm按版本/ID/最新档案和风险transaction检查，完全一致重试幂等。

父执行真实PostgreSQL全量13项通过，0跳过/失败（`business-java/build/evidence/parent-postgres-final.log`）；初期H2默认模式测试仅隔离JDBC不称PG。deps固定版本/哈希在`dependencies.lock.json`，`fetch-dependencies.py`从Maven Central实际校验，jar/build/env忽略Git。

- `sh test.sh --scan-class-path`：默认隔离H2；若显式提供专用 `READY_GYM_JDBC_URL/DB_USER/DB_PASSWORD` 则真实PG，fixture仅清理s1/s2，不应指向生产数据库。
- `sh run.sh`：JDK17绝对路径可用；堆160MiB，无Gradle常驻。导入私密业务环境后启动，不打印秘密。
- 内部头`X-Ready-Gym-Internal`随机至少32字符，常量时间比较。严格重复JSON/尾随token/类型/范围及32KiB请求，模型/调用者不决定sets/reps/load。
- `profile/confirm`与`plan/confirm`为业务确认写入口，风险自动锁存是保护记录。`propose_plan/propose_revision`、检索、资格与历史工具读/仅内存草稿，不付费或真实会员声称。
- 风险STOP最高不能被专业review/none降级，clearpending取消未确认草稿，active/history保持但客户端阻断时不展示可用；DELETE删所有owned版本并实际GET404。

Demo无多实例写入/账号恢复/准时TTL清理保证。公开侧由Python会话TTL与访问码保护，主动删除同步业务数据；PG业务行未依赖自动后台过期删除。计划规则未经专业审核，不是医疗处方。所有secret只服务端，不进入APK/源码。
