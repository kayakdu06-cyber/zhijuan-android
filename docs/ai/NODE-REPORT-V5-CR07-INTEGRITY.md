---
document_type: V5_NODE_REPORT
marker: V5-NODE-REPORT
node_id: V5-CR07-INTEGRITY
status: COMPLETE
date: 2026-08-21
requirements: [PV-004, PV-005, PV-006, PV-012]
change_requests: [ZJ-CR-07]
search_tags: [finish-reason, length, content-filter, insufficient-system-resource, incomplete-draft, paused, no-settlement]
---

# 节点报告：截断正文完整性

## 可见结果

- 正文 Provider 只有收到 `[DONE]`、非空正文且 `finish_reason=stop` 时才返回完整成功。
- `length`、`content_filter`、`insufficient_system_resource`、未知停止原因、SSE 结果未知和本地字符上限均保留已收到片段，但章节状态写为 `PAUSED`，同时保存脱敏 `incompleteReason`。
- 未完成草稿不会进入结构化结算、PendingCommit 或故事 revision；生成页显示具体原因，只提供用户明确触发的“重新生成本章”，不显示“只重试结算”。
- 用户明确重新生成时可覆盖同章 `PAUSED` 草稿；新请求失败前旧片段仍在，完整成功后才按正常两调用提交。
- 正常中文正文输出预算从自限 4000 token 调整为最多 8192 token，同时仍以 6000 字符为推荐正文上限并受本地字符硬上限保护，降低再次因请求 token 上限截断的概率。

## 停止原因映射

| Provider/本地原因 | 本地错误 | 章节状态 | 结算 |
|---|---|---|---|
| `stop` | 无 | `READABLE_DRAFT → COMMITTED` | 是 |
| `length` | `PROSE_TRUNCATED_LENGTH` | `PAUSED` | 否 |
| `content_filter` | `PROSE_CONTENT_FILTERED` | `PAUSED` | 否 |
| `insufficient_system_resource` | `PROSE_RESOURCE_INTERRUPTED` | `PAUSED` | 否 |
| 缺失/其他 | `PROSE_FINISH_REASON_UNKNOWN` | `PAUSED` | 否 |
| 本地字符/流上限 | `PROSE_LIMIT_EXCEEDED` | `PAUSED` | 否 |
| SSE 未完成且有片段 | `REQUEST_OUTCOME_UNKNOWN` | `PAUSED` | 否 |

Provider 诊断保留错误码、停止原因、输入/输出 token 数、响应字节和请求 ID 哈希；不记录 API Key 或正文。

## 主要改动文件

- `core/src/main/kotlin/app/zhijuan/core/s0/S0Domain.kt`
- `core/src/main/kotlin/app/zhijuan/core/s0/S0GenerationCoordinator.kt`
- `core/src/main/kotlin/app/zhijuan/core/s0/S1ProviderContract.kt`
- `core/src/main/kotlin/app/zhijuan/core/s0/S3GenerationJob.kt`
- `data/src/main/kotlin/app/zhijuan/data/s0/FileS0NovelRepository.kt`
- `data/src/main/kotlin/app/zhijuan/data/s0/provider/OpenAiCompatibleS1Provider.kt`
- `app/src/main/kotlin/app/zhijuan/reader/S0App.kt`
- `app/src/main/kotlin/app/zhijuan/reader/S3GenerationForegroundService.kt`
- 对应 core/data/Compose 测试。

## 证据

- JVM：55 项，0 失败，0 跳过；覆盖四种 Provider 停止原因、诊断字段、PAUSED 重启恢复、禁止结算和显式重新生成。
- `:app:assembleDebugAndroidTest`：通过；新增未完成草稿 UI 行为测试已编译。
- `:app:assembleDebug`：通过。
- 项目完整性：`PROJECT_INTEGRITY_CHECK_OK`；仍为三模块、四路由、一个 Provider 协议。
- 官方语义核对：DeepSeek Chat Completion 文档把 `stop` 定义为自然停止，把 `length` 定义为达到上限且内容可能截断，并列出 `content_filter` 与 `insufficient_system_resource`。

## 安全边界

- 没有隐藏第三次调用；异常后的重新生成必须由用户明确点击。
- 不尝试绕过 Provider 内容策略；`content_filter` 只保存本地片段并提示改为符合 Provider 规则的表达。
- 已经在旧版本中提交的历史章节不自动降级或重写，避免擅自修改现有正文与 revision。

## 下一入口

继续 `V5-READER-NAV`：实现来源感知返回、阅读纸色、中央轻触工具栏和已提交章节连续阅读。
