---
document_type: V3_TEST_EVIDENCE
marker: V3-TEST-EVIDENCE
node_id: V3-S4
status: COMPLETE
date: 2026-08-20
search_tags: [RG-01, RG-04, RG-05, RG-07, RG-09, five-chapter, fault-matrix]
---

# 测试证据：V3-S4

## 构建身份

- 版本控制：`NONE`；项目状态由 `PROJECT-STATE.json` 与节点报告追踪。
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，SHA-256 `A164B3B9D48F0EB7485DD047FD4E4C48FCC10B02D141458BBD234098EED84916`。
- Release APK：`app/build/outputs/apk/release/app-release.apk`，SHA-256 `511CC834DBEAA31866BF7498D15D9D60F6B8A7B3C76E954840162EBB9D356A3B`。
- clean 构建：Debug、Release、lintDebug、lintRelease 成功；Lint 0 error、各 20 warning。
- 设备：`emulator-5554` / Android API 35；`d2b15cce` / Android 16 API 36 双屏手机。
- Provider：OpenAI-compatible Chat Completions，DeepSeek HTTPS Endpoint，模型 `deepseek-v4-pro`；不记录密钥。

## 自动化结果

| 测试集 | 通过/总数 | 关键范围 |
|---|---:|---|
| JVM `:core/:data/:app` | 51/51 | 两调用、连续性、SSE、文件恢复、归档、建书/计划 |
| API 35 app instrumentation | 18/18 | 四路由关键 UI、目录、位置、设置、前台服务、五章夹具 |
| API 36 archive round-trip | 1/1 | 真机私有目录导出/导入、可读与可继续 |
| 规格 lint | 47 files / 18 requirements / 10 gates | PASSED |

## 十种故障注入

| # | 故障 | 稳定测试/实机动作 | 权威状态与恢复结果 |
|---:|---|---|---|
| 1 | 正文流开始前断网 | `network failure before first prose chunk...` | 无章节、无 revision 变化、无结算调用；显式重试正文 |
| 2 | 正文流中途取消 | `mid stream cancellation...` | `.part` 不提升为正式正文，权威状态不变 |
| 3 | 正文保存后杀进程 | `process death immediately after prose save...`；真机第 3 章 | `.md` 与 READABLE_DRAFT 保留；冷启动只请求结算 |
| 4 | 结算返回非 JSON | `non json settlement remains...`；真机第 4 章注入 | 正文保留，revision 不变；只重试结算 |
| 5 | 重复首次相识 | `replayed one-time event...`；真机第 5 章首次结算 | 本地硬拒绝，不重复事件；明确结算重试后提交 |
| 6 | 唯一道具双持有 | `unique item cannot be settled to two holders...` | 硬冲突阻止提交，输入权威状态不变 |
| 7 | PendingCommit 每步杀进程 | `pending commit recovers idempotently after every durable step...` | 每个步骤重放至一个 commit、无重复事件 |
| 8 | `events.jsonl` 尾部半行 | `incomplete jsonl tail is discarded...` | 半行丢弃，后续去重追加有效 |
| 9 | `state.json` 损坏、`.bak` 完整 | `corrupt state primary uses intact backup...` | 使用备份并安全完成 pending |
| 10 | 恶意 ZIP | `S4ProjectArchiveTest` 五项 | 拒绝 `../`、声明超限、实际膨胀、密钥样本、坏 Schema；不提升项目 |

## 五章真机

固定设定：沈砚、叶舟、唯一黄铜钥匙、关系变化、第五章回收“缺失一分钟”伏笔。最终状态：`revision=5`、`nextChapter=6`、五章已提交、15 条 JSONL 事件、15 个 recentEventKeys。

| 章 | 正文调用 | 结算调用 | 明确异常动作 | 耗时（正文/结算） | Markdown 字节数 | SHA-256 | 结果 |
|---:|---:|---:|---|---|---:|---|---|
| 1 | 1 | 1 | 无 | 51919 / 11287 ms | 7996 | `c20c4156f1419bb5d80a4cf2744c5916d4dc368b0516a79c40d1f9e23963d0e1` | PASS |
| 2 | 1 | 1 | 无 | 87152 / 14415 ms | 13480 | `4ff702159dc308f444fe5fb4f223fa05a01a7de5ebbf444e102a7d1cf00d4e7e` | PASS |
| 3 | 1 | 1 | 正文保存后强杀；冷启动 settlement-only | 93350 / 13341 ms | 14136 | `081754bfe40e1b21c07b8190c9558b63edaa0991cac643a6fd146a1f806d77a7` | PASS |
| 4 | 1 | 1 | 网络前注入坏结算；用户只重试结算 | 60025 / 18716 ms | 8360 | `05b5749f919e44e7e58db178970862bb4d49beeb1901ea6761d4e303c7a4723e` | PASS |
| 5 | 1 | 2 | 首次结算含重复一次性事件，被本地拒绝；用户明确重试结算 | 72593 / 17232 + 15637 ms | 10677 | `427956c8a13412585bd3637546e231cd445a826e5b7eb67aac73aecce439a264` | PASS |

第 5 章第二次结算是验收故障后的显式用户动作，不属于正常章节路径。五章没有隐藏审稿、摘要或记忆调用；结算摘要只保存在内部元数据中，未进入阅读正文或无障碍语义树。

## 阅读、无障碍与隐私

- 目录、上一章、下一章、逐章滚动位置、字号、行距、跟随系统/浅色/深色均有 UI 或持久化测试。
- 真机以 `font_scale=1.3`、深色主题检查安全区和正文可读性；实际启用 TalkBack 后检查标题、按钮、目录和状态标签，随后恢复设备原无障碍设置。
- 所有主要动作目标为 48dp 或 52dp；Release 在 API 35 冷启动 997ms，`run-as` 拒绝，证明不可调试。
- `security-scan.ps1` 对源码与三个 APK 产物扫描通过；备份策略 `allowBackup=false`，9 个域排除。
- 项目无语音、录音、朗读、TTS、音频权限或媒体依赖。

## 发布门禁

| Gate | 证据 | 结果 |
|---|---|---|
| RG-01 | clean Debug/Release；Release API 35 安装、冷启动、不可调试 | PASS |
| RG-02 | 51 个 JVM core/data/app 测试 | PASS |
| RG-03 | `S0VerticalSliceTest` app-core-data、阅读、重启 | PASS |
| RG-04 | 上述十种故障矩阵 | PASS |
| RG-05 | API 36 手机五章、强杀与坏结算恢复 | PASS |
| RG-06 | 正常章正文 1 + 结算 1；异常只由明确动作触发 | PASS |
| RG-07 | ZIP 契约测试 + API 36 真机 round-trip | PASS |
| RG-08 | 源码/日志/ZIP/APK 安全边界与扫描 | PASS |
| RG-09 | API 35 UI 自动化 + API 36 TalkBack/大字/深色/安全区 | PASS |
| RG-10 | 3 modules / 4 routes / 1 protocol；`THIRD_PARTY_NOTICES.md` | PASS |

## 已知限制

- API 36 双屏真机完整 Compose instrumentation 受设备测试基础设施卡住；API 35 全套自动化与真机分项检查已补齐证据。
- 厂商 USB 安装安全策略阻止真机 Release 侧载；没有绕过。Release 已在 API 35 安装和冷启动，真机 Debug 已更新安装和冷启动。
