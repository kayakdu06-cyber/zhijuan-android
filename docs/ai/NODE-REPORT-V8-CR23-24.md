---
document_type: ZHIJUAN_NODE_REPORT
marker: V8-NODE-REPORT
node_id: V8-CR23-24
status: COMPLETE
date: 2026-08-21
requirements: [ZJ-CR-23, ZJ-CR-24]
search_tags: [safe-project-discard, cancel-before-delete, pending-commit-discard, api-config-isolation, no-api-request-on-delete, content-scale-ui, no-behavior-description]
next: REAL_PROVIDER_QUALITY_VALIDATION
---

# V8-CR23-24 节点报告

## 节点结果

本节点解决“项目正在生成或等待安全提交时无法删除”，并移除 `清叙 / 暗涌 / 沉浸` 在前台的行为说明。release APK 已覆盖安装到实体手机，未卸载、未清数据、未删除用户书稿、未点击待结算恢复，真实 Provider 调用为 `0`。

## 最终需求裁决

1. 用户明确确认删除目标书后，允许连同该书的活动任务检查点、可读草稿和 `PendingCommit` 一起丢弃。
2. 删除必须先取消目标书请求并等待生成协程结束，再删除文件；不得让旧任务在删除后回写。
3. 删除不发送新的 API 请求，不改 API Key、Provider 档案或其他书；另一部书正在生成时拒绝删除。
4. 前台保留三档名称和强选中态，不展示各档的行为、身体/感官、成人情节或 API 说明。
5. 三档持久化及正文内部 prompt 规则不变；本节点只精简 UI，不弱化生成系统。

## 实现切片

### Core / Data

- `S0NovelRepository.discardProject` 表示用户确认后的显式破坏性动作，与普通受保护的 `deleteProject` 分离。
- `FileS0NovelRepository` 对目标项目目录做精确规范化校验；普通删除继续拒绝活动任务和 `PendingCommit`，显式丢弃只删除该项目目录。
- 单元测试构造目标项目的 `jobs/active.json` 与 pending commit，验证普通删除拒绝、显式丢弃成功，另一项目保持完整。

### App / Service

- 删除确认框按状态显示“停止任务并删除”，说明会丢弃未提交内容、不会发送新 API 请求、不会影响 API 配置或其他书。
- 服务删除顺序：收集目标项目已知 request ID → 本地取消正文/结算请求 → `cancelAndJoin` 等待该项目生成协程 → 清活动任务检查点 → 显式删除项目目录 → 发布完成状态。
- 若活动任务属于另一项目，删除请求在 UI 层拒绝；服务层也有二次保护。
- 删除完成后清理该项目阅读位置和恢复 UI 状态。
- 新建书和已有书的尺度界面均只显示 `清叙 / 暗涌 / 沉浸`、选中态与保存操作。

## “API 干净”的精确含义

- Provider 配置目录：`filesDir/zhijuan-config`。
- 项目目录：`filesDir/zhijuan-projects/<projectId>`。
- 删除只处理精确目标项目路径；不会删除或改写配置目录。
- 删除调用 `provider.cancel(requestId)` 只取消本地进行中的 HTTP call，不创建模型请求；完成后不会自动重试目标书。
- OpenAI-compatible Provider 没有跨书复用的会话 ID；后续其他书不会读取已删除书的本地连续性数据。
- 本地删除无法撤回已经发送给第三方的历史请求、日志或计费记录；这是 Provider 端边界，不应宣称“远端记录已清除”。

## 调用与架构边界

`gradleModules=3` `topLevelRoutes=4` `providerProtocols=1` `activeJobs=1`

`chaptersPerJob=1` `normalCallsPerChapter=2` `deletePathModelCalls=0`

`readable-draft-before-settlement` `idempotent-pending-commit` `Room=absent`

本节点没有新增模块、顶级路由、Provider 协议、后台、云同步、Room、RAG、向量库、多智能体、隐藏 critic/summary/memory 调用或人物卡系统。

## 验收证据

| gate | result |
|---|---|
| JVM（ASCII 镜像） | 14 suites，75 tests，0 failures/errors/skipped |
| 定向 Android API 35 | `OK (11 tests)`，0 failures |
| 全量 Android API 35 | `OK (29 tests)`，0 failures；2 个手工/物理夹具 assumption skip |
| Lint Debug / Release | 各 0 errors，33 warnings |
| module boundary | PASS：3 modules / 4 routes / 1 Provider protocol / acyclic |
| security scan | PASS |
| backup exclusion | PASS：9 domains，allowBackup=false |
| physical backup policy | PASS：`Backup is not allowed` |
| release signing | v2 PASS；1 signer；non-debuggable |
| physical install | `adb install -r` PASS；未清数据；冷启动 129 ms |
| Provider calls during acceptance | 0 |

签名证书 SHA-256：

`1B7B30A094D72F40A73B4ADA360012A39258BFD7600C8998FF18C677E1975561`

APK：

| artifact | bytes | SHA-256 |
|---|---:|---|
| `outputs/zhijuan-v8-debug.apk` | 32300675 | `417A01CDE8F6CA6BAE89241A0FC2BD0808D9C178EEE74BEB2A53AD7DA2E232F5` |
| `outputs/zhijuan-v8-release.apk` | 3024208 | `7BB8B940AA90335C9EB68A010F3161D9F7BD2C77FF78BA9F195AB65713C92423` |

外部证据：

- `D:/gptuser/projects/zhijuan-change-requests/evidence/v8-targeted-android-instrumentation.log`
- `D:/gptuser/projects/zhijuan-change-requests/evidence/v8-full-android-instrumentation.log`
- `D:/gptuser/projects/zhijuan-change-requests/evidence/zhijuan-v8-library.png`
- `D:/gptuser/projects/zhijuan-change-requests/CHANGE-REQUEST-2026-08-21-04-SAFE-DISCARD-AND-SCALE-UI.md`

## 真机状态

- device：`d2b15cce`，Android API 36。
- package：`app.zhijuan.reader`，version `0.1.0`。
- release 覆盖安装成功，应用数据未清除。
- 书“1”仍为已完成 2 章、约 1.2 万字、当前第 3 章。
- 恢复状态仍为“需要恢复：只重试结算”；没有点击该按钮。
- 手机验收期间其他应用抢占前台后立即停止自动点击；未打开或确认真实书籍删除框。删除 UI 与行为由 API 35 自动化夹具验证。

## 下一入口

后续如验证真实长篇质量，先读：

1. `docs/ai/PROJECT-STATE.json`
2. 本报告
3. `D:/gptuser/projects/zhijuan-change-requests/CHANGE-REQUEST-2026-08-21-04-SAFE-DISCARD-AND-SCALE-UI.md`

外部 Provider 成人内容服从度与长篇文学质量仍为 `UNVERIFIED`。不要把本节点的 UI/删除测试写成真实模型质量结论。

