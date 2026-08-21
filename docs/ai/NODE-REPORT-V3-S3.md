---
document_type: V3_NODE_REPORT
marker: V3-NODE-REPORT
node_id: V3-S3
status: COMPLETE
date: 2026-08-20
requirements: [PV-005, PV-006, PV-008, PV-009, PV-010, PV-011, PV-012, PV-013, PV-016, PV-017]
search_tags: [foreground-generation, recovery, reader-settings, process-recreation]
---

# 节点报告：V3-S3 可用界面、前台任务与恢复

状态：`COMPLETE`。实施入口：`docs/ai/IMPLEMENTATION-PLAN-S3-V3.md`；证据：`docs/ai/TEST-EVIDENCE-S3-V3.md`。

硬边界：`modules=3` `routes=4` `provider_protocols=1` `active_jobs<=1` `normal_calls=2` `automatic_retry=false`。

## 节点结果

- 单章生成已从 Activity coroutine 迁入 `dataSync` 前台服务，持续通知显示书名、章节、阶段，并提供返回应用/停止；服务内只有一个活动 Job，另有 20 分钟应用级上限和 Android 15+ `onTimeout()` 处理。
- Android 13+ 在用户第一次点击生成时按上下文请求通知权限；拒绝不阻止核心生成，应用内继续显示阶段与停止入口。
- `jobs/active.json` 严格按 1.0 任务 Schema 的有限字段原子保存，不保存密钥、Prompt 或正文；任务失败/进程死亡不会自动重发 POST。
- 启动恢复审计先幂等完成 PendingCommit，再区分只重试结算、确认重发正文、重试正文或查看草稿。
- 已有 `READABLE_DRAFT/NEEDS_REVIEW` 只显示“只重试结算”；实测恢复调用为正文 0 次、结算 1 次。
- 阅读设置增加字号 16/18/20/22sp、行距 26/30/34/38sp、跟随系统/浅色/深色，使用单独本机偏好文件持久化；所有选择目标至少 48dp 且有已选择语义。
- S2 的目录、上下章和摘要隐藏继续保持。

## 主要实现文件

- `app/src/main/kotlin/app/zhijuan/reader/S3GenerationForegroundService.kt`
- `app/src/main/kotlin/app/zhijuan/reader/S3ReaderPreferences.kt`
- `core/src/main/kotlin/app/zhijuan/core/s0/S3GenerationJob.kt`
- `data/src/main/kotlin/app/zhijuan/data/s0/FileS3GenerationJobStore.kt`
- `app/src/main/AndroidManifest.xml`

## 下一入口

自动进入 `V3-S4`：十种故障注入、导入导出、TalkBack/真机五章与发布门禁收敛。
