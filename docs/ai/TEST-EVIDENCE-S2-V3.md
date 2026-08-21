# V3-S2 测试证据

检索标记：`S2-TEST-EVIDENCE` `continuity-cases` `chapter-directory` `settlement-schema-1.0`。

日期：2026-08-20。项目根：`D:/deepseekuser/projects/织卷1`。版本控制：`NONE`。

## 自动化结果

| 层级 | 结果 | 覆盖 |
|---|---:|---|
| Core JVM | 11/11 | 权威 9 个连续性案例精确匹配；上下文预算；计划刷新阈值；协调器故障路径 |
| Data JVM | 15/15 | `settlement.schema.json` 1.0 严格根键/嵌套结构；权威有效夹具；旧六键拒绝；SSE/取消/错误映射；旧状态兼容 |
| App JVM | 2/2 | App→Core→Data 重启恢复；重复一次性事件保留草稿且不推进 revision |
| API 35 App 仪器 | 5/5 | 目录切章、上下章边界、摘要隐藏、Provider 设置 |
| API 35 阅读压力组合 | 2/2 | 568×320 dp、1.3 字体倍率、深色模式；目录与摘要隐藏 |
| API 35 Data 仪器 | 2/2 | AndroidKeyStore 往返与 key 丢失覆盖 |
| API 36 真机 Data 仪器 | 2/2 | AndroidKeyStore 往返与 key 丢失覆盖 |

JVM 合计：28/28，failures=0，errors=0，skipped=0。

## 构建与设备

- `:app:assembleDebug`：PASS。
- `:app:assembleRelease`：PASS；lint vital 和 R8 同步通过。
- Debug APK SHA-256：`EC0D91CD794FC45EB959AECCE800E0F8B11711BAC6F84990895F75BA709C00CB`。
- Release unsigned APK SHA-256：`0C8705A16DA17A3B3772E3719FACF174C37FDB6087F44BB2228FBAAB9C1C8BD4`。
- API 36 真机覆盖安装：PASS；既有 `project_s0`、第 1 章正文和 Provider 设置文件仍存在。
- API 36 冷启动：PASS，`TotalTime=395ms`，AndroidRuntime fatal=0。

## 工程门禁

- `verify-project-integrity.ps1`：PASS。
- `verify-module-boundaries.ps1`：PASS（3 modules / 4 routes / 1 Provider protocol）。
- `verify-backup-exclusions.ps1`：PASS。
- `security-scan.ps1`：PASS，扫描 4 个 APK，未发现潜在密钥材料。
- 第三版 `docs_lint.py`：PASS（47 required files / 18 requirements / 10 release gates）。

## 模型调用核对

本节点的测试与界面验收没有发起真实模型请求。正常章节代码路径仍固定为正文 1 次、结算 1 次；目录、规则验证、Schema 验证、事件去重和计划提示均为本地逻辑。
