# 第三版实现交接

检索标记：`ZHIJUAN-V3-HANDOFF`  
日期：2026-08-20  
版本控制：NONE

## 可交付物

- 源码：`D:/deepseekuser/projects/织卷1`。
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。
- Release APK：`app/build/outputs/apk/release/app-release.apk`。
- Debug SHA-256：`A164B3B9D48F0EB7485DD047FD4E4C48FCC10B02D141458BBD234098EED84916`。
- Release SHA-256：`511CC834DBEAA31866BF7498D15D9D60F6B8A7B3C76E954840162EBB9D356A3B`。
- 第三方清单：`THIRD_PARTY_NOTICES.md`。

## 配置与使用

1. Android 12+；首次启动在设置页只需填 API Key 并“测试并保存”，默认 Endpoint/模型已配置。
2. 在书库点击“创建小说”，填写书名、题材、主角、基调、核心设定。
3. 预览并确认故事基线与 8 章文字计划；项目文件在确认成功后才创建。
4. 在生成页一次生成一章。正常章固定正文一次、结构化结算一次；正文先成为可读草稿。
5. 阅读页通过“目录”切章，支持上下章并记住每章滚动位置；结算摘要不会出现在正文。
6. 计划只剩 2 章时，先明确确认“扩展后续计划”；该操作为本地文字规划，不调用模型。
7. 导出位置和导入文件由 Android 系统文件选择器指定，备份不含 API Key。

## 验证摘要

- 发布门禁：RG-01 至 RG-10 均有证据，详见 `TEST-EVIDENCE-S4-V3.md`。
- 五章真机：5/5 提交、15 个事件、第三章强杀恢复、第四章坏结算只重试、第五章硬冲突显式重试。
- 自动化：51 JVM + 18 API35 instrumentation + 1 API36 archive round-trip 全通过。
- Release：clean 构建、v2 签名、模拟器安装/冷启动且不可调试；源码与 APK 扫描无密钥。

## 已知限制

- 个人单设备、本地优先；无账号、后台服务端、云同步、广告、统计、语音、TTS 或媒体功能。
- 一次只生成一章且只有一个活动任务；不自动连写。
- 普通章节固定正文与结算两次 API 调用；异常重试必须由用户明确触发。
- 不含 Room、RAG、向量库、多智能体、富文本编辑器、分支版本、EPUB/PDF 导出。
- 真机厂商 USB 安装策略阻止 Release 侧载；Release 已在 API 35 验证，真机 Debug 与五章数据均正常。

## 故障时先做什么

- 正文可读但未提交：在生成页点击“只重试结算”，不要重新生成正文。
- 请求结果无法确认：只有确认不会造成不可接受重复计费时才明确重发。
- 提示恢复提交：保持应用打开，让 PendingCommit 幂等恢复完成。
- 状态版本不一致：不要删除项目，先导出备份；删除动作会拒绝活动任务与 PendingCommit。
- 认证/模型错误：到设置修正并测试，不反复点击生成。
