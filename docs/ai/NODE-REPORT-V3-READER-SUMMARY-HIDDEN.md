---
document_type: V3_NODE_REPORT
marker: V3-NODE-REPORT
node_id: V3-READER-SUMMARY-HIDDEN
status: COMPLETE
date: 2026-08-20
parent_node: V3-S1
resume_node: V3-S2
requirements: [PV-006, PV-010, PV-016]
search_tags: [settlement-summary-hidden, reader-content-only, metadata-preserved, compose-semantics]
---

# 节点报告：阅读页隐藏结算摘要

## 结论

用户追加约束“结算摘要不要出现在正文里，不要显示”已完成。章节结算摘要继续作为结构化内部状态保存，供后续连续性上下文使用；阅读页不渲染摘要，也不把摘要放入 Compose 无障碍语义树。本变更没有删除数据、没有改变两调用链、没有生成新章节，也没有发起模型请求。

## 实现裁决

- `app/src/main/kotlin/app/zhijuan/reader/S0App.kt`：删除阅读器唯一的 `chapter.summary` 文本渲染入口。
- `app/src/androidTest/kotlin/app/zhijuan/reader/S0ReaderScreenTest.kt`：使用摘要非空的已提交章节，断言正文存在，摘要哨兵和“结算摘要”均不存在。
- `core`、`data`、结算提示、PendingCommit 和章节文件格式不变；`summary` 字段仍是内部权威状态。
- UI/UX 裁决采用“内容优先”：长时间阅读表面只保留章节标题、轻量状态和正文，内部记账信息不混入读者内容。

## 验证证据

| evidence_id | result | evidence |
|---|---|---|
| `RSH-SOURCE-001` | PASS | `app/src/main` 中 `chapter.summary` 与“结算摘要”展示入口均为 0 |
| `RSH-COMPOSE-001` | PASS | API 35：摘要非空但阅读语义树不可见，专项 1/1；完整 App 仪器测试 4/4 |
| `RSH-DATA-001` | PASS | Android 16 现有章节摘要仍非空（144 字符），章节状态仍为 `COMMITTED` |
| `RSH-STATE-001` | PASS | revision=1、nextChapter=2、Committed=1、PendingCommit=0、CompletedCommit=1 |
| `RSH-DEVICE-001` | PASS | Debug 覆盖安装保留数据；阅读页滚动至末尾只显示正文，重复上滑位置稳定，摘要未出现 |
| `RSH-BOOT-001` | PASS | 最终 Debug 覆盖安装后 Android 16 冷启动 `TotalTime=281ms`，FATAL 匹配 0 |
| `RSH-JVM-001` | PASS | ASCII 镜像 Core 8 + Data 12 + App 1 = 21/21，0 failure |
| `RSH-BUILD-001` | PASS | clean Debug/Release 构建成功 |
| `RSH-CALL-BUDGET-001` | PASS | 本节点模型调用 0；普通章节正文/结算预算未改变 |

APK SHA-256：

- Debug：`A499E510AE1A274C8DA19829A018186E86B4C582E426C2F8970033D385A8E76A`
- Release unsigned：`BEE1216636398FFCF80AA487A3E1F405298C8A7D67D3A8E743ACF29A516AEA5D`

## 章节边界说明

当前项目已经按章节存储；现有真实项目只有第 1 章，所以阅读器当前只显示一个章节。每章对应独立 Markdown 正文和元数据，提交后解锁下一章。目录、上一章/下一章、位置记忆属于 `V3-S2` 的 `PV-010` 阅读体验工作，不由本展示补丁提前实现。

## 下一入口

`resume_node=V3-S2`。先建立连续性内核实施计划，再做目录和完整阅读器；不得因为隐藏摘要而删除结构化摘要或增加额外总结调用。
