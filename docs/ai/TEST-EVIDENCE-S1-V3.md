---
document_type: V3_NODE_TEST_EVIDENCE
marker: V3-NODE-REPORT
node_id: V3-S1
status: COMPLETE
verified_snapshot: no-vcs-2026-08-20-reader-summary-hidden
resume_node: V3-S2
requirements: [PV-001, PV-005, PV-006, PV-014, PV-015, PV-017]
search_tags: [provider, keystore, sse, cancellation, safe-errors, secret-scan, real-two-call]
---

# V3-S1 测试证据

## 最终结论

Provider 配置、AndroidKeyStore、流式正文、结构化结算、错误映射、真实单章两调用、可恢复提交和重启恢复均已通过。`V3-S1` 与真实连接子节点均为 `COMPLETE`；未完成的连续五章发布硬门禁属于后续 `PV-018`，不回退 S1 状态。

## 最终证据矩阵

| evidence_id | result | evidence |
|---|---|---|
| `S1-JVM-001` | PASS | ASCII 镜像 Core 8 + Data 12 + App 1 = 21/21，0 failure |
| `S1-ANDROID-DATA-001` | PASS | API 35 Keystore 2/2；Android 16 真机 Keystore 2/2 |
| `S1-ANDROID-APP-001` | PASS | API 35 完整 App 仪器测试 4/4，其中快速设置 3、阅读摘要隐藏 1 |
| `S1-CONNECTION-001` | PASS | 真实 DeepSeek 连接 HTTP 200，1203ms |
| `S1-REAL-CALLS-001` | PASS | 最终单次普通章节动作 PROSE=1、SETTLEMENT=1，均 HTTP 200，无自动重试 |
| `S1-COMMIT-001` | PASS | revision=1、nextChapter=2、COMMITTED=1、Pending=0、Completed=1 |
| `S1-RECOVERY-001` | PASS | Android 16 杀进程后项目、Provider 配置、章节和下一章入口恢复，FATAL=0 |
| `S1-READER-001` | PASS | 正文可读；内部结算摘要保留在元数据但不进入阅读视觉/语义树 |
| `S1-BUILD-001` | PASS | 离线 clean Debug/Release 成功 |
| `S1-BOUNDARY-001` | PASS | 3 Gradle 模块、4 顶级路由、1 Provider 协议、依赖无环 |
| `S1-SECURITY-001` | PASS | 源码、Debug/Release APK 密钥扫描、备份排除和明文网络禁用通过 |

## 需求追踪

| requirement_id | 证据 |
|---|---|
| `PV-001` | 推荐 DeepSeek 仅填 Key；兼容服务配置折叠；真实连接保存通过 |
| `PV-005` | SSE 分片、心跳、独立原始流上限、可读草稿优先保存及真实正文通过 |
| `PV-006` | 结构化结算、坏 JSON 保留草稿、真实结算和 PendingCommit 通过 |
| `PV-014` | Keystore、备份排除、诊断脱敏和源码/APK 扫描通过 |
| `PV-015` | 401/404/429/5xx、坏 Content-Type、缺失 `[DONE]`、空正文等稳定错误通过 |
| `PV-017` | 单元/集成测试与最终真实动作均证明普通章节恰好正文一次、结算一次 |

## 故障与修复证据

- 404、429、503、坏 Content-Type、SSE 缺失 `[DONE]`、正文字符超限、非 JSON 结算和 Keystore 条目丢失均有测试。
- 历史四次 HTTP 200 正文因旧原始 SSE 阈值被误判超限；分离 2MiB 原始流上限和 12000 字符正文上限后修复。
- DeepSeek V4 默认思考导致一次只有推理流、正文为空；仅对 `deepseek-v4-pro/flash` 发送 `thinking.type=disabled` 后，真实单章通过。其他兼容模型不添加厂商字段。
- 历史失败没有自动重试，没有结算、没有提交；最终成功动作自身仍严格为 1+1。

## APK

- Debug SHA-256：`A499E510AE1A274C8DA19829A018186E86B4C582E426C2F8970033D385A8E76A`
- Release unsigned SHA-256：`BEE1216636398FFCF80AA487A3E1F405298C8A7D67D3A8E743ACF29A516AEA5D`

## 剩余发布门禁

- `V3-S2`：连续性内核和完整阅读器。
- `PV-018`：真实设备连续五章及故障恢复场景。

详细真实诊断见 `docs/ai/NODE-REPORT-V3-S1-REAL-CONNECTION.md`；阅读摘要隐藏见 `docs/ai/NODE-REPORT-V3-READER-SUMMARY-HIDDEN.md`。
