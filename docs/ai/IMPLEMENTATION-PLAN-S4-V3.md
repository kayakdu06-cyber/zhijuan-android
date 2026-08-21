# 实施计划：V3-S4 故障与实机收敛

检索标记：`V3-S4-IMPLEMENTATION-PLAN`  
日期：2026-08-20  
版本控制：NONE  
负责人/智能体：Codex

## 范围与门禁

- 目标：`RG-01` 至 `RG-10`；重点是 `RG-04` 十种故障、`RG-05` 五章真机、`RG-07` 导出导入、`RG-09` 无障碍。
- 不变量：3 modules / 4 routes / 1 Provider protocol / one active job / one chapter / normal 2 calls / no automatic resend。
- 不把 mock、单章或构建成功冒充五章真实门禁。
- 门禁审计发现 `PV-002/004/010/011` 的产品表面不完整时，先按既有 MUST 需求补齐，不把它们留到 S5 当作新增能力。

## 纵向步骤

| # | 结果 | 状态 | 证据入口 |
|---|---|---|---|
| 1 | 建立十种故障注入矩阵并补齐文件恢复缺口 | complete | `TEST-EVIDENCE-S4-V3.md#十种故障注入` |
| 2 | SAF 单项目 ZIP 导出/导入及恶意 ZIP 拒绝 | complete | `S4ProjectArchiveTest`、真机 round-trip |
| 3 | TalkBack、字体、主题、安全区与通知门禁 | complete | `TEST-EVIDENCE-S4-V3.md#阅读无障碍与隐私` |
| 4 | 真机连续五章剧本（第 3 章强杀、第 4 章坏结算只重试） | complete | `TEST-EVIDENCE-S4-V3.md#五章真机` |
| 5 | 补齐五字段建书、明确计划刷新、多项目书库和逐章位置恢复 | complete | `TEST-EVIDENCE-S5-V3.md` |
| 6 | clean build、release 安装、密钥/许可/追溯和节点报告 | complete | `TEST-EVIDENCE-S4-V3.md#发布门禁` |
