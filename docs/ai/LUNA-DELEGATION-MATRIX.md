---
document_type: MODEL_DELEGATION_MATRIX
marker: V3-LUNA-DELEGATION
status: ACTIVE
date: 2026-08-20
owner_model: primary
delegate_model: gpt-5.6-luna
search_tags: [token-saving, delegation, luna, quality-guardrail, task-routing]
---

# Luna 委派矩阵

## 目标

把高上下文、高重复、结果可机械验证的工作交给 Luna；架构、安全、恢复语义、破坏性操作和最终验收保留给主模型。预计可转移约 35%–50% 的例行上下文与日志处理量；这是工作量估计，不是计费承诺。

## 路由表

| work_type | token_pressure | route | quality_guardrail |
|---|---:|---|---|
| 规格 context pack 摘录、requirement/gate 对照表 | high | `LUNA_SAFE` | 主模型先限定文件；Luna只引用原文 ID，主模型复核缺项 |
| 文件清单、依赖别名、旧路径、模块边界只读扫描 | high | `LUNA_SAFE` | 只读命令；输出绝对路径和计数；不执行删除 |
| JVM/仪器/build 执行及 XML 计数、APK 哈希归档 | high | `LUNA_SAFE` | 使用固定命令；原始退出码和失败数必须保留 |
| 节点报告、测试证据、机器可读状态更新 | high | `LUNA_SAFE` | 只从已给证据填充；未知项写 `UNVERIFIED` |
| 已有合同下的表驱动测试/fixture 扩充 | medium-high | `LUNA_CONDITIONAL` | 文件白名单、断言清单、禁止生产代码；主模型审查并重跑 |
| 明确映射的源码路径搬迁或格式化 | medium | `LUNA_CONDITIONAL` | 逐文件哈希/编译验证；不得自行判断删除范围 |
| 静态安全扫描、备份规则检查、敏感词结果去重 | medium | `LUNA_SAFE` | 不读取或接收真实 secret；发现即停并只报路径 |
| Compose 可访问性清单初审 | medium | `LUNA_CONDITIONAL` | Luna只列证据；视觉取舍和最终验收由主模型完成 |
| Provider/Keystore/取消/超时/重试安全设计 | high | `PRIMARY_ONLY` | 涉及凭据、网络副作用和错误语义 |
| 草稿优先、PendingCommit、幂等恢复、状态机设计 | very-high | `PRIMARY_ONLY` | 数据损坏风险与跨模块不变量 |
| 架构取舍、模块边界变化、真实故障根因分析 | very-high | `PRIMARY_ONLY` | 需要全局推理与不确定性判断 |
| 删除、覆盖、迁移、版本控制清理 | medium | `PRIMARY_ONLY` | 不可逆动作必须由主模型确认精确目标 |
| 真实 Provider 请求、API Key 操作 | high | `USER_LOCAL_ONLY` | Key 只在 App 内输入；任何模型都不接触明文 |
| 节点 `COMPLETE`、发布门禁最终裁决 | medium | `PRIMARY_ONLY` | 主模型核对全部证据与外部门禁 |

## 当前节点可交给 Luna 的部分

`V3-S1-REAL-CONNECTION` 中，Luna可以整理真机步骤、收集已脱敏的状态码/耗时/请求 ID 哈希并更新证据草稿；用户负责在 App 内输入凭据，主模型负责判断门禁是否通过。

后续节点中，Luna优先承担：最小规格包摘录、测试矩阵生成、命令执行、失败日志压缩、报告与哈希更新。主模型负责领域合同、代码实现、跨模块审查和最终状态。

## 委派包最低字段

每次 Luna 任务必须包含：`node_id`、文件白名单、权威规格路径、硬边界、允许动作、禁止动作、验证命令、预期证据、停止条件。缺一项则不委派生产改动。

## 升级条件

Luna 遇到下列任一情况立即停止并交回主模型：需要新增抽象/依赖、测试与规格冲突、涉及凭据或真实网络、可能破坏数据、需要删除/覆盖、无法确定 PASS/FAIL、连续两次修复仍失败。
