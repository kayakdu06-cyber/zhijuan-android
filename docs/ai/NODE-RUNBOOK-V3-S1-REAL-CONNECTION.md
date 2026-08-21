---
document_type: V3_NODE_RUNBOOK
marker: V3-NODE-RUNBOOK
node_id: V3-S1-REAL-CONNECTION
status: COMPLETE
date: 2026-08-20
parent_node: V3-S1
search_tags: [real-device, provider, api-key-local-only, redacted-evidence, adb]
---

# 运行手册：V3-S1 真机真实 Provider 连接

## 当前准备状态

- Android 16 / API 36 物理设备已连接。
- Debug APK 已覆盖安装且冷启动通过；未清除已有 App 数据。
- 真实连接测试已经通过；API Key 仅在 App 内并已安全保存，不需要再次输入。
- DeepSeek V4 已显式关闭思考模式；最终单次普通章节的正文与结算均通过，提交、阅读和重启恢复完成。
- App 仪器测试 APK 需要设备端允许 USB 安装/确认；不影响手动真实连接，但在确认前保持 `UNVERIFIED`。

## 禁止事项

- 不在聊天、终端参数、剪贴到报告、截图、录屏或日志中提供 API Key。
- 不读取或导出 `provider-settings.json`、Keystore 条目或凭据密文文件。
- 不把 Provider 响应正文、原始错误 body 或请求头写进节点报告。
- 不自动重试已发送的 POST；结果未知时停止并记录安全错误码。

## 用户在手机上的操作

1. 打开 Debug 版“织卷”；“灯下回卷”和安全连接摘要应已恢复。
2. 进入生成页，只点击一次“生成本章”，等待结果，不重复点击。
3. 成功后进入阅读页，确认正文可读且不显示内部结算摘要。
4. 将 App 从最近任务划掉或强制停止后重新打开，确认项目、章节和已安全配置摘要仍可恢复。

连接测试是用户明确触发的独立调用；普通章节仍必须只有一次 `PROSE` 和一次 `SETTLEMENT`。

## Agent 可执行的无凭据检查

在项目根运行；设备序列号只在本机替换，不写入报告：

```powershell
. .\scripts\env-zhijuan1-toolchain.ps1
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
& $adb devices -l
& $adb -s '<device-serial>' shell run-as app.zhijuan.reader.debug cat files/zhijuan-config/provider-diagnostics.jsonl
```

`provider-diagnostics.jsonl` 的合同字段仅允许：`at`、`stage`、`errorCode`、`requestIdHash`、`durationMillis`、`responseBytes`、`httpStatus`、`inputTokens`、`outputTokens`、`finishReason`。若输出出现 Endpoint、API Key、Authorization、正文或 Provider 原始 body，立即停止并按安全缺陷处理。

## 可写入报告的脱敏证据

| field | allowed value |
|---|---|
| `device_api` | Android API 数字；不写设备序列号 |
| `connection_test` | PASS 或稳定 `errorCode` |
| `prose_stage_count` | 本次单章应为 1 |
| `settlement_stage_count` | 本次单章应为 1 |
| `request_id_hash` | 仅哈希 |
| `duration_ms` | 数字 |
| `http_status` | 数字或空 |
| `usage` | input/output token 数字或空 |
| `restart_recovery` | PASS/FAIL |

## S1 关闭条件

以下全部满足才可把 `V3-S1` 从 `PARTIAL` 改为 `COMPLETE`：

1. 真机“测试并保存”成功，原始 key 未进入 UI state、日志、报告和 APK。
2. 一次用户生成动作得到可读正文和结构化结算。
3. 脱敏诊断中本章恰好 `PROSE=1`、`SETTLEMENT=1`，无隐藏调用。
4. 杀进程后项目、章节和 Provider 摘要恢复。
5. 失败时只记录稳定错误码与下一动作，不记录原始响应。

本手册只关闭 S1 单章真连接门禁，不替代 `PV-017/PV-018/RG-05` 的连续五章发布验收。
