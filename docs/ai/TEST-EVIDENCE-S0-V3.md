# 测试证据：S0 纵切片（第三版）

日期：2026-08-17  
范围：创建项目 → 固定假正文 → 固定结算 → PendingCommit → 阅读 → 重启恢复

## 构建身份

- 独立项目根：`D:/deepseekuser/projects/织卷1`
- 历史实现快照：`8086278`（移除版本控制前记录）
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
- Debug SHA-256：`703005A3663854315AAD15E5A691FF2D087852E901B8C1AE7C854CAE066DA95E`
- Release APK：`app/build/outputs/apk/release/app-release-unsigned.apk`
- Release unsigned SHA-256：`D10B80055669879A5B6BD6B4D700934DD568B7951BC0160247ADDA56B85083E7`
- Endpoint/模型：无；S0 使用本地固定 Fake Provider

## 自动化结果

| 测试集 | 命令 | 结果 | 证据 |
|---|---|---:|---|
| Core S0 | `scripts/run-jvm-tests-ascii-mirror.ps1 -GradleTasks ':core:test'` | 通过 | `ASCII_MIRROR_TESTS_OK` |
| Data S0 | 同上，追加 `:data:testDebugUnitTest` | 通过 | `S0DataPersistenceTest`：新 Repository 实例读取草稿；重复 apply 不重复事件 |
| App S0 | 同上，追加 `:app:testDebugUnitTest` | 通过 | `S0VerticalSliceTest`：App → Core → Data；重启后读取 COMMITTED 章节 |
| Debug/Release | `scripts/verify-build.ps1 -Offline` | 通过 | Debug/Release assemble、4 项安全扫描、备份排除检查均通过 |
| 模块边界 | `scripts/verify-module-boundaries.ps1` | 通过 | `MODULE_COUNT=3`, `TOP_LEVEL_ROUTES=4`, `PROVIDER_PROTOCOLS=1` |
| 规格文档 | `python .../development-spec-v3/scripts/docs_lint.py` | 通过 | 47 required files / 18 requirements / 10 gates |

## S0 关键断言

- 固定 Provider 计数：正文 `1` 次，结算 `1` 次；无 critic/summary/memory 第三次调用。
- `saveReadableDraft` 先写 `chapters/000001.md` 与 `READABLE_DRAFT` meta，再创建 PendingCommit。
- PendingCommit 应用顺序为 state → plan → JSONL event 去重 → chapter meta → completed；重复 `applyPendingCommit(commitId)` 不追加重复事件。
- 进程重启通过重新实例化 File Repository 验证：正文仍可读，revision 为 `1`，章节状态为 `COMMITTED`。

## 已知限制（本切片不宣称完成）

- S0 不接真实 OpenAI-compatible HTTP/SSE、Keystore、前台服务、导入导出和五章实机门禁。
- 2026-08-20 已完成旧源码物理清理；当前只保留 V3 三模块。
- 当前 UI 使用固定示例创建入口，未实现第三版完整 Provider 设置表单。
