---
document_type: V9_CURRENT_CONTEXT
marker: V9-NODE-REPORT
updated: 2026-08-21
project_root: D:/deepseekuser/projects/织卷1
version_control: NONE
current_node: V9-CR25-28
current_status: COMPLETE
---

# 织卷当前上下文

## 当前事实

- 产品为“织卷”，个人单设备、本地优先 Android APK；没有账号、后台、云同步、广告或 Git。
- 工程边界：`:app/:core/:data` 三模块、4 个顶级路由、1 个 Provider 协议、最多 1 个活动任务、一次 1 章、普通章正文+结算恰好 2 次调用。
- `V3-S0` 至 `V8-CR23-24` 与当前 `V9-CR25-28` 均已完成。
- 阅读页与生成页使用可保存有限返回栈；直接返回父层会弹栈，不再形成两页互相返回。
- 项目级剧情节奏为 `舒展 / 均衡 / 紧凑`，旧书默认“均衡”，项目 schema 为 `1.2`。节奏只影响后续正文的场景停留、节拍密度与转折间距，不跳过计划或增加调用。
- `清叙 / 暗涌 / 沉浸` 前台只显示名称与选中态。“沉浸”内部 prompt 已加强：明确成年任务要直接、完整、连续呈现，保持身体/感官和自愿/非自愿事实与后果连续性。第三方 Provider 服从度仍为 `UNVERIFIED`。
- 底栏仍只有“书库/设置”；图标、选中线和文字整体下移 `6dp`，与顶部分隔线留出空白。
- 前台章节标题只显示 `第 N 章`；规划方向、结算摘要、关键线索和质量卡细节不显示，但继续作为内部连续性数据。
- 阅读页纸色 `#EEECDF`，中央轻触隐藏工具栏，右上“续写”先选择 1/2/3 章并确认；批次仍逐章独立保存、结算、提交。
- Skill 支持通用 Markdown/JSON 候选与人工编辑：来源最多 256 KiB，最终最多 8 条/1600 字符；只进入当前正文 prompt，不训练外部模型。
- Provider 仍是单一 OpenAI-compatible 协议，但支持 DeepSeek/Qwen/GLM/Kimi/自定义中转站多配置档案；每档 Key 由 AndroidKeyStore 保护。
- V9 回归：JVM 78/0；API 35 instrumentation `OK (30 tests)`；lint 0 error；边界、安全、备份和签名门禁通过。
- V9 release 已使用 `adb install -r` 覆盖安装到实体手机 `d2b15cce`；未清数据，验收真实 Provider 调用为 `0`。

## 当前入口

- 节点报告：`docs/ai/NODE-REPORT-V9-CR25-28.md`
- 实施计划：`docs/ai/IMPLEMENTATION-PLAN-V9-CR25-28.md`
- 状态：`docs/ai/PROJECT-STATE.json`
- 外部变更：`D:/gptuser/projects/zhijuan-change-requests/CHANGE-REQUEST-2026-08-21-05-NAVIGATION-PACE-IMMERSIVE.md`
- Debug APK：`outputs/zhijuan-v9-debug.apk`
- Release APK：`outputs/zhijuan-v9-release.apk`

## 下一阶段

如用户继续要求“写真正长篇”，先定义专用测试书、少量章节的对照组合、可接受 API 费用和可判定的文学质量标准，再触发真实模型。不得用隐藏 critic/summary/memory 调用或一次 Prompt 多章替代现有逐章流程。

