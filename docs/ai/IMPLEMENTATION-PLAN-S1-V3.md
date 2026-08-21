# 实施计划：V3-S1 真实 Provider

```yaml
node_id: V3-S1
status: PARTIAL
date: 2026-08-17
requirements: [PV-001, PV-005, PV-006, PV-014, PV-015, PV-017]
release_gates: [RG-01, RG-02, RG-06, RG-08, RG-10]
```

## 范围

- 用户可见结果：设置页可输入 Endpoint、API Key、模型，预览规范化请求 URL，测试成功后保存；生成协调器可切换到同一 OpenAI-compatible Provider。
- 协议结果：正文走 SSE，结算走非流式 JSON；支持 requestId 取消、响应大小/字符限制、明确超时和安全错误。
- 凭据结果：API Key 经 AndroidKeyStore AES-GCM 保护；UiState、日志、项目文件和备份均不包含原始 key。
- 明确不做：前台服务、通知、自动重试 POST、连续性八规则、计划刷新、导入导出、五章验收。

## 最小上下文

- 文档：第三版 `docs/02-interaction.md`、`03-architecture.md`、`05-data-provider-recovery.md`、`07-quality-acceptance.md`、`08-delivery-workload.md`。
- 契约：`constraints.json`、`requirements.json`、`provider-settings.schema.json`、`error-catalog.json`、`routes.json`。
- 不变量：三模块、四路由、一个 Provider 协议、一次一章、普通章两调用、HTTPS-only、POST 送达后不自动重试。

## 纵向步骤

| # | 跨模块结果 | 预计改动文件 | 测试/证据 | 完成条件 | 状态 |
|---|---|---|---|---|---|
| 1 | Core 定义设置、请求、流事件、连接/取消与安全错误合同 | `core/src/main/.../S1ProviderContract.kt`、`S0Domain.kt` | Core JVM 表驱动测试 | URL/错误/调用目的可确定映射 | complete |
| 2 | Data 保存非秘密设置与 Keystore 密文，实现 HTTP/SSE adapter | `data/src/main/.../provider/*`、Data Gradle | JVM fake server + Android Keystore test | SSE/错误/超限/取消/Keystore 故障覆盖通过 | complete |
| 3 | App 设置页联通测试并保存，生成链可选择真实 Provider | `MainActivity.kt`、`S0App.kt` | App JVM/Compose compile | UI 不持有已保存原 key；失败均有下一动作 | complete |
| 4 | 节点证据与智能体报告 | `docs/ai/TEST-EVIDENCE-S1-V3.md`、`NODE-REPORT-V3-S1.md`、索引 | build/scans | 本地证据已归档；真实请求门禁未通过 | complete-local |

## 风险与故障路径

- 注入：无效 URL、401、404/模型不可用、429、5xx、断流、坏 Content-Type、SSE 任意分片、超限、用户取消、Keystore 重建后密文不可解。
- 恢复：S1 不改变 S0 的草稿优先与 PendingCommit 协议；正文请求失败不创建草稿，正文保存后结算失败仍返回 READABLE_DRAFT。
- 模型调用：普通章节仍为正文一次、结算一次；连接测试是用户明确触发的独立调用。
- 依赖：复用版本目录已有 OkHttp/MockWebServer，不新增模块、路由、Provider 协议或持久化实体。

## 完成核对

- [x] Core/Data/App 已提交范围的相关测试通过
- [x] fake server 已提交范围覆盖 SSE、错误和取消
- [x] Android Keystore 已提交范围仪器测试通过
- [ ] 真实设备真实连接请求通过；若环境缺失则节点状态保持 PARTIAL
- [x] 源码、资源、日志和 APK 密钥扫描通过
- [x] 需求—测试—提交映射写入节点报告

## 暂停状态（2026-08-17）

本地实现和新增故障测试均已验证；恢复节点为 `V3-S1-REAL-CONNECTION`。
