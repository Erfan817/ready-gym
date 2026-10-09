# Foundation Demo 操作与运行

## 手机试用

1. 安装 v0.4.0 主 APK（沿用旧 Debug签名，Android8.0+）。
2. 打开“Demo连接设置”，地址使用 `https://150.158.125.67:26100/ready-gym-demo`。从单独发送的私密说明取得 **Demo访问码** 并粘贴；这不是 DeepSeek Key，也不能放进 APK。
3. 保存仅在本次 Activity 内存，没有联网。点击“同意并开始”后才建立会话/调用模型并上传训练资料。
4. 可用虚构样例：`21岁，175厘米，70公斤，目标增肌，地点在家，有哑铃，每周3天，每次30分钟，新手`；接着分别明确回答当前不适与医学训练限制。真实试用只填自己的真实状态，不按示例否认实际风险。
5. 资料摘要确认 → 对话内草稿 → 点动作查看原始GIF与要点 → 明确采用 v1。
6. 发送 `周三没空，改成四天` → 查看差异 → 确认 v2。直到确认前，v1不会改变。
7. 请求结果未知时先“读回确认状态”，不要反复确认/发送以避免重复请求。结束时点击清空删除，会删除对应 Java 保存数据并实际 GET404 验证。

## 独立本地运行

Java17、PostgreSQL16+、Python3.12。所有秘密配置在 `~/.config/ready-gym/`（目录700，普通文件600），不提交。模型 Key配置 `.env` 仅 `DEEPSEEK_API_KEY=...`；业务 `business.env` 含专用非superuser JDBC role/DB参数和随机 `READY_GYM_INTERNAL_TOKEN`。Demo访问码是随机至少32字符的独立文件 `demo-access-code`，原生 Android 启动时输入，不是模型密钥。

- Java：`business-java/run.sh`，仅回环8766，160MiB heap，准备业务 schema。`business-java/test.sh --scan-class-path`，默认 H2 隔离，显式 JDBC环境时为真实PG；两者分开记录。
- Python：安装 `agent-python/requirements.txt` 到专用venv；在该目录运行 `python -m uvicorn ready_gym_demo:create_private_demo --factory --host 127.0.0.1 --port 8765 --no-access-log`。默认旧 `create_app` 不读Key/不启Java。
- TLS网关把 `/ready-gym-demo/*` 移除前缀后反代回环8765。保留既有可信证书验证；不关闭客户端TLS校验。
- 公共端所有请求必须 `X-Ready-Gym-Demo`，除创建外再带本会话Bearer。门控失败401在模型调用前拒绝；无通配CORS、无匿名创建入口。

## 这台验证主机

独立PG集群在 `~/.local/share/ready-gym-runtime/postgres`，loopback55432，16MiB shared_buffers/12连接；系统默认PG服务停用，不随重启常驻。

按需启停（仅此Demo；systemd单元已安装，默认disabled、不随开机启动）：

```sh
sudo systemctl start ready-gym-agent.service
# 依赖自动启动专用Java/PG；先health/readback验证再试用
sudo systemctl stop ready-gym-agent.service ready-gym-business.service ready-gym-postgres.service
```

三个单元分别是私密API、Java业务、独立PG；单元内容不含Key/数据库密码/访问码，API factory从私密目录600文件读取，Java使用EnvironmentFile。没有修改Hermes配置或共享聊天网关服务定义。

手动低层数据库操作仍可用于本地诊断：

```
/usr/lib/postgresql/16/bin/pg_ctl -D "$HOME/.local/share/ready-gym-runtime/postgres" -l "$HOME/.local/share/ready-gym-runtime/postgres.log" -o "-p 55432 -h 127.0.0.1 -k $HOME/.local/share/ready-gym-runtime/pgsocket -c shared_buffers=16MB -c max_connections=12" -w start
# Java和Python按上文命令启动；检查health后才开始试用
/usr/lib/postgresql/16/bin/pg_ctl -D "$HOME/.local/share/ready-gym-runtime/postgres" -m fast -w stop
```

不使用时停止Demo Java/Python/PG进程，不动聊天网关/其它会话桌面或代理。模型Key在聊天出现过，建议撤销并在专用私密配置中换新；不在聊天索取/回显Key。访问码也不要公开分享。

## 不包含与安全边界

Plan版本历史不等于完成训练日志；无生产账号/RAG/会员/收费。资料/会话期限与业务保存不同：服务内存过期后不能恢复帐号，业务行主动删可验证，惰性过期清理不当后台准时保证。规则未经专业审核；模型失败回退仅明确标记自编规则，不说“专家审核通过”。风险守卫为有限保守词法，不是诊断或完整医学筛查；不能把删除/重开作为医疗许可。
