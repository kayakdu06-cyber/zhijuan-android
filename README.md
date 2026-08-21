# 织卷

织卷是个人单设备使用的本地优先 Android 长篇小说生成与阅读 App。第三版实现已完成，只包含三个 Gradle 模块、四个顶级路由和一套 OpenAI-compatible Provider 协议。

## 当前实现

- `:core`：领域合同、唯一 Provider 协议、一次一章协调器。
- `:data`：文件型项目存储、可读草稿、PendingCommit 恢复、OpenAI-compatible Provider、AndroidKeyStore。
- `:app`：Jetpack Compose 四路由、五字段建书、多项目书库、章节目录/阅读位置、生成与 Provider 设置。
- Provider 支持 DeepSeek、Qwen、GLM、Kimi 与自定义 OpenAI-compatible 中转站的多配置保存和切换；密钥只进入 AndroidKeyStore。
- 普通章节固定为正文一次、结构化结算一次模型调用。
- 正文默认目标为 2500–6000 字符、最多 4000 输出 token；长度安全阈值仍为 12000 字符。
- 原始 SSE 流独立限制为 2MiB，推理事件不占用正文字符额度。
- 每章保存为独立 Markdown；目录、上下章、每章滚动位置、字号/行距/主题均为本地文字阅读能力。
- 结算摘要只供连续性内核使用，不显示在正文或无障碍语义树。
- 可由用户明确选择连续生成 1/2/3 章；系统仍逐章执行、一次只生成一章，每章固定两次模型调用。
- 不包含语音、录音、朗读、TTS、媒体播放或音频权限/依赖。

`V3-S0` 至 `V9-CR25-28` 已完成。当前证据包含 78 个 JVM 测试、API 35 runner `OK (30 tests)`、API 36 真机安装与冷启动、连续五章、50 章无费用影子回归、十种故障、密钥扫描和 Release 构建。

## 快速入口

- 机器可读状态：`docs/ai/PROJECT-STATE.json`
- 节点索引：`docs/ai/NODE-REPORT-INDEX.md`
- 当前节点：`docs/ai/NODE-REPORT-V10-PUBLIC-REPOSITORY.md`
- 发布门禁证据：`docs/ai/TEST-EVIDENCE-S4-V3.md`
- 交接：`docs/ai/HANDOFF-V3.md`
- 第三方清单：`THIRD_PARTY_NOTICES.md`
- Luna 委派边界：`docs/ai/LUNA-DELEGATION-MATRIX.md`

## 构建

```powershell
. .\scripts\env-zhijuan1-toolchain.ps1
.\scripts\verify-project-integrity.ps1
.\scripts\run-jvm-tests-ascii-mirror.ps1 -GradleTasks ':core:test',':data:testDebugUnitTest',':app:testDebugUnitTest'
.\gradlew.bat --offline --no-configuration-cache :app:assembleDebug
```

完整本地门禁可一次运行：

```powershell
.\scripts\verify-build.ps1 -Offline
```

该脚本按顺序执行项目完整性、ASCII 镜像 JVM 测试、Debug/Release 构建、安全扫描与备份排除检查。

项目使用公开 Git 仓库管理源码；构建缓存、`local.properties`、APK、签名材料和本地用户数据不会提交。开发状态以节点报告、测试结果和产物 SHA-256 为准。API Key 只能在 App 内输入并由 AndroidKeyStore 保护，禁止写入命令行、源码、日志、备份或文档。
