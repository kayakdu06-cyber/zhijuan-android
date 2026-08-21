---
document_type: V3_NODE_REPORT
marker: V3-NODE-REPORT
node_id: V3-S1-QUICK-SETUP
status: COMPLETE
date: 2026-08-20
parent_node: V3-S1
current_product_node: V3-S1-REAL-CONNECTION
requirements: [PV-001, PV-014, PV-015]
search_tags: [api-key-only, deepseek-v4-pro, quick-setup, progressive-disclosure, provider-preset]
---

# 节点报告：V3-S1 API Key 快速设置

## 结论

设置页已从“Endpoint + API Key + 模型 + 四个参数”改为默认只需填写 `DeepSeek API Key`。Endpoint、模型、超时和正文上限由内置推荐配置提供；其他 OpenAI-compatible 服务仍可在折叠入口中配置，因此没有新增 Provider 协议，也没有破坏 V3 的完整落盘 Schema。

本节点状态为 `COMPLETE`；父节点 `V3-S1` 仍为 `PARTIAL`，因为真实 API Key 尚未由用户在 App 内执行连接和单章请求。

## 用户可见结果

- 首屏标题为“连接 AI”，只显示推荐配置摘要、API Key 输入框和“验证并启用”按钮。
- 推荐配置：`https://api.deepseek.com`、`deepseek-v4-pro`、连接 15 秒、读取 180 秒、单章总计 300 秒、正文上限 12000 字符。
- API Key 少于 8 个字符或包含空格时，就地显示错误并禁用主按钮。
- Key 默认隐藏、不是 `rememberSaveable`、成功或失败后清空；已保存 Key 不回显。
- “使用其他兼容服务”展开后可编辑 Endpoint 和模型；生成参数再下一层折叠，可一键恢复推荐配置。
- 已保存的自定义 Endpoint、模型和数值参数继续恢复，Key 始终为空。

## 规格裁决

用户本轮明确要求“除了 API 之外全部配置好”，因此采用默认预设 + 渐进展开。第三版 `02-interaction.md` 中 Endpoint/API Key/模型仍是可配置字段；当前实现保留了全部字段，只改变默认可见层级。第三版规格原件未被修改。

DeepSeek 推荐值按 2026-08-20 官方 API 文档核对：OpenAI 格式 Base URL 为 `https://api.deepseek.com`，现行模型包含 `deepseek-v4-pro`。预设未来变更时只修改 `S1ProviderDefaults` 并重跑本节点测试。

## 关键实现

- `core/src/main/kotlin/app/zhijuan/core/s0/S1ProviderContract.kt`：新增 `S1ProviderDefaults`，不新增协议或密钥。
- `app/src/main/kotlin/app/zhijuan/reader/S1ProviderSettingsScreen.kt`：Key-only 默认界面、兼容服务折叠、内联校验和 TalkBack live region。
- `core/src/test/kotlin/app/zhijuan/core/s0/S1ProviderContractTest.kt`：预设 HTTPS、唯一路径和数值范围测试。
- `app/src/androidTest/kotlin/app/zhijuan/reader/S1ProviderSettingsScreenTest.kt`：仅 Key 成功、折叠字段、旧自定义配置恢复。

## 证据

| evidence_id | status | evidence |
|---|---|---|
| `QS-CORE-001` | PASS | Core 7/7；推荐预设规范化为唯一 `/chat/completions` 且满足 Validator |
| `QS-JVM-001` | PASS | ASCII 镜像 Core 7 + Data 11 + App 1 = 19/19、0 failure |
| `QS-COMPOSE-001` | PASS | API 35：3/3；仅 Key、折叠兼容服务、旧配置恢复且 Key 为空 |
| `QS-BUILD-001` | PASS | clean 后 Debug/Release 构建成功 |
| `QS-SECURITY-001` | PASS | 4 项扫描回归、源码、Debug/Release APK 与备份排除通过 |
| `QS-VISUAL-001` | PASS | API 35：375dp、横屏、font scale 1.3 均可读、可滚动、无必要操作遮挡 |
| `QS-PHYSICAL-001` | PASS | 当前 Debug APK 在 Android 16 真机覆盖安装；连接摘要与项目均恢复 |
| `QS-PHYSICAL-COMPOSE-001` | UNVERIFIED | Android 16 Compose runner 安装成功但未结束；API 35 的 3/3 是当前自动化裁决证据 |
| `QS-LUNA-001` | PASS | Luna 只读复核最小改动、Schema 恢复与密钥边界；未修改文件、未接触 Key |

APK SHA-256：

- Debug：`A499E510AE1A274C8DA19829A018186E86B4C582E426C2F8970033D385A8E76A`
- Release unsigned：`BEE1216636398FFCF80AA487A3E1F405298C8A7D67D3A8E743ACF29A516AEA5D`

视觉证据：

- `docs/ai/evidence/V3-S1-QUICK-SETUP-api35-375dp.png`
- `docs/ai/evidence/V3-S1-QUICK-SETUP-api35-landscape.png`
- `docs/ai/evidence/V3-S1-QUICK-SETUP-api35-large-font.png`

## 保持不变的硬边界

`modules=3` `routes=4` `provider_protocols=1` `normal_model_calls=2`
`HTTPS-only` `Key-storage: AndroidKeyStore only` `readable-draft-before-settlement`

## 下一入口

手机上的 Debug 版已经更新。用户只需在 App 内粘贴 DeepSeek API Key 并点击“验证并启用”；Key 不得进入聊天、命令、日志或报告。随后按 `NODE-RUNBOOK-V3-S1-REAL-CONNECTION.md` 完成单章真实连接门禁。
