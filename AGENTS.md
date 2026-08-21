# 织卷 V3 独立 Android 项目

## 唯一范围

- 项目根：`D:\deepseekuser\projects\织卷1`。
- 产品名：`织卷`。个人单设备、本地优先；无账号、服务端后台、云同步和广告。
- 本目录使用公开 Git 版本控制；只提交源码、必要品牌资源和项目文档，忽略构建缓存、`local.properties`、密钥、签名材料、APK 与用户项目数据。状态仍以 `docs/ai/PROJECT-STATE.json` 和节点报告为准。
- 规格原件只读：`D:\gptuser\projects\longform-novel-app-planning\development-spec-v3`。
- 完整权威入口：规格根 `AGENTS.md`、`START_HERE.md`、`contracts/docs-manifest.json`、`design-system/long-novel-android-v3/MASTER.md`。

## 每次启动

1. 读取本文件与 `docs/ai/PROJECT-STATE.json`。
2. 读取 `docs/ai/NODE-REPORT-INDEX.md`，只打开 `current_node` 指向的报告。
3. 按任务运行规格脚本：`python ...\scripts\context_pack.py android-ui|generation|storage|quality --list`，再读取最小上下文。
4. 修改前检查现有文件与当前节点未验证项；完成后更新节点报告和 `PROJECT-STATE.json`。

## 工程硬边界

- Gradle 模块恰好三个：`:app`、`:core`、`:data`。
- 顶级路由恰好四个；Provider 协议恰好一个；活动生成任务最多一个；一次只生成一章。
- 普通章节恰好两次模型调用：正文、结构化结算；不增加隐藏调用。
- 正文先保存为可读草稿，再结算并通过 PendingCommit 幂等提交。
- 项目数据使用可恢复文件；不引入 Room、RAG、向量库、多智能体、富文本编辑器、分支版本或自动连写。
- `:core` 无 Android 依赖；`:data -> :core`；`:app -> :core + :data`。

## 品牌与安全

- 保留 `branding/selected/zhijuan-logo-draft.png` 和 `branding/logo-generator-v2/`；App Logo 必须与 selected 文件同源。
- API Key 只允许用户在 App 内输入并由 AndroidKeyStore 保护；不得进入聊天、命令行、日志、项目文件或报告。
- 生产 Endpoint 仅 HTTPS；POST 送达后不自动重试。

## 构建与证据

```powershell
. .\scripts\env-zhijuan1-toolchain.ps1
.\scripts\verify-project-integrity.ps1
.\scripts\run-jvm-tests-ascii-mirror.ps1 -GradleTasks ':core:test',':data:testDebugUnitTest',':app:testDebugUnitTest'
.\gradlew.bat --offline --no-configuration-cache :app:assembleDebug :app:assembleRelease
.\scripts\verify-module-boundaries.ps1
```

- 每个节点必须有 `docs/ai/NODE-REPORT-V3-*.md`；检索标记统一为 `V3-NODE-REPORT`。
- 测试未运行时写 `UNVERIFIED`；真实设备或真实 Provider 缺失时写 `PARTIAL`，不能推断为通过。
- 构建缓存与临时目录固定在 `D:\deepseekuser`。
