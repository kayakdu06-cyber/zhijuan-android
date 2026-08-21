---
document_type: V3_NODE_REPORT
marker: V3-NODE-REPORT
node_id: V3-INTEGRITY
status: COMPLETE
date: 2026-08-20
scope: engineering-asset-integrity
product_completeness: PARTIAL
current_product_node: V3-S1-REAL-CONNECTION
search_tags: [integrity, no-git, three-modules, source-layout, brand-hash, completeness-audit]
---

# 节点报告：V3 新项目完整性门禁

## 结论

`engineering_asset_integrity=PASS`：旧织卷清理后，当前 `D:/deepseekuser/projects/织卷1` 仍具备继续开发 S0/S1 的完整工程资产，未发现清理造成的源码、构建入口、测试、品牌或节点状态缺失。

`release_product_completeness=PARTIAL`：这不表示 18 项产品需求或 10 项发布门禁已经全部实现。当前仍停在 `V3-S1-REAL-CONNECTION`；后续连续性内核、前台生成任务、完整阅读/书库、导入导出、五章实机与无障碍验收仍按后续节点实施。

## 自动门禁

入口：`scripts/verify-project-integrity.ps1`

门禁验证：

- 项目根固定为 `D:/deepseekuser/projects/织卷1`，产品名为“织卷”。
- `.git`、`.gitnexus`、`.gitignore` 和已裁决旧目录不存在。
- Gradle 模块恰好 `:app`、`:core`、`:data`，依赖方向保持 `app -> core + data`、`data -> core`。
- 顶级路由恰好 4 个，`S0TextGenerationProvider` 协议定义恰好 1 个。
- 生产、JVM 测试与 Android 仪器测试均处于标准 source layout，不存在 `src/s0` 临时 source set。
- 未发现 Room、SQLCipher、WorkManager、RAG 或向量存储依赖。
- `PROJECT-STATE.json` 可解析，项目身份、硬边界及报告入口可解析且存在。
- App 使用的 Logo 与 `branding/selected/zhijuan-logo-draft.png` SHA-256 一致：`AD63D0BB3EBD000ADEBEBE5F1F72C5DAC101DD7DD608A94AED3AB1EDF4DBB8BA`。

成功标记：`PROJECT_INTEGRITY_CHECK_OK`。

## 交叉审计与已修正文档问题

主模型审计与 Luna 只读交叉审计共同发现并处理：

1. S1 的 `PV-014/015/017` 追踪说明曾错位，现已修正为凭据与隐私、可行动错误、两次调用预算；取消仍是已验证合同能力，但不冒充 `PV-014`。
2. S0 报告缺少统一 YAML `marker`，现已补齐。
3. `PROJECT-STATE.json` 曾把没有独立报告的 S1 加固动作列为完成节点，现归回 S1 证据，不再伪装成独立节点。
4. 全量本地门禁改为组合完整性检查、ASCII 镜像 JVM 测试、Debug/Release 构建、安全扫描与备份排除检查。

## 完整性边界

以下是“后续尚未实现/验收”，不是本次旧项目剥离造成的丢失：

- `PV-001` 真实设备真实 Provider 连接仍为外部门禁。
- `PV-002` 至 `PV-018` 的完整产品覆盖仍分布在 S2–S4；当前 S0/S1 只能提供局部或本地证据。
- `RG-04/05/07/09` 等发布门禁尚未完成；完整性脚本不得据此输出 release ready。
- `PV-017` 的本地单章两调用测试不能替代真实 Provider 连续五章计数。

## 验证证据

| evidence_id | status | 说明 |
|---|---|---|
| `INTEGRITY-001` | PASS | 完整性脚本输出根目录、无版本控制、模块/路由/协议、source layout、旧路径和品牌哈希全部通过 |
| `INTEGRITY-002` | PASS | Luna 只读规格—文件—测试矩阵复核；未修改文件、未接触 API Key |
| `INTEGRITY-003` | PASS | S1 需求追踪编号与 V3 `requirements.json` 重新核对 |
| `INTEGRITY-004` | PASS | 组合本地门禁：18/18 JVM、Debug/Release、4 项安全扫描回归、源码与 2 个 APK 扫描、备份排除全部通过 |
| `INTEGRITY-005` | PASS | V3 规格 lint：47 个必需文件、18 项需求、10 项发布门禁通过 |
| `INTEGRITY-006` | PASS | Android 16 真机：Debug 覆盖安装、Data Keystore 2/2、冷启动 362ms 且 0 FATAL |
| `INTEGRITY-007` | UNVERIFIED | App 测试 APK 被设备端安装确认拦截；已有 API 35 App 1/1 证据不受影响 |

## 下一入口

仍为 `V3-S1-REAL-CONNECTION`。真实 API Key 只能由用户在 App 设置页本地输入，不得进入聊天、命令行、日志或报告；通过前不进入 S2。
