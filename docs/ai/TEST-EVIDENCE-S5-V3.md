---
document_type: V3_TEST_EVIDENCE
marker: V3-TEST-EVIDENCE
node_id: V3-S5
status: COMPLETE
date: 2026-08-20
search_tags: [project-bootstrap, rolling-plan, library, reader-position, text-only, handoff]
---

# 测试证据：V3-S5 发布整理

## 既有 MUST 需求闭环

| Requirement | 实现 | 稳定证据 | 结果 |
|---|---|---|---|
| PV-002 | 书名、题材、主角、基调、核心设定；预览基线和 8 章计划；确认后原子落盘 | `S5TextProjectPlanTest`、`S5TextProjectUiTest`、data bootstrap test | PASS |
| PV-004 | 计划剩 2 项时阻止正文生成并要求明确确认本地 8 项滚动窗口 | core plan test、`explicit refresh keeps two...`、Generation UI test | PASS |
| PV-010 | 目录、上下章、每章滚动偏移、最近章节、字体/行距/主题持久化 | `S0ReaderScreenTest`、`S5ReaderPositionStoreTest` | PASS |
| PV-011 | 多项目卡片、继续阅读、生成、导出、恢复徽标、二次确认删除 | `S5TextProjectUiTest`、app-data 双项目集成测试 | PASS |

## 纯文字范围检查

命令对 `app/core/data`、Manifest 与 Gradle 配置搜索 `TextToSpeech/TTS/SpeechRecognizer/RECORD_AUDIO/audio/voice/语音/朗读/录音`，结果为 0。项目未声明音频权限，未引入媒体或语音依赖；所有精力集中于文字创建、生成、结算、章节、阅读与恢复。

## 最终回归

- JVM：51/51，0 failure，0 skipped。
- API 35 instrumentation：18/18，0 failure。
- API 36 真机 archive round-trip：1/1，0 failure。
- clean Debug/Release + lintDebug/lintRelease：成功，0 lint error。
- Release：v2 签名通过、API 35 安装/冷启动、不可调试。
- 真机 Debug：更新安装、420ms 冷启动、当前进程 0 fatal match；五章项目仍为 revision 5。
- 安全扫描、备份排除、模块边界、规格 lint：全部通过。
