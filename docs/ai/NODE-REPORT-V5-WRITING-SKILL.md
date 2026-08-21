---
document_type: V5_NODE_REPORT
marker: V5-NODE-REPORT
node_id: V5-WRITING-SKILL
status: COMPLETE
date: 2026-08-21
requirements: [PV-002, PV-005, PV-010, PV-012]
change_requests: [ZJ-CR-12, ZJ-CR-13]
search_tags: [writing-skill, markdown-import, strict-json, quality-card, prompt-priority, sha256, archive-roundtrip]
---

# 节点报告：项目级创作 Skill

## 可见结果

- 新建小说时可展开“创作 Skill（可选）”，从系统文件选择器导入单个 `.md` 或 `.json` 文件。
- 导入后先显示文件名、格式、规则预览和 SHA-256 短哈希；用户必须点“确认质量卡”，才可带 Skill 创建项目。
- 已有项目可从书库三点菜单进入“创作 Skill”，查看当前状态、替换或移除。
- 创作页显示本章实际使用的质量卡名称、版本与短哈希；损坏文件自动停用并回退默认质量卡，不阻塞项目打开。

## API 遵循边界

- API 不会训练或永久学习该文件；应用在每次正文请求中确定性加入经校验的质量卡，因此可保证“该项目后续正文请求带上当前有效 Skill”。
- 提示优先级固定为：Provider/应用安全与纯正文输出 > 项目硬事实和禁止项 > 本章目标与状态 > 项目质量卡 > 默认写作基线。
- Skill 只进入正文调用，不进入结构化结算调用；不产生第三次调用，不记录规则正文到诊断日志。
- Skill 原文件不会作为代码或 Agent 指令执行。Markdown 只提取白名单标题下的项目符号；JSON 使用严格 schema，拒绝工具、文件、网络、提示覆盖、外部引用、HTML 和非空示例。

## 存储与恢复

- 项目目录新增 `writing-skill/source.md|json`、`manifest.json`、`quality-card.json`，均使用原子写入。
- 单文件最大 32 KiB；有效规则最多 8 条、规范化正文最多 1600 字符；超限直接拒绝，不静默截断。
- manifest 同时记录源文件和规范化质量卡 SHA-256。重启时重新核验；不一致时状态为 `DISABLED_CORRUPT`。
- 替换或移除在活动生成任务或 PendingCommit 存在时被拒绝，避免同一章中途变更规则。
- 项目导出/导入白名单已包含 Skill 三文件，并通过归档往返与篡改降级测试。

## 主要改动文件

- `core/src/main/kotlin/app/zhijuan/core/s0/S0Domain.kt`
- `data/src/main/kotlin/app/zhijuan/data/s0/S5WritingSkill.kt`
- `data/src/main/kotlin/app/zhijuan/data/s0/FileS0NovelRepository.kt`
- `data/src/main/kotlin/app/zhijuan/data/s4/S4ProjectArchive.kt`
- `core/src/main/kotlin/app/zhijuan/core/s2/S2ContextBuilder.kt`
- `app/src/main/kotlin/app/zhijuan/reader/S1OpenAiCompatibleProvider.kt`
- `app/src/main/kotlin/app/zhijuan/reader/S5TextProjectUi.kt`
- `app/src/main/kotlin/app/zhijuan/reader/S0App.kt`
- 对应 core/data/app JVM 与 Compose 测试。

## 证据

- JVM ASCII 镜像完整回归：60 项，0 失败，0 跳过。
- API 35 `S5TextProjectUiTest`：8 项，0 失败；覆盖创建前预览与明确确认、已有项目替换、创作页质量卡标识以及原有创建/生成回归。
- `:app:assembleDebug` 与 `:app:assembleDebugAndroidTest`：通过。
- 当前 debug APK SHA-256：`A14495FC4951DB8661051B72F5092772A4AC8078731C8A2917775F8C409553DD`。

## 下一入口

继续 `V5-SEQUENTIAL-BATCH`：在一次明确操作中选择 1/2/3 章，仍逐章建立独立 Job、逐章两调用和提交，失败/取消立即停止且进程死亡后不自动续跑。
