---
document_type: V3_NODE_REPORT
marker: V3-NODE-REPORT
node_id: V3-S1-REAL-CONNECTION
status: COMPLETE
date: 2026-08-20
parent_node: V3-S1
requirements: [PV-001, PV-005, PV-006, PV-014, PV-015, PV-017]
search_tags: [real-device, deepseek, connection-pass, prose-limit, no-auto-retry, output-budget]
---

# 节点报告：V3-S1 真机真实连接

## 结论

真实 DeepSeek 连接测试与一章真实普通章节均已通过，本节点与父节点 `V3-S1` 状态为 `COMPLETE`。

四次正文请求均返回 HTTP 200，但旧实现把“正文字符上限”换算成约 161KB 的原始 SSE 上限。四次原始流都刚超过该阈值，因而被错误映射成 `PROSE_LIMIT_EXCEEDED`；现有诊断不能证明正文自身超过 12000 字符。没有执行结算、没有写入章节、没有推进 revision。每轮连续两次失败后均停止，不自动重试。

分离 SSE/正文上限后，一次请求返回 1.21MB 推理流但最终 `content` 为空，映射为 `PROSE_EMPTY`。官方文档确认 DeepSeek V4 默认开启思考模式；仅对 `deepseek-v4-pro/flash` 明确发送 `thinking.type=disabled` 后，单次用户授权动作得到 `PROSE=1`、`SETTLEMENT=1`，随后完成可读草稿、PendingCommit、阅读与杀进程恢复。

## 脱敏时间线

| evidence_id | stage | result | duration_ms | response_bytes | http_status | request_id_hash |
|---|---|---:|---:|---:|---:|---|
| `RC-CONNECTION-001` | `CONNECTION_TEST` | PASS | 1203 | 531 | 200 | `baddc25b83ec2905` |
| `RC-PROSE-001` | `PROSE` | `PROSE_LIMIT_EXCEEDED` | 12518 | 162085 | 200 | `47faa66b9b86baad` |
| `RC-PROSE-002` | `PROSE` | `PROSE_LIMIT_EXCEEDED` | 9975 | 162174 | 200 | `4b09eab9deb657fd` |
| `RC-PROSE-003` | `PROSE` | `PROSE_LIMIT_EXCEEDED` | 12100 | 162730 | 200 | `2415bf1f130f7c03` |
| `RC-PROSE-004` | `PROSE` | `PROSE_LIMIT_EXCEEDED` | 12999 | 161652 | 200 | `482ac38567ae3f46` |
| `RC-PROSE-005` | `PROSE` | `PROSE_EMPTY` | 82413 | 1210765 | 200 | `d7a3e297a69c4848` |
| `RC-PROSE-006` | `PROSE` | PASS | 65732 | 693516 | 200 | `5710e26a0371f0e5` |
| `RC-SETTLEMENT-001` | `SETTLEMENT` | PASS | 5296 | 1233 | 200 | `5df7e0c671661991` |

第一轮第二次点击发生在第一条失败诊断尚未落盘、界面已被误判为空闲之后。输出预算修复后的用户重试又产生两条相同错误，证明即时失败原因是原始 SSE 字节阈值。历史诊断总计 `PROSE=6`、`SETTLEMENT=1`；最终成功动作自身恰好 `PROSE=1`、`SETTLEMENT=1`，没有隐藏调用。

## 修复

- 正文请求的推荐目标改为 2500–6000 个中文字符；自定义正文上限较小时按上限收缩。
- 默认输出预算从 8192 token 收紧到最多 4000 token；1000 字符配置只允许 666 token。
- 12000 字符的 Provider 安全上限保持不变，不以放宽上限掩盖问题。
- 超限时，把超限前已经完整接收的分片保存为 `READABLE_DRAFT`，但不调用结算、不推进 revision。
- 原始 SSE 流改用独立的 2MiB 硬上限；推理字段和事件封装不再占用正文字符额度，正文仍按组装后的 `content` 独立限制 12000 字符。
- DeepSeek V4 默认思考会消耗正文输出预算；连接、正文和结算请求仅在模型为 `deepseek-v4-pro/flash` 时显式发送 `thinking.type=disabled`，其他兼容模型不添加该字段。
- 生成页不再显示误导性的“S0 固定结算”，改为“单章安全生成 / 结构化结算”。

## 修复证据

| evidence_id | status | evidence |
|---|---|---|
| `RC-JVM-001` | PASS | Core 8 + Data 12 + App 1 = 21/21；0 failure、0 error |
| `RC-BUDGET-001` | PASS | MockWebServer 断言字符目标和派生 token 预算进入正文请求 |
| `RC-DRAFT-001` | PASS | 超限前完整分片落为可读草稿，结算调用为 0，revision 保持 0 |
| `RC-SSE-001` | PASS | 超过 100KB 的模拟推理流不消耗正文字符额度，正文完成且 `[DONE]` 被确认 |
| `RC-THINKING-001` | PASS | MockWebServer 断言 DeepSeek V4 的连接、正文、结算均关闭思考；其他模型保持通用请求 |
| `RC-BUILD-001` | PASS | clean 后 Debug/Release 构建成功 |
| `RC-SECURITY-001` | PASS | 源码、Debug/Release APK 与备份排除通过 |
| `RC-REAL-001` | PASS | 最终单次动作：PROSE 1 次、SETTLEMENT 1 次，均 HTTP 200；input/output tokens 分别为 78/2511 与 2568/211 |
| `RC-COMMIT-001` | PASS | revision=1、nextChapter=2、chapter=COMMITTED、正文 9862 bytes、摘要非空、Pending=0、Completed=1 |
| `RC-READER-001` | PASS | 阅读路由正文非空并可滚动至末尾；结算摘要保留在元数据中但不显示 |
| `RC-RECOVERY-001` | PASS | Android 16 杀进程后冷启动 383ms；配置摘要、项目、已提交 1 章与下一章入口恢复，FATAL=0 |

APK SHA-256：

- Debug：`A499E510AE1A274C8DA19829A018186E86B4C582E426C2F8970033D385A8E76A`
- Release unsigned：`BEE1216636398FFCF80AA487A3E1F405298C8A7D67D3A8E743ACF29A516AEA5D`

阅读展示变更的独立证据见 `docs/ai/NODE-REPORT-V3-READER-SUMMARY-HIDDEN.md`。

## 硬边界

`modules=3` `routes=4` `provider_protocols=1` `active_jobs=1`
`historical_prose_calls=6` `historical_settlement_calls=1` `automatic_retry=0`
`successful_action_prose_calls=1` `successful_action_settlement_calls=1`
`API-key-local-only` `no-response-body-in-report`

## 下一入口

`V3-S1` 已关闭。下一节点是 `V3-S2` 连续性内核：ContextBuilder、章节任务、结算 Schema、八规则、事件去重和计划窗口；真实失败历史继续保留，不作为 S2 的模拟通过证据。
