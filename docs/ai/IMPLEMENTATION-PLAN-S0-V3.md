# S0 纵切片实施计划：三模块本地优先最小闭环

日期：2026-08-17  
范围：第三版规格 S0，不接真实 Provider、不扩展未来功能

## 初始审计结论

- 第三版要求 `:app`、`:core`、`:data` 三模块、四个顶级路由、文件型项目数据、一次一章、普通章节恰好正文+结算两次调用。
- S0 以全新三模块纵切片验证草稿优先与 PendingCommit；旧实现未进入本切片。
- 2026-08-20 已完成物理剥离，当前项目不再包含旧模块或旧 source set。

## 本切片范围

- 需求：PV-002、PV-003、PV-005、PV-006、PV-008、PV-010、PV-013、PV-017；门禁 RG-02、RG-03、RG-06。
- 用户可见结果：创建一个本地项目，点击生成得到固定假正文；正文立即成为可读草稿；固定结算经过本地校验写入 PendingCommit 并幂等提交；阅读页显示章节；重新创建 Repository 实例仍能读取同一正文/提交状态。
- 明确不做：真实网络、Keystore 配置界面、前台服务、计划刷新、导入导出、五章实机、旧 feature 功能迁移。

## 纵向步骤

| # | 跨模块结果 | 预计改动文件 | 测试/证据 | 完成条件 |
|---|---|---|---|---|
| 1 | `:core` 定义 S0 领域模型、Provider 单协议、ChapterRoute 和生成协调器 | `core/src/main/kotlin/...` | Core JVM S0 test | 假 Provider 严格两次调用；草稿先于结算；PendingCommit 可重复应用 |
| 2 | `:data` 以项目目录 JSON/JSONL/Markdown 保存项目、草稿、提交包和事件，并在新实例中恢复 | `data/src/main/kotlin/...` | Data JVM S0 persistence test | 无 Room；正文可读；重复 commit 不重复事件 |
| 3 | `:app` 仅依赖 `:core`/`:data`，提供四路由 Compose 壳和 S0 操作 | `app/src/main/kotlin/...`、`settings.gradle.kts`、Gradle 文件 | App compile + integration test | 创建→生成→阅读可操作；状态文案和控件可访问 |
| 4 | 端到端 S0 证据 | 三模块标准 test source set、`docs/ai/TEST-EVIDENCE-S0-V3.md` | 三模块测试、assembleDebug、边界脚本 | 进程重建模拟后同一项目/正文/COMMITTED 状态 |

## 风险与恢复

- 每次多文件写入使用 PendingCommit；故障恢复只重放预期值，不覆盖更高 revision。
- 结算失败保留 `chapters/000001.md` 和 `READABLE_DRAFT`，不改变权威状态。
- 不新增模块、Provider 协议、模型调用或持久化实体；S0 Provider 为固定 Fake，仅用于闭环测试。
- 当前项目只保留 V3 三模块源码，不依赖隔离或排除规则。

## 完成核对

- [x] Core/Data/App S0 测试通过
- [x] `settings.gradle.kts` 仅三个模块
- [x] `python D:/gptuser/projects/longform-novel-app-planning/development-spec-v3/scripts/docs_lint.py` 通过
- [x] Debug APK 构建成功且品牌资源仍来自 `branding/selected/zhijuan-logo-draft.png` 的既有副本
- [x] 三模块依赖与源码边界扫描通过
