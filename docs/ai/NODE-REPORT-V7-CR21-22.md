---
document_type: ZHIJUAN_NODE_REPORT
marker: V7-NODE-REPORT
node_id: V7-CR21-22
status: COMPLETE
date: 2026-08-21
requirements: [ZJ-CR-21, ZJ-CR-22]
search_tags: [settlement-retry, readable-draft, content-scale, 清叙, 暗涌, 沉浸, sensory-continuity, no-character-card]
next: REAL_PROVIDER_QUALITY_VALIDATION
---

# V7-CR21-22 节点报告

## 节点结果

本节点完成结算失败的可恢复重试、三档项目级叙事尺度及其正文提示接线，并把 release APK 覆盖安装到实体手机。用户书稿与第 3 章待结算草稿保持不变；验收期间真实 Provider 调用为 `0`。

## 最终需求裁决

1. 三档名称使用 `清叙 / 暗涌 / 沉浸`，不使用写作炉原有直白命名。
2. 不移植或新增人物卡模型、字段、提示词和前台入口。
3. 身体与感官连续性进入每次正文 prompt：衣着、姿势、距离、接触、呼吸、温度、声音、气味、疼痛、疲劳、伤势、行动能力必须连续。
4. `沉浸`档在故事人物明确成年时完整呈现决定剧情的成人过程；既定强迫/非自愿事实不自动淡出、不改写成自愿，同时保留抗拒、限制、当下反应与后续影响，不美化强迫、不虚构同意。
5. 第三方 Provider 是否接受并完整返回由其模型、过滤和服务策略决定，织卷不宣称可绕过外部限制。

## 实现切片

### Core

- `S0ContentScale`：`QING_XU / AN_YONG / CHEN_JIN`。
- `S0Project.contentScale`：已有书默认 `QING_XU`。
- `S0ChapterTask.contentScale`：连续性规划生成任务时固定项目档位。
- `settlementRepairHint`：只在用户显式重试结算时携带上次安全错误摘要。
- `S3RecoveryAuditor`：初次结算加两次显式重试后转 `REVIEW_DRAFT`，停止继续请求。

### Data / Provider

- 文件型项目 schema `1.1` 保存 `contentScale`；读取 `1.0` 时无损默认 `QING_XU`。
- 活动任务或 `PendingCommit` 存在时禁止修改项目档位，避免同一章中途换规则。
- 正文 prompt 以结构化 `<content_scale>` 块携带档位、成年人条件、连续性和事实约束。
- 结算 parser 只规范化一个确定性的 `json`/普通代码围栏，其他无效返回继续严格拒绝。
- 结算修复提示只要求修正格式，不允许改变章节事实。

### App

- 新建书页和书籍更多菜单均提供“叙事尺度”；现有风格 3 preset 组件提供边框、底色、勾选和文字色四重选中信号。
- 恢复卡分别解释：无可解析 JSON、schema 错误、契约不匹配、次数耗尽。
- 次数耗尽后不再显示“只重试结算”，改为查看已保存草稿。
- Provider 档案不可用时服务先进入 foreground，再安全记录 `PROVIDER_PROFILE_UNAVAILABLE` 并结束；不发 API 请求。

## 调用与数据边界

`gradleModules=3` `topLevelRoutes=4` `providerProtocols=1` `activeJobs=1`

`chaptersPerJob=1` `normalCallsPerChapter=2` `settlementRetryProseCalls=0`

`readable-draft-before-settlement` `idempotent-pending-commit` `Room=absent`

本节点没有新增模块、路由、Provider 协议、后台、云同步、Room、RAG、向量库、多智能体、critic/summary/memory 调用或人物卡系统。

## 基线与冲突

- 中文项目路径上的直接 Gradle JVM test worker 仍受 Windows/Gradle argsfile 编码问题影响，会出现 `ClassNotFoundException`；这是既有工具链限制。
- 权威测试通过 `scripts/run-jvm-tests-ascii-mirror.ps1` 同步当前项目快照到 ASCII 路径执行。
- 项目没有 Git，符合用户此前“git 也不要了”的裁决；未执行 reset/checkout。
- 初始需求分析建议的“安全诊断导出”未进入最终确认范围，本节点没有新增该前台功能。

## 验收证据

| gate | result |
|---|---|
| JVM（ASCII 镜像） | 14 suites，74 tests，0 failures/errors/skipped |
| Android API 35 | runner `OK (27 tests)`，0 failures；2 个手工/物理夹具 assumption skip |
| Lint Debug | 0 errors，33 warnings |
| Lint Release | 0 errors，33 warnings |
| module boundary | PASS：3 modules / 4 routes / 1 Provider protocol / acyclic |
| security scan | PASS |
| backup exclusion | PASS：9 domains，allowBackup=false |
| physical backup policy | PASS：Backup Manager 返回 `Backup is not allowed` |
| release signing | v2 PASS；1 signer；non-debuggable |
| physical install | `adb install -r` PASS；未清数据；冷启动 133 ms；0 fatal |
| Provider calls during acceptance | 0 |

签名证书 SHA-256：

`1B7B30A094D72F40A73B4ADA360012A39258BFD7600C8998FF18C677E1975561`

APK：

| artifact | bytes | SHA-256 |
|---|---:|---|
| `outputs/zhijuan-v7-debug.apk` | 32300675 | `EBC9F5F2ECD4F2D23AEE65864062943D36AD2C84B24B4102478E43B25B7AA20F` |
| `outputs/zhijuan-v7-release.apk` | 3024208 | `C1DDDC91633F2660A7E3F5F65FECB6DFE200CD84F4C01E5FAED536DC0EB3889A` |

外部证据：

- `D:/gptuser/projects/zhijuan-change-requests/evidence/cr21-22-release-library.png`
- `D:/gptuser/projects/zhijuan-change-requests/evidence/zhijuan-scale-sheet.png`
- `D:/gptuser/projects/zhijuan-change-requests/evidence/v7-android-instrumentation.log`
- `D:/gptuser/projects/zhijuan-change-requests/CHANGE-REQUEST-2026-08-21-03-SETTLEMENT-AND-MATURITY.md`

## 真机状态

- device：`d2b15cce`，Android API 36。
- package：`app.zhijuan.reader`，version `0.1.0`。
- release 覆盖安装成功；签名与 V6 一致。
- 书“1”仍为已完成 2 章、约 1.2 万字、当前第 3 章。
- 恢复状态仍为“需要恢复：只重试结算”；没有点击该按钮。
- 旧书默认显示 `清叙`；打开尺度 sheet 仅做视觉验收，随后返回，没有保存新档位。

## 下一入口

若要验证“真正长篇”的文学质量，下一节点应由用户选定 Provider、费用预算与允许的成年样本，再做真实章节质量回归。开始前先读：

1. `docs/ai/PROJECT-STATE.json`
2. 本报告
3. `D:/gptuser/projects/zhijuan-change-requests/CHANGE-REQUEST-2026-08-21-03-SETTLEMENT-AND-MATURITY.md`

外部 Provider 的成人内容服从度与长篇文学质量当前标为 `UNVERIFIED`；不要把离线 prompt fixture 结果写成真实模型质量结论。
