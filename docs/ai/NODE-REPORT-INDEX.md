# 第三版节点报告索引

> Agent entrypoint：搜索 `V10-NODE-REPORT` 或节点 ID（当前为 `V10-PUBLIC-REPOSITORY`）；历史节点继续使用 `V9-NODE-REPORT` / `V8-NODE-REPORT` / `V7-NODE-REPORT` / `V6-NODE-REPORT` / `V5-NODE-REPORT` / `V4-NODE-REPORT` / `V3-NODE-REPORT`。
> 当前权威规格：`D:/gptuser/projects/longform-novel-app-planning/development-spec-v3/`。
> 当前实现根：`D:/deepseekuser/projects/织卷1`。
> 版本控制：`GIT_PUBLIC`；状态入口：`docs/ai/PROJECT-STATE.json`。
> 当前变更：有限返回栈、三档剧情节奏、沉浸尺度加强与底栏留白见 `D:/gptuser/projects/zhijuan-change-requests/CHANGE-REQUEST-2026-08-21-05-NAVIGATION-PACE-IMMERSIVE.md`。快速检索 ID：`ZJ-CR-25`…`ZJ-CR-28`。

## 检索规则

1. 先读本索引，只打开当前节点报告。
2. 节点报告提供需求、实现、测试、提交、限制和下一入口；详细命令输出只在其证据文档中读取。
3. `status=COMPLETE` 只表示报告列出的完成条件已有证据；外部门禁未通过时必须标为 `PARTIAL` 或 `BLOCKED`。

## 节点表

| node_id | status | scope | report | evidence | next |
|---|---|---|---|---|---|
| `V3-S0` | `COMPLETE` | 三模块假数据纵切片 | `docs/ai/NODE-REPORT-V3-S0.md` | `TEST-EVIDENCE-S0-V3.md` | `V3-S1` |
| `V3-SEPARATION` | `COMPLETE` | 清除旧项目、标准源码布局、移除版本控制 | `docs/ai/NODE-REPORT-V3-SEPARATION.md` | `SEP-*` | `V3-S1-REAL-CONNECTION` |
| `V3-INTEGRITY` | `COMPLETE` | 新项目工程资产完整性与规格追踪复核 | `docs/ai/NODE-REPORT-V3-INTEGRITY.md` | `INTEGRITY-*` | `V3-S1-REAL-CONNECTION` |
| `V3-S1-QUICK-SETUP` | `COMPLETE` | 默认只填 API Key 的 Provider 快速设置 | `docs/ai/NODE-REPORT-V3-S1-QUICK-SETUP.md` | `QS-*` | `V3-S1-REAL-CONNECTION` |
| `V3-S1-REAL-CONNECTION` | `COMPLETE` | 真机连接、单章两调用、阅读与重启恢复 | `docs/ai/NODE-REPORT-V3-S1-REAL-CONNECTION.md` | `RC-*` | `V3-S2` |
| `V3-S1` | `COMPLETE` | Provider、Keystore、SSE、取消、错误映射 | `docs/ai/NODE-REPORT-V3-S1.md` | `TEST-EVIDENCE-S1-V3.md` | `V3-S2` |
| `V3-READER-SUMMARY-HIDDEN` | `COMPLETE` | 结算摘要保留为内部状态、阅读页完全隐藏 | `docs/ai/NODE-REPORT-V3-READER-SUMMARY-HIDDEN.md` | `RSH-*` | `V3-S2` |
| `V3-S2` | `COMPLETE` | 连续性内核、受限上下文与章节目录 | `docs/ai/NODE-REPORT-V3-S2.md` | `TEST-EVIDENCE-S2-V3.md` | `V3-S3` |
| `V3-S3` | `COMPLETE` | 前台生成、恢复操作与阅读设置 | `docs/ai/NODE-REPORT-V3-S3.md` | `TEST-EVIDENCE-S3-V3.md` | `V3-S4` |
| `V3-S4` | `COMPLETE` | 故障注入、导入导出、五章真机与发布门禁 | `docs/ai/NODE-REPORT-V3-S4.md` | `TEST-EVIDENCE-S4-V3.md` | `V3-S5` |
| `V3-S5` | `COMPLETE` | 纯文字范围、第三方清单、交接与最终回归 | `docs/ai/NODE-REPORT-V3-S5.md` | `TEST-EVIDENCE-S5-V3.md` | `DONE` |
| `V4-EDITORIAL-UI` | `COMPLETE` | 风格 3 编辑式书库、创作、目录、阅读与设置 | `docs/ai/NODE-REPORT-V4-EDITORIAL-UI.md` | `design-qa.md`、`docs/design/v4-ui-proposal/` | `WAIT_USER_CONFIRMATION` |
| `V5-CHANGESET-CR01-13` | `COMPLETE` | 截断、导航、阅读器、创建预设、Skill 与顺序批次 | `docs/ai/NODE-REPORT-V5-CHANGESET-CR01-13.md` | `docs/ai/NODE-REPORT-V5-RELEASE-REGRESSION.md` | `DONE` |
| `V5-CR07-INTEGRITY` | `COMPLETE` | 停止原因、未完成草稿、禁止错误结算 | `docs/ai/NODE-REPORT-V5-CR07-INTEGRITY.md` | JVM 55/0；AndroidTest 编译；debug 构建 | `V5-READER-NAV` |
| `V5-READER-NAV` | `COMPLETE` | 逐层返回、纸色、沉浸工具栏与已提交章节连续阅读 | `docs/ai/NODE-REPORT-V5-READER-NAV.md` | API 35 Reader UI 8/0；debug/test APK 构建 | `V5-PROJECT-PRESETS` |
| `V5-PROJECT-PRESETS` | `COMPLETE` | 题材/基调预设、生成接线、菜单与强选中态 | `docs/ai/NODE-REPORT-V5-PROJECT-PRESETS.md` | JVM 55/0；API 35 UI 5/0；三张视觉证据 | `V5-WRITING-SKILL` |
| `V5-WRITING-SKILL` | `COMPLETE` | Markdown/严格 JSON 创作 Skill、项目质量卡与请求证据 | `docs/ai/NODE-REPORT-V5-WRITING-SKILL.md` | JVM 60/0；API 35 UI 8/0；debug/test APK | `V5-SEQUENTIAL-BATCH` |
| `V5-SEQUENTIAL-BATCH` | `COMPLETE` | 用户明确选择 1/2/3 章并逐章独立生成提交 | `docs/ai/NODE-REPORT-V5-SEQUENTIAL-BATCH.md` | JVM 64/0；API 35 UI/服务 9/0；边界通过 | `V5-RELEASE-REGRESSION` |
| `V5-RELEASE-REGRESSION` | `COMPLETE` | 全量回归、签名 APK 与安装门禁 | `docs/ai/NODE-REPORT-V5-RELEASE-REGRESSION.md` | JVM 64/0；API 35 26/0；lint/build/security PASS | `DONE` |
| `V6-CR14-20` | `COMPLETE` | 通用 Skill、多 API、两项底栏、纯章节号、阅读续写与 50 章影子回归 | `docs/ai/NODE-REPORT-V6-CR14-20.md` | JVM 71/0；API 35 29 reported/27 executed/0 failed；真机 release PASS | `QUALITY_VALIDATION` |
| `V7-CR21-22` | `COMPLETE` | 结算修复重试、次数上限、三档叙事尺度与身体/感官连续性 | `docs/ai/NODE-REPORT-V7-CR21-22.md` | JVM 74/0；API 35 runner 27/0；lint/security/signing/真机 release PASS | `REAL_PROVIDER_QUALITY_VALIDATION` |
| `V8-CR23-24` | `COMPLETE` | 活动/待提交项目安全删除、API 配置隔离、三档前台说明移除 | `docs/ai/NODE-REPORT-V8-CR23-24.md` | JVM 75/0；API 35 29/0；lint/security/signing/真机 release PASS | `REAL_PROVIDER_QUALITY_VALIDATION` |
| `V9-CR25-28` | `COMPLETE` | 有限返回栈、三档剧情节奏、沉浸尺度加强、底栏 `6dp` 留白 | `docs/ai/NODE-REPORT-V9-CR25-28.md` | JVM 78/0；API 35 `OK (30)`；lint/security/signing/真机 release PASS | `REAL_PROVIDER_QUALITY_VALIDATION` |
| `V10-PUBLIC-REPOSITORY` | `COMPLETE` | 公开 GitHub 源码仓库、敏感信息排除与发布核验 | `docs/ai/NODE-REPORT-V10-PUBLIC-REPOSITORY.md` | 206 文件；`SECURITY_SCAN_OK`；匿名读取 PASS | `REAL_PROVIDER_QUALITY_VALIDATION` |

## 当前状态

- `current_node=V10-PUBLIC-REPOSITORY`，`status=COMPLETE`，`pending_gates=[]`；公开仓库为 `https://github.com/kayakdu06-cyber/zhijuan-android`，`main` 已推送并通过匿名读取验证。
- 实体手机 V9 release 已使用 `adb install -r` 覆盖安装成功；没有卸载、清数据或触发真实生成。当前 APK 为 `outputs/zhijuan-v9-release.apk`。
- 阅读/生成现使用可保存有限返回栈；返回父层时弹栈，不会再把两页互相设为返回目标。
- 项目级剧情节奏为 `舒展 / 均衡 / 紧凑`，旧书默认“均衡”，仅影响后续正文；不跳过当前计划或增加调用。
- “沉浸”正文 prompt 已加强为直接、完整、连续呈现明确成年任务，保留身体/感官与自愿/非自愿事实连续性。外部 Provider 服从度仍未验证。
- 底部导航内容下移 `6dp`，与顶部分隔线留出间距；模拟器证据为 `D:/gptuser/projects/zhijuan-change-requests/evidence/zhijuan-v9-bottom-nav.png`。
- 生成或待安全提交的书现在可经“停止任务并删除”显式丢弃：先取消已知本地请求并等待任务停止，再清活动检查点和目标项目目录；删除路径不发新 API 请求，不改 Provider 配置或其他书。
- `清叙 / 暗涌 / 沉浸` 前台只显示名称、选中态和保存，不再显示各档行为说明；内部 prompt 与项目持久化保持不变。
- 结算失败现在区分无 JSON、schema、契约和次数耗尽；初次结算加两次用户显式重试后停止，已有可读草稿继续保留。
- 项目级叙事尺度为 `清叙 / 暗涌 / 沉浸`；正文 prompt 保持身体、感官、自愿/非自愿与后果连续性，不含人物卡逻辑。外部 Provider 的实际服从度仍未验证。
- 底栏只保留“书库/设置”；GENERATION 仍是内部四路由之一，仅从书库或阅读页进入。任何前台页面都不显示规划方向、内部摘要或关键线索。
- Provider 保持单一 OpenAI-compatible 协议，但支持 DeepSeek、Qwen、GLM、Kimi 和自定义兼容中转站的多配置档案；活动任务锁定启动时的档案。
- Markdown/JSON 来源文件上限为 256 KiB，最终质量卡仍为 8 条/1600 字符；Skill 只进入当前正文提示，不构成模型训练。
- 所有用户可见章节标题只显示 `第 N 章`；阅读页右上“续写”先选择 1/2/3 章并显式确认，批次始终逐章两调用。
- 50 章无费用影子回归证明状态机、计划窗口、恢复与提交可持续运行，不代表真实模型文学质量已经完成 50 章验收。
- 风格 3 已作为一套统一系统实现，不是多个互斥方案；最终视觉证据见 `design-qa.md`。
- 只填 API Key 的默认 Provider 设置、五字段建书、8 章初始计划、多项目书库、单章生成、目录/上下章、逐章位置恢复、导入导出均已完成。
- 阅读页只显示章节正文；结算摘要仍保存在元数据中供连续性使用，不进入视觉或无障碍语义树。
- 十种故障与连接手机连续五章通过；正常章恰好正文 1 次、结算 1 次，异常调用只由明确用户动作触发。
- 项目没有语音、录音、朗读、TTS、音频权限或媒体依赖。
- V6 没有新增无障碍专项功能或专项验收；既有 Compose 语义未删除。
- 最终门禁证据：`docs/ai/TEST-EVIDENCE-S4-V3.md`；文字功能证据：`docs/ai/TEST-EVIDENCE-S5-V3.md`；交接：`docs/ai/HANDOFF-V3.md`。
- 工程资产完整性可用 `scripts/verify-project-integrity.ps1` 重复验证；完整本地回归可用 `scripts/verify-build.ps1 -Offline`。

## 全局硬边界关键词

`gradleModules=3` `topLevelRoutes=4` `providerProtocols=1` `activeJobs=1`
`chaptersPerJob=1` `normalCallsPerChapter=2` `Room=forbidden`
`readable-draft-before-settlement` `idempotent-pending-commit`
