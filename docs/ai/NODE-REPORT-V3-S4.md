---
document_type: V3_NODE_REPORT
marker: V3-NODE-REPORT
node_id: V3-S4
status: COMPLETE
date: 2026-08-20
requirements: [PV-006, PV-007, PV-009, PV-010, PV-013, PV-014, PV-015, PV-016, PV-017, PV-018]
search_tags: [fault-injection, export-import, five-chapter-device, accessibility, release-gates]
---

# 节点报告：V3-S4 故障与实机收敛

状态：`COMPLETE`。计划：`docs/ai/IMPLEMENTATION-PLAN-S4-V3.md`。完整证据：`docs/ai/TEST-EVIDENCE-S4-V3.md`。

## 节点结论

- 十种规定故障均有可重复测试；权威状态不被半成品污染，草稿和 PendingCommit 按阶段保留，恢复不重复事件。
- 单项目 ZIP 使用 SAF 导入导出，拒绝路径穿越、超限声明、实际膨胀、密钥样本和无效 manifest；真机 round-trip 通过。
- 连接手机 `d2b15cce` 完成固定五章：最终 `revision=5`、`nextChapter=6`、五章 Markdown 与 15 条事件完整，第三章强杀和第四章坏结算恢复通过。
- 正常章只有正文与结构化结算两类调用；第五章为故障注入后用户明确触发的结算重试，没有隐藏 critic/summary/memory 调用。
- 阅读正文不显示结算摘要；章节目录、上下章和位置恢复均为纯文字功能。
- Debug/Release 从 clean 构建成功；Release 在 API 35 模拟器安装、冷启动且不可调试；源码与三个 APK 产物密钥扫描通过。

## 关键修复

- PendingCommit 每个持久步骤均可幂等重放；损坏主状态可从 `.bak` 恢复，JSONL 半行安全丢弃。
- 事件持久 ID 不再信任模型 ID；按 commit/eventKey 去重，并能在启动时补齐旧提交缺失事件。
- 导入在私有 staging 中完成路径、数量、大小、压缩率、Schema 和哈希验证后才提升为新项目。
- 门禁审计补齐 `PV-002/004/010/011`：五字段建书、明确滚动计划刷新、多项目书库/恢复徽标/二次删除确认、逐章滚动位置恢复。
- 项目没有语音、录音、朗读、TTS 或音频权限/依赖；TalkBack 仅用于 Android 无障碍验收。

## 自动化与设备结果

| 项目 | 结果 |
|---|---|
| JVM core/data/app | 51/51，0 failure |
| API 35 Compose 仪器测试 | 18/18，0 failure |
| API 36 真机导出导入 | 1/1，0 failure |
| 五章真机 | 5/5 committed，15 events，0 hard continuity conflict |
| Lint Debug/Release | 0 error；各 20 warning |
| Release 签名 | APK Signature Scheme v2 verified；单一 RSA signer |
| 模块/路由/协议 | 3 / 4 / 1，边界脚本通过 |
| 密钥与备份策略 | 扫描通过；`allowBackup=false`；9 个排除域 |

## 已知外部限制

- 双屏 Android 16 真机上的完整 Compose instrumentation 会卡在设备测试基础设施；同一 APK 的 UiAutomator 语义、实际 TalkBack、1.3 字体、深色主题、冷启动和关键真机 round-trip 已分别验证，API 35 全套 Compose 测试通过。
- 真机厂商 USB 安装策略拒绝 Release 侧载并返回 `INSTALL_FAILED_USER_RESTRICTED`；未绕过设备安全策略。相同签名 Release APK 已在 API 35 安装、冷启动并验证不可调试，Debug APK 已在真机更新安装与冷启动。

下一入口：`V3-S5` 只做诊断文案、第三方清单和交接整理，不再增加产品能力。
