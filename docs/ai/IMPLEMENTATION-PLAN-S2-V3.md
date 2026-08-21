# 实施计划：V3-S2 连续性内核与章节目录

日期：2026-08-20  
实现 commit/分支：NONE（项目不使用版本控制）  
负责人/智能体：Codex

## 范围

- 需求 ID：`PV-004`、`PV-007`、`PV-010`、`PV-016`、`PV-017`
- 本次用户可见结果：阅读器可打开目录、切换已有章节并使用上一章/下一章；内部生成任务包含受限连续性上下文，本地规则拒绝九类硬冲突。
- 明确不做：不自动生成多章、不新增模型调用、不实现复杂大纲编辑器、不删除内部结算摘要、不引入新模块/路由/数据库。
- 预计有效时长：1.5–2.0 个有效工作日。

## 最小上下文

- 已读文档：`docs/02-interaction.md`、`docs/03-architecture.md`、`docs/04-generation-continuity.md`、`docs/06-prompt-contracts.md`、Android V3 `MASTER.md`。
- 已读契约：`constraints.json`、`requirements.json`、`routes.json`、`chapter-states.json`、`chapter-task.schema.json`、`settlement.schema.json`、`story-state.schema.json`、`plan-window.schema.json`。
- 需要保持的不变量：3 模块、4 顶级路由、1 Provider 协议、一次一章、普通章节恰好正文/结算各一次、草稿先保存、PendingCommit 幂等、摘要内部保存但阅读页隐藏。

## 纵向步骤

| # | 跨模块结果 | 预计改动文件 | 测试/证据 | 完成条件 | 状态 |
|---|---|---|---|---|---|
| 1 | 阅读页从 Repository 快照选择任意已保存章节 | `S0App.kt`、`S0ReaderScreenTest.kt` | API 35 Compose | 目录为 ModalBottomSheet；当前章有文字标识；上一/下一章可达 | complete |
| 2 | Core 构建有预算的 ChapterTask | `S0Domain.kt`、`S2Continuity.kt`、Core tests | ContextBuilder 单元测试 | 最近摘要最多 5、上一章尾部受限、无全书正文 | complete |
| 3 | Core 以规格夹具执行连续性规则 | `S2Continuity.kt`、`continuity-cases.json`、Core tests | 表驱动 9 案例 | 每个案例精确返回预期硬错误，不调用模型 | complete |
| 4 | 生成协调器拒绝序号/一次性事件冲突并保持草稿 | `S0GenerationCoordinator.kt`、跨模块 tests | Core + App→Core→Data | 冲突不写 PendingCommit、不推进 revision，正文仍可读 | complete |
| 5 | 节点验收与状态推进 | tests/build/scripts/docs | JVM、仪器、实机、Debug/Release、安全门禁 | 节点报告 COMPLETE，自动进入下一节点 | complete |

## 风险与故障路径

- 将注入的失败：重复一次性事件、章节跳号、死亡角色在场、未知知识、唯一物品双持、无事件突变、目录空/单章/多章。
- 恢复后的权威状态：连续性硬冲突仅保留 `READABLE_DRAFT/NEEDS_REVIEW`；revision、计划和已提交事件不变。
- 是否涉及额外模型调用：否。
- 是否新增依赖/路由/模块/持久化实体：否；Core 测试复用项目已有 `kotlinx-serialization-json` 解析权威夹具。

## 完成核对

- [x] 关联测试通过
- [x] 需求—测试追溯更新
- [x] 无密钥/正文敏感日志
- [x] 进程重建路径验证
- [x] `python scripts/docs_lint.py` 通过
- [x] 有可复核证据而非口头“完成”
