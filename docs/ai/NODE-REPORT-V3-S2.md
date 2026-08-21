---
document_type: V3_NODE_REPORT
marker: V3-NODE-REPORT
node_id: V3-S2
status: COMPLETE
date: 2026-08-20
requirements: [PV-004, PV-007, PV-010, PV-016, PV-017]
search_tags: [continuity-core, context-builder, chapter-directory, no-extra-calls]
---

# 节点报告：V3-S2 连续性内核与章节目录

状态：`COMPLETE`。实施计划见 `docs/ai/IMPLEMENTATION-PLAN-S2-V3.md`；测试证据见 `docs/ai/TEST-EVIDENCE-S2-V3.md`。

当前硬边界：`modules=3` `routes=4` `provider_protocols=1` `normal_calls=2` `automatic_multi_chapter=false`。

## 节点结果

- 阅读器已支持目录底部抽屉、任意已保存章节切换、当前章文字标识，以及有边界的上一章/下一章。结算摘要继续只供内部连续性使用，不进入正文、视觉树或无障碍语义树。
- `S2ContextBuilder` 只装入当前计划项、最多 6000 字上一章尾部、最近 5 个且单条最多 1000 字的摘要、相关实体 ID、硬事实与一次性事件键；不会传入全书正文。
- 权威连续性夹具实际包含 9 个案例（规格正文称“八条规则”，夹具另含章节跳号）。实现按唯一权威夹具逐案精确匹配，不用模型参与验证。
- 章节结算升级为 `settlement.schema.json` 1.0：严格根键、有限操作、证据、事件和嵌套字段验证；旧六键临时格式现在明确拒绝。
- 正文 Prompt 使用结构化 `ChapterTask`；结算 Prompt 只读取同一任务、前置事件键和已保存正文。普通章节仍恰好两次调用。
- 重复一次性事件会保留第二章为 `READABLE_DRAFT`，不写 PendingCommit、不推进 revision、不消费计划；已提交事件持久化并在重启后继续生效。
- 计划剩余 1–2 项时界面明确提示不会自动连写或隐藏刷新。

## 主要实现文件

- `core/src/main/kotlin/app/zhijuan/core/s0/S2Continuity.kt`
- `core/src/main/kotlin/app/zhijuan/core/s0/S0GenerationCoordinator.kt`
- `data/src/main/kotlin/app/zhijuan/data/s0/provider/OpenAiCompatibleS1Provider.kt`
- `data/src/main/kotlin/app/zhijuan/data/s0/FileS0NovelRepository.kt`
- `app/src/main/kotlin/app/zhijuan/reader/S0App.kt`
- `core/src/test/resources/continuity-cases.json`
- `data/src/test/resources/settlement-valid.json`

## 验收摘要

- JVM：28/28；API 35 App：5/5；API 35 + API 36 Data：各 2/2。
- 小屏横向 + 1.3 字体 + 深色阅读组合：2/2。
- Debug/Release 构建、安全扫描、备份排除、模块边界、项目完整性、规格 lint：全部 PASS。
- Debug SHA-256：`EC0D91CD794FC45EB959AECCE800E0F8B11711BAC6F84990895F75BA709C00CB`。
- Release unsigned SHA-256：`0C8705A16DA17A3B3772E3719FACF174C37FDB6087F44BB2228FBAAB9C1C8BD4`。

## 下一入口

自动进入 `V3-S3`：前台生成任务、进程重建、可恢复操作与阅读设置。目录能力已提前完成，S3 不重复实现。
