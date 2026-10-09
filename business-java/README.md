# Ready Gym Java business

实现及真实验收见 [`../docs/foundation-business.md`](../docs/foundation-business.md)。共享契约 [`../docs/foundation-demo-contract.md`](../docs/foundation-demo-contract.md)。

## 构建

Java17，不需要 Maven/Gradle。

```
python3 fetch-dependencies.py
sh test.sh --scan-class-path
sh run.sh
```

依赖锁定 Maven Central 坐标、上游 SHA-1 与本地 SHA-256；jar和build忽略提交。默认测试使用真实H2 JDBC隔离，不当作PostgreSQL；显式专用JDBC测试环境可运行真实PG。测试会清理s1/s2 fixture，禁止指向真实用户生产数据库。

业务环境变量仅私密服务端：READY_GYM_JDBC_URL、READY_GYM_DB_USER、READY_GYM_DB_PASSWORD、READY_GYM_INTERNAL_TOKEN、READY_GYM_BUSINESS_PORT。随机internal token不放APK/模型arguments。JVM堆160MiB，仅127.0.0.1监听。

只有用户显式确认档案/计划才事务持久写入；模型只能读取/提出内存草稿。风险自动锁存保护不被none降级，版本history是计划不伪造训练记录；动作规则未经专业审核。
