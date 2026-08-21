---
document_type: V3_NODE_REPORT
marker: V3-NODE-REPORT
node_id: V3-S1
status: COMPLETE
local_engineering_status: COMPLETE_AT_HISTORICAL_SNAPSHOT_2a7f8e4
verified_snapshot: 2a7f8e4
resume_node: V3-S2
blocked_gate: NONE
next_product_node: V3-S2
requirements: [PV-001, PV-005, PV-006, PV-014, PV-015, PV-017]
search_tags: [provider, openai-compatible, android-keystore, aes-gcm, sse, cancellation, safe-errors, pending-commit]
---

# 节点报告：V3-S1 Provider

## 结论

本地工程实现、故障测试、边界与安全扫描均已通过；真机真实连接与一次普通章节也已通过。最终成功动作恰好一次正文、一次结算，随后完成可读草稿、PendingCommit、阅读与杀进程恢复。历史 SSE 字节误判和默认思考导致的空正文均保留在真实连接报告中。

## 已实现

- Core：在唯一 `S0TextGenerationProvider` 协议上扩展连接摘要、测试保存与取消；URL、稳定错误码和用户动作均为确定性合同。
- Data：非秘密 Provider 设置原子落盘；API Key 由 AndroidKeyStore AES-GCM 保护；OpenAI-compatible 正文 SSE、结算 JSON、超时、响应/字符上限、取消与安全诊断已接通。
- App：首次未配置时进入设置页；默认只需 DeepSeek API Key，其余使用推荐预设；Endpoint、模型、超时与上限仍在折叠兼容设置中可编辑；已保存 key 不回显、不进入可保存 UI state；配置前禁止启动生成。
- 纵切片：真实 Provider adapter 接回 S0 的草稿优先、固定结算、PendingCommit 幂等提交和重启恢复路径，没有新增第三次模型调用。
- 工具链：修正 emulator/AVD 环境变量到 `D:\deepseekuser`；修复 debug manifest 遗留组件造成的冷启动崩溃。

## 硬边界复核

`modules=3` `routes=4` `provider_protocols=1` `active_generation_jobs=1`
`chapters_per_job=1` `normal_model_calls=2` `Room=absent` `RAG=absent`

## 需求—实现—证据

| requirement_id | implementation | evidence_id | state |
|---|---|---|---|
| `PV-001` | Provider 设置与连接测试 | `S1-JVM-001`, `S1-ANDROID-001`, `RC-CONNECTION-001` | PASS_REAL |
| `PV-005` | SSE 增量正文 | `S1-JVM-001`, `RC-REAL-001` | PASS_REAL |
| `PV-006` | 非流式结构化结算 | `S1-JVM-001`, `RC-REAL-001` | PASS_REAL |
| `PV-014` | Keystore 凭据隔离、备份与日志边界 | `S1-ANDROID-001`, `S1-SECURITY-001` | PASS_LOCAL |
| `PV-015` | 稳定错误码、用户消息与可行动建议 | `S1-JVM-001`, `S1-SECURITY-001` | PASS_LOCAL |
| `PV-017` | 正文章节恰好一次、结算恰好一次 | `S1-JVM-001`, `RC-REAL-001` | PASS_ONE_REAL_CHAPTER |

详细证据见 `docs/ai/TEST-EVIDENCE-S1-V3.md`。

`requestId` 取消是本切片已验证的 Provider 合同行为，但不再错误地归到 `PV-014`。真实 Provider 的五章调用计数仍属于外部门禁，不由 MockWebServer 结果替代。

## 本地加固结果

以下两个测试文件的新增故障场景均已通过：

- `data/src/test/kotlin/app/zhijuan/data/s0/provider/OpenAiCompatibleS1ProviderTest.kt`
- `data/src/androidTest/kotlin/app/zhijuan/data/s0/provider/AndroidKeystoreS1SecretStoreTest.kt`

Data JVM 为 11/11，API 35 Keystore 为 2/2；详细计数见测试证据。

## resume

1. 下一节点为 `V3-S2`；先读取连续性最小上下文并建立独立实施计划。
2. S1 的真实证据在 `docs/ai/NODE-REPORT-V3-S1-REAL-CONNECTION.md`，不得删除失败历史或原始密钥边界。
3. `PV-017/PV-018/RG-05` 的连续五章发布门禁仍属于 S4，不能由本次单章通过替代。
