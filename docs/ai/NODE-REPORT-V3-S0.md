---
document_type: V3_NODE_REPORT
marker: V3-NODE-REPORT
node_id: V3-S0
status: COMPLETE
date: 2026-08-17
historical_implementation_snapshot: 8086278
historical_evidence_snapshot: b5097c2
requirements: [PV-002, PV-003, PV-005, PV-006, PV-008, PV-010, PV-013, PV-017]
next_node: V3-S1
search_tags: [three-modules, fake-provider, readable-draft, pending-commit, restart-recovery]
---

# 节点报告：V3-S0 工程与假数据纵切片

## 结果

- Gradle 仅包含 `:app`、`:core`、`:data`；2026-08-20 后项目内不存在旧模块源码。
- 创建本地示例项目后，Fake Provider 严格执行正文一次、固定结算一次。
- 正文先写为 Markdown 可读草稿，随后通过 PendingCommit 幂等提交状态、计划、事件和章节元数据。
- 新 Repository 实例可恢复同一项目、正文、revision 和 COMMITTED 状态。
- 四个顶级路由与三个底部导航入口已建立；复用现有“织卷”品牌资源。

## 关键实现入口

- `core/src/main/kotlin/app/zhijuan/core/s0/S0Domain.kt`
- `core/src/main/kotlin/app/zhijuan/core/s0/S0GenerationCoordinator.kt`
- `data/src/main/kotlin/app/zhijuan/data/s0/FileS0NovelRepository.kt`
- `app/src/main/kotlin/app/zhijuan/reader/S0App.kt`

## 证据

- 详细测试、命令、APK 哈希：`docs/ai/TEST-EVIDENCE-S0-V3.md`
- 实施范围与冲突：`docs/ai/IMPLEMENTATION-PLAN-S0-V3.md`
- 模块门禁：`MODULE_COUNT=3`、`TOP_LEVEL_ROUTES=4`、`PROVIDER_PROTOCOLS=1`。

## 保留限制

- S0 使用本地 Fake Provider；没有真实 HTTP/SSE、Keystore、前台服务或导入导出。
- 中文项目路径下 JVM worker 使用项目既有 ASCII mirror 脚本执行。

## 下一入口

执行 `V3-S1`：先读 `docs/ai/IMPLEMENTATION-PLAN-S1-V3.md`，只接 Provider、安全凭据、SSE、取消和错误映射。
