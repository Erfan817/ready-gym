# Ready Gym

最终产品方向：**安卓 APP**。`frontend/` 仅为已开始的人体点选交互验证，不是最终 APP，也不按 Web/PWA 方向继续扩展。原有规划文档本次未修改。

## 正式 Android 开发

`android/` 新建 Kotlin + Jetpack Compose 建档→目标→训练地点/器械→确认资料源代码；业务JUnit测试已实际通过。当前Android SDK许可待用户阅读接受，debug APK构建实际失败，**尚无APK，Compose未编译/未真机验证**。环境、测试、构建与隐私边界见 [`android/README.md`](android/README.md)。无帐号、无服务器、仅本次会话内存，不调用AI、不生成训练处方。

## 运行交互验证

目录名末尾含空格，请保持引用：

```sh
cd '/home/ubuntu/ready gym /frontend'
npm ci
npm run dev
```

本次未启动常驻服务。验证命令：

```sh
npm test
npm run typecheck
npm run build
```

## 已完成与边界

- React + TypeScript + Vite；Vitest + Testing Library 真实点击与键盘交互测试。
- 简化正反面 SVG，胸、背、肩、手臂、核心、腿点选；文字按钮同步，勾选、标签、描边和 aria-pressed 不只依赖颜色。
- 按所选肌群取并集筛选人工演示动作；未选时显示全部。
- 动作卡片的查看按钮禁用：详情尚未实现。器械筛选、搜索、空状态尚未实现；收到安卓方向调整后停止新增功能。
- 未接入 Agent、后端、登录、建档、训练计划、GIF、练后反馈、调整或器械替换。无训练处方。动作信息未经专业审核。

## 数据

`frontend/src/data/demoExercises.ts` 是人工编写的界面演示数据，ID 以 `demo-` 开头，不是源仓库 JSON，不包含动作步骤或已审核安全建议。

尝试从 https://github.com/hasaneyldrm/exercises-dataset 固定提交 `7455efae41b330c265e7cd4b78dfa848e7ce5ebd` 获取非媒体数据：GitHub API 查到提交与文件树，读取到 LICENSE 与 NOTICE；完整 JSON 请求超时，未导入或伪造上游内容。数据 MIT 与媒体许可分离，图片/GIF 不在 MIT 范围内。本项目未下载、复制或热链任何图片/GIF，也未使用上游数据，无上游数据版权声明替代。安卓产品后续使用媒体需单独核实授权。

## TDD

React 交互验证逐轮先新增测试、实际运行预期失败，再最小实现并全套回归：边界说明 → 文字肌群开关 → SVG 同步 → Enter/空格 → 肌群并集筛选。安卓业务模型有独立测试；Android UI 和 APK 尚未完成编译与运行验证，详见 `android/README.md`。
