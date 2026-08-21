---
document_type: V6_NODE_REPORT
marker: V6-NODE-REPORT
node_id: V6-CR14-20
status: COMPLETE
date: 2026-08-21
requirements: [PV-001, PV-002, PV-004, PV-009, PV-010, PV-011, PV-014, PV-015, PV-017]
change_requests: [ZJ-CR-14, ZJ-CR-15, ZJ-CR-16, ZJ-CR-17, ZJ-CR-18, ZJ-CR-19, ZJ-CR-20]
search_tags: [v6, skill-import, markdown, json, quality-card, two-bottom-navigation, chapter-number-only, reader-continue, sequential-batch, multi-api, provider-profile, long-form-shadow, physical-release]
---

# 节点报告：V6 CR14–20

## 结论

`ZJ-CR-14` 至 `ZJ-CR-20` 已实现、回归并覆盖安装到实体手机 `d2b15cce`。现有书稿、章节与 API 凭据未清除；真机最终检查没有触发正文或结算请求。

产品边界保持：Gradle 模块 3 个、顶级路由 4 个、Provider 协议 1 个、活动生成任务最多 1 个；一次仍只生成一章，普通章节仍恰好执行“正文 + 结构化结算”两次模型调用。

## 需求到实现

| change request | final behavior | primary implementation |
|---|---|---|
| `ZJ-CR-14` | 通用 Markdown 可从常见标题、列表和短段落生成可编辑候选；无法识别时仍进入手动整理，不执行源文件中的命令或链接 | `data/src/main/kotlin/app/zhijuan/data/s0/S5WritingSkill.kt`、`app/src/main/kotlin/app/zhijuan/reader/S5TextProjectUi.kt` |
| `ZJ-CR-15` | `.md/.json` 来源上限提升为 256 KiB；通用 JSON 递归读取白名单字段；最终质量卡仍限制 8 条/1600 字符 | `S5WritingSkill.kt`、`S5WritingSkillTest.kt` |
| `ZJ-CR-16` | 底栏只保留“书库/设置”；内部生成路由继续存在，但不再作为常驻底栏页，也不向前台展示规划、摘要或线索 | `app/src/main/kotlin/app/zhijuan/reader/S0App.kt` |
| `ZJ-CR-17` | 目录、阅读、生成、恢复与导出中的用户章节主标题统一为 `第 N 章`；规划方向只保留在内部生成数据 | `S0App.kt`、`data/src/main/kotlin/app/zhijuan/data/s0/FileS0NovelRepository.kt` |
| `ZJ-CR-18` | 阅读页右上显示“续写”；先选择 1/2/3 章并明确确认，随后进入同一个顺序队列；大屏缩放下操作面板可滚动到确认按钮 | `S0App.kt`、`app/src/main/kotlin/app/zhijuan/reader/S3GenerationForegroundService.kt` |
| `ZJ-CR-19` | 可保存、编辑、删除和切换多组 API；提供 DeepSeek、Qwen、GLM、Kimi 与 OpenAI-compatible 预设/自定义入口；每组 Key 使用独立 Keystore 别名 | `core/src/main/kotlin/app/zhijuan/core/s0/S1ProviderContract.kt`、`data/src/main/kotlin/app/zhijuan/data/s0/provider/S1ProviderStorage.kt`、`OpenAiCompatibleS1Provider.kt`、`app/src/main/kotlin/app/zhijuan/reader/S1ProviderSettingsScreen.kt` |
| `ZJ-CR-20` | 本节点没有新增无障碍专项功能或专项验收，也没有删除既有 Compose 语义 | change-request scope |

## 关键数据与恢复规则

- 旧 `provider-settings.v1` 单配置会原地迁移为档案集合；用户原有 DeepSeek 配置与凭据别名继续使用。
- 生成任务在启动时记录并锁定 `providerProfileId`，正文和结算使用同一配置；任务结束后解锁。
- 多 API 存储为 schema `2.0`，同时兼容读取 schema `1.0`；最多保存 20 个配置档案。
- 开发回归发现并修正了配置 JSON 写入缺陷：`activeProfileId` 与 `lastConnectionTestAt` 曾因 Kotlin Elvis 表达式绑定范围而被写成 `null`。新增多档切换测试已覆盖该恢复路径。
- Skill 只被整理成项目级质量卡并注入正文提示；它不是模型训练，不能让外部 API 永久学习，也不能保证模型百分之百遵循。

## 自动化证据

- JVM ASCII 镜像：71 项，0 failure，0 error，0 skipped。
- 其中 `S6LongFormShadowTest`：无费用模拟 50 章；正文 50 次、结算 50 次、提交 50 章；计划窗口按阈值刷新；每 10 章重建仓库模拟进程死亡；最终 `PendingCommit=0`。
- API 35 仪器测试：29 项被报告，27 项执行，2 项按设计跳过，0 失败。跳过项是实体五章归档夹具和显式真实 Provider Harness。
- `:app:lintDebug`：0 error、26 warning；`:app:lintRelease`：0 error、26 warning。
- `scripts/verify-build.ps1 -Offline`：PASS，包含 debug/release、项目完整性、模块边界、安全扫描与备份排除策略。
- release 签名：APK Signature Scheme v2 PASS，1 个签名者，release 非 debuggable。

## 实体手机证据

- 设备：`d2b15cce`，Android API 36。
- `adb install -r` 安装 `outputs/zhijuan-v6-release.apk`：`Success`；没有卸载或清除数据。
- 冷启动：`COLD`，`TotalTime=111 ms`；AndroidRuntime fatal 匹配 0。
- 已检查：两项底栏、原项目与 3 章数据仍在、阅读纸色、纯章节序号、右上续写、面板滚动至确认按钮、多 API 当前配置强选中态、阅读返回书库。
- 没有点击“续写第 4 章”，因此真机检查模型调用为 0。

视觉证据目录：`docs/design/v6-cr14-20/`。关键文件：

- `phone-final-library.png`
- `phone-final-reader.png`
- `phone-final-reader-continue-sheet.png`
- `phone-final-reader-continue-sheet-scrolled.png`
- `phone-final-settings.png`
- `phone-generation.png`

## APK

- debug：`outputs/zhijuan-v6-debug.apk`，33,105,237 bytes，SHA-256 `8C828F0EFE8CE775858BA1101840365D6C76032A0579E1CA52B22937F0F29FC2`。
- release：`outputs/zhijuan-v6-release.apk`，3,007,824 bytes，SHA-256 `74C9281643CA0627FDF691F9165057A8ECE8E07CFA061559CDCE7B9D1D49EE56`。

## 长篇能力的准确边界

50 章影子回归证明文件存储、章节状态机、计划窗口、恢复提交和逐章两调用机制能持续运行；它不等同于 50 章真实模型文学质量验收。真正长篇质量仍取决于所选模型、上下文窗口、项目设定与每章人工校准。2/3 章模式只是连续排队，仍逐章保存和结算；章数越多，用户在中途校准方向的机会越少，因此默认继续为 1 章。

## 已批准偏差与未做事项

- 用户已批准底栏从 3 项改为 2 项，以及 GENERATION 路由不显示为底栏入口。
- 前台不显示规划方向、内部摘要、关键线索或质量卡明细；这些数据仅在生成连续性内部使用。
- 没有新增 Room、RAG、向量库、多智能体、富文本、分支版本、自动连写、语音或第三次普通章节模型调用。
- 没有执行 50 章真实付费生成；真实 Provider 的内容策略也不能由织卷绕过。

## 后续入口

本节点完成。下一阶段如继续开发，应从真实长篇质量验收开始：使用用户明确授权的测试书和费用上限，小规模比较模型/参数/Skill 的章节连贯性，再决定是否调整提示和上下文预算；不得用隐藏调用替代人工评估。
