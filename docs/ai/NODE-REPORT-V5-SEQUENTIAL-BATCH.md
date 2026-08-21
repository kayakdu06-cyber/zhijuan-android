---
document_type: V5_NODE_REPORT
marker: V5-NODE-REPORT
node_id: V5-SEQUENTIAL-BATCH
status: COMPLETE
date: 2026-08-21
requirements: [PV-002, PV-004, PV-005, PV-009]
change_requests: [ZJ-CR-05]
search_tags: [batch-generation, sequential-chapters, one-active-job, two-calls-per-chapter, fail-fast, process-death]
---

# 节点报告：1/2/3 章顺序续写

## 可见结果

- 创作页新增“本次续写”设置，固定提供 1/2/3 章，默认 1 章。
- 选中态使用实色主色背景、2dp 描边、反色文字、加粗文字和 `selected=true` 语义，不再只依靠轻微色差。
- 界面实时显示本次最多调用次数：1/2/3 章分别最多 2/4/6 次模型调用。
- 选择多章后主按钮改为“顺序续写 2 章/3 章”；运行中同时显示批次位置，例如“批次 2/3 · 正在生成正文”。
- 计划不足时不可选择超过现有计划的章数；计划进入明确刷新门槛时仍先要求用户扩展计划。

## 运行语义

- 批次只是当前进程内的一次用户意图，不是新的持久化实体。
- 每章都重新从权威项目状态领取一个 `GenerationPermit`，建立一个独立 `S3GenerationJob`，完成正文保存、结算、PendingCommit 和幂等提交后，才开始下一章。
- 每一正常章仍恰好两次调用：正文 1 次、结构化结算 1 次；没有批量 Prompt、并行请求、隐藏总结、审稿或第三次调用。
- 第一项非 `Committed` 结果立即停止剩余批次，包括截断草稿、结算失败、网络错误、计划耗尽与本地拒绝。
- 用户取消会取消当前章的两个确定性 request ID 并停止整批；每章单独重置 20 分钟本地超时。
- 批次大小不写入活动 Job 或项目文件；进程死亡后只按当前一章的检查点恢复，不会自动续跑未开始的章节。

## 对写作质量的影响

- 后一章只会在前一章完成结构化结算并提交新状态后生成，所以不会出现并行多章常见的事实分叉。
- 质量卡、题材、基调、计划目标和连续性检查均逐章重新构建，单章提示质量不因选择 2/3 章而缩水。
- 实际代价是用户在章与章之间审阅、改方向或替换 Skill 的机会减少；因此默认保持 1 章，并在界面明确提示该权衡。

## 主要改动文件

- `app/src/main/kotlin/app/zhijuan/reader/S3SequentialBatch.kt`
- `app/src/main/kotlin/app/zhijuan/reader/S3GenerationForegroundService.kt`
- `app/src/main/kotlin/app/zhijuan/reader/S0App.kt`
- `app/src/test/kotlin/app/zhijuan/reader/S3SequentialBatchTest.kt`
- `core/src/test/kotlin/app/zhijuan/core/s0/S0GenerationCoordinatorTest.kt`
- `app/src/androidTest/kotlin/app/zhijuan/reader/S5TextProjectUiTest.kt`

## 证据

- JVM ASCII 镜像完整回归：64 项，0 失败，0 跳过。
- 核心三章集成测试：提交章号 `[1,2,3]`，正文调用 3，结算调用 3，最终 revision 3。
- 批次运行器测试：严格顺序、首个失败即停、只接受 1..3。
- API 35 `S5TextProjectUiTest + S3ForegroundServiceTest`：9 项，0 失败。
- `:app:assembleDebug :app:assembleDebugAndroidTest`：通过。
- 模块边界：3 模块、4 顶级路由、1 Provider 协议，检查通过。
- 当前 debug APK SHA-256：`AE0B71FA331D652922D57DDAC49AFEBFD4E3B75DCB1C2B40C2BC38E682A5A46E`。

## 下一入口

继续 `V5-RELEASE-REGRESSION`：执行全量 JVM/仪器/完整性/模块边界/lint/debug/release 回归，生成最终 APK 和变更集总结。
