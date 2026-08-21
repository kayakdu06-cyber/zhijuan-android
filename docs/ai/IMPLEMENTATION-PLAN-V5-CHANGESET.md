---
document_type: V5_IMPLEMENTATION_PLAN
marker: V5-IMPLEMENTATION-PLAN
node_id: V5-CHANGESET-CR01-13
status: COMPLETE
date: 2026-08-21
requirements: [PV-002, PV-004, PV-005, PV-006, PV-010, PV-011, PV-012, PV-016]
change_requests: [ZJ-CR-01, ZJ-CR-02, ZJ-CR-03, ZJ-CR-04, ZJ-CR-05, ZJ-CR-06, ZJ-CR-07, ZJ-CR-08, ZJ-CR-09, ZJ-CR-10, ZJ-CR-11, ZJ-CR-12, ZJ-CR-13]
search_tags: [finish-reason, incomplete-draft, back-stack, reader-paper, immersive-reader, auto-next, genre-presets, tone-presets, writing-skill, quality-card, batch-generation]
---

# 实施计划：V5 用户确认变更集

日期：2026-08-21  
实现 commit/分支：无；项目按约定不使用 Git  
负责人/智能体：Codex `/root`

## 范围

- 需求 ID：`PV-002`、`PV-004`、`PV-005`、`PV-006`、`PV-010`、`PV-011`、`PV-012`、`PV-016`。
- 变更 ID：`ZJ-CR-01` 至 `ZJ-CR-13`；唯一人类权威说明为 `D:/gptuser/projects/zhijuan-change-requests/CHANGE-REQUEST-2026-08-21.md`。
- 本次用户可见结果：截断不会作为完整章提交；返回逐层；阅读器纸色/沉浸/连续阅读；题材与基调预设；编辑式菜单和明确选中态；项目级创作质量卡；一次选择 1/2/3 章并逐章执行。
- 明确不做：新模块、新路由、新 Provider 协议、第三次隐藏模型调用、Room/RAG/向量库、多智能体运行时、富文本、语音、云同步、原始 Skill 运行时执行、批次并行生成。
- 预计有效时长：按 7 个可验证节点推进；每节点红绿测试，不等待中途人工确认。

## 最小上下文

- 已读文档：第三版 `AGENTS.md`、`START_HERE.md`、`docs/02` 至 `07`、`docs/09-change-control.md`、设计系统 `MASTER.md`。
- 已读契约：`constraints.json`、`requirements.json`、`chapter-states.json`、`generation-job.schema.json`、`error-catalog.json`、Prompt 契约。
- 需要保持的不变量：`:app/:core/:data` 恰好三模块；四个顶级路由；一个 Provider 协议；活动 Job 最多一个；每个 Job 一章；普通完整章正文一次 + 结算一次；先草稿后结算；PendingCommit 幂等；API Key 不进入日志/项目/导出。

## 纵向步骤

| # | 跨模块结果 | 预计改动文件 | 测试/证据 | 完成条件 | 状态 |
|---|---|---|---|---|---|
| 1 | 基线与变更门 | `docs/ai/*` | 现有 JVM 测试、完整性、debug 构建 | 修改前基线可重复，变更授权可检索 | completed |
| 2 | Provider 停止原因贯穿协调器、文件草稿和 UI | `app` Provider/协调器、`core` 结果契约、`data` 文件状态、对应测试 | `length/content_filter/resource/network/local-limit` 故障测试 | 非 `stop` 正文保存但不结算、不提交 | completed |
| 3 | 来源感知返回与完整阅读体验 | `S0App.kt`、`S0Theme.kt`、Reader 测试 | Compose UI、位置恢复、误触和状态资格测试 | 纸色 `#EEECDF`；无重复三点；中央轻触；已提交下一章连续阅读 | completed |
| 4 | 创建与项目选择体验 | `S5TextProjectUi.kt`、`S6EditorialComponents.kt`、上下文构建、测试 | 预设/自定义/无障碍与题材注入测试 | 题材/基调可选且可编辑，选中两种视觉线索，菜单一致 | completed |
| 5 | 创作 Skill 文件闭环 | `core` 质量卡模型、`data` 项目/归档、`app` SAF/UI/提示构建、测试 | 非法输入、提示注入、哈希、重启、归档往返 | 原文件不运行；唯一质量卡进入正文；诊断可证明版本 | completed |
| 6 | 1/2/3 章用户批次 | `app` 生成控制与 UI、测试 | 单活动任务、逐章调用计数、失败/取消/进程死亡 | 每章独立两调用与提交；失败即停；默认 1 章 | completed |
| 7 | 发布回归与真机 | 报告、状态、索引、APK | JVM、仪器、完整性、模块边界、debug/release、真机 | 证据齐全，APK 安装，节点报告可检索 | completed |

## 风险与故障路径

- 将注入的失败：`finish_reason=length/content_filter/insufficient_system_resource`、SSE 未完成、正文上限、结算失败、批次中断、Skill 损坏/超限/归档篡改。
- 修改前基线：JVM 测试与 debug 构建通过；完整性脚本发现三个既有状态错误——把 `v4Screenshots` 目录当作普通文件、把所有历史节点标记固定写死为 `V3-NODE-REPORT`、开发中重建 APK 后仍要求等于上个完成节点哈希。校验器已分别改为“状态报告引用允许文件或目录”“节点标记按版本匹配”“仅完成节点锁定 APK 哈希”，不放宽路径存在性、节点 ID 或最终制品检查。
- 恢复后的权威状态：正文文件和 Job 检查点优先；未完整正文不可推进 PendingCommit；批次只在当前进程内保存用户剩余意图，进程死亡后不自动续启下一章。
- 是否涉及额外模型调用：批次按章增加用户明确请求的正常调用；每章仍恰好两次。Skill 导入、校验、哈希和机械检查均为本地零调用。
- 是否新增依赖/路由/模块：否。
- 是否新增持久化实体：是，项目内 `writing-skill/` 三文件；已由用户明确批准，生命周期与项目绑定，并纳入导入导出白名单、哈希和故障测试。
- 规格偏差：`ZJ-CR-05` 从停车场进入实现；只支持用户显式发起的 1/2/3 章顺序批次，不并行、不跨进程自动续启、不改变每个 Job 一章。

## 完成核对

- [x] 关联测试通过
- [x] 需求—测试追溯更新
- [x] 无密钥/正文敏感日志
- [x] 进程重建路径验证
- [x] 三模块、四路由、一个 Provider 与两调用守卫通过
- [x] 有可复核证据而非口头“完成”
