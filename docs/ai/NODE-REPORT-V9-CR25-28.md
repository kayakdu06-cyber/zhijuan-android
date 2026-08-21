---
document_type: ZHIJUAN_NODE_REPORT
marker: V9-NODE-REPORT
node_id: V9-CR25-28
status: COMPLETE
date: 2026-08-21
requirements: [ZJ-CR-25, ZJ-CR-26, ZJ-CR-27, ZJ-CR-28]
search_tags: [finite-back-stack, reader-generation-loop, plot-pace, expansive-balanced-tight, immersive-adult-prompt, provider-compliance-boundary, bottom-navigation-spacing]
next: REAL_PROVIDER_QUALITY_VALIDATION
---

# V9-CR25-28 节点报告

## 节点结果

本节点修复阅读页与生成页之间的无限返回环，新增 `舒展 / 均衡 / 紧凑` 三档剧情节奏，加强“沉浸”的成年正文提示词，并将底部导航图标与文字整体下移 `6dp`。最终 release 已覆盖安装到实体手机，未卸载、未清数据、未触发真实 API。

## ZJ-CR-25：返回循环

根因是阅读页和生成页各自把对方记为来源：阅读→生成后，生成又把阅读记为返回目标，两页返回会持续互跳。

现在使用有限返回栈：

- 正常进入子层时压入当前路由。
- 目标已是直接父层时弹栈，不再重复压入。
- 系统返回键和顶部返回箭头共用同一逻辑。
- 书库/设置底栏属于顶级切换，切换时清空嵌套历史。
- 返回栈可保存，活动重建后不会恢复旧循环指针。

单元测试覆盖阅读↔生成、书库→生成→阅读和顶级切换三条路径，均有限返回到书库。

## ZJ-CR-26：三档剧情节奏

### 前台与持久化

- 新建书和书籍更多菜单都可选 `舒展 / 均衡 / 紧凑`。
- 前台只显示三个名称、明确选中态和保存，不展示内部行为说明。
- 项目 schema 从 `1.1` 升到 `1.2`；读取 `1.0/1.1` 旧书时安全默认“均衡”。
- 生成或等待安全提交时禁止修改，避免任务中途改变。
- 只影响修改后生成的章节；不重写旧正文、计划或结算。

### 对写作效果的影响

| 档位 | 主要效果 | 主要风险 | 系统约束 |
|---|---|---|---|
| 舒展 | 观察、氛围、反应、关系与因果铺垫更充分 | 容易水、重复或延迟变化 | 当章仍必须完成 `goal + mustChange` 并产生新局面 |
| 均衡 | 场景展开与事件推进折中 | 特色不如两端鲜明 | 作为旧书和新书的安全默认 |
| 紧凑 | 有效行动、信息、阻力和转折的单位密度更高 | 容易因果突跳、情绪过浅 | 关键因果、决定、情绪和身体变化仍要写出过程 |

三档都只调整当前章的场景停留、节拍密度与转折间距；不允许跳过当前计划项、提前使用未来章事件、合并多章或增加模型调用。

## ZJ-CR-27：“沉浸”尺度加强

正文 prompt 现在明确要求：当当章任务与已有事实要求成年情节时，以成年小说的直接层级完整、连续地写出实际发生的关键过程；淡出、跳时、事后概括、纯暗示或与情节无关的情绪概述不能代替核心行为。

同时保留以下质量与事实边界：

- 只在相关人物被已有事实明确为成年人时应用。
- 具体行为服从人物视角、空间、衣着、姿势、距离、感官、疼痛、疲劳与行动能力的连续性。
- 保留自愿、犹豫、拒绝、被迫和失去选择等已有事实，不把非自愿改写成自愿；必须呈现当下与后续影响，不美化强迫。
- 不写成机械的词汇或动作清单，过程要与欲望、恐惧、权力、关系和剧情后果相连。

该改动能提高提示词对明确成年任务的要求强度，但不能保证第三方 Provider 不过滤、不拒绝或一定按请求执行。本节点不对真实 Provider 发起付费验收，所以外部服从度仍为 `UNVERIFIED`。

## ZJ-CR-28：底部导航留白

- 分隔线与 NavigationBar 内容之间增加 `6dp` 上内边距。
- 底栏固定高度、系统手势导航区适配、两项导航和选中态不变。
- 模拟器视觉证据为 `D:/gptuser/projects/zhijuan-change-requests/evidence/zhijuan-v9-bottom-nav.png`。

## 调用与架构边界

`gradleModules=3` `topLevelRoutes=4` `providerProtocols=1` `activeJobs=1`

`chaptersPerJob=1` `normalCallsPerChapter=2` `projectSchema=1.2`

`readable-draft-before-settlement` `idempotent-pending-commit` `Room=absent`

本节点没有新增模块、顶级路由、Provider 协议、人物卡、后台、云同步、Room、RAG、向量库、多智能体、富文本编辑器或隐藏模型调用。

## 验收证据

| gate | result |
|---|---|
| JVM（ASCII 镜像） | 15 suites，78 tests，0 failures/errors/skipped |
| Android API 35 | runner `OK (30 tests)`，0 failures；2 个物理/付费夹具 assumption skip |
| Lint Debug / Release | 各 0 errors，34 warnings |
| module boundary | PASS：3 modules / 4 routes / 1 Provider protocol / acyclic |
| security scan | PASS |
| backup exclusion | PASS：9 domains，allowBackup=false |
| emulator backup manager | PASS：`Backup is not allowed` |
| release signing | v2 PASS；1 signer；non-debuggable |
| physical install | `adb install -r` PASS；未卸载、未清数据 |
| Provider calls during acceptance | 0 |

签名证书 SHA-256：

`1B7B30A094D72F40A73B4ADA360012A39258BFD7600C8998FF18C677E1975561`

APK：

| artifact | bytes | SHA-256 |
|---|---:|---|
| `outputs/zhijuan-v9-debug.apk` | 33410073 | `2EA769906AB56EF7DAED021D0095C0DE10A570CAF2EC472588FE0125C0FEF88E` |
| `outputs/zhijuan-v9-release.apk` | 3024208 | `D16A4507CA38C975E396D8499145F1FDE9FA93CBE3B152A8FB5BC65C2570F8FB` |

外部产物：

- `D:/gptuser/projects/zhijuan-change-requests/CHANGE-REQUEST-2026-08-21-05-NAVIGATION-PACE-IMMERSIVE.md`
- `D:/gptuser/projects/zhijuan-change-requests/deliverables/v9-navigation-pace/zhijuan-v9-debug.apk`
- `D:/gptuser/projects/zhijuan-change-requests/deliverables/v9-navigation-pace/zhijuan-v9-release.apk`
- `D:/gptuser/projects/zhijuan-change-requests/evidence/zhijuan-v9-bottom-nav.png`

## 主要改动文件

- `core/.../S0Domain.kt`：剧情节奏类型、项目/任务字段与保存契约。
- `core/.../S2Continuity.kt`：项目节奏进入章节任务。
- `data/.../FileS0NovelRepository.kt`：schema 1.2、旧书兼容、节奏保存锁。
- `data/.../OpenAiCompatibleS1Provider.kt`：剧情节奏与加强后的“沉浸”正文规则。
- `app/.../S0App.kt`：有限返回栈、节奏管理接线、底栏 `6dp` 留白。
- `app/.../S5TextProjectUi.kt`：新建和已有书的三档节奏 UI。
- JVM 与 AndroidTest：导航、迁移、持久化、prompt 证据和选中态回归。

## 下一入口

如需继续验证真实长篇质量，先定义一本可专用测试的书、少量章节对照方案和 API 费用上限。不得把本节点的 prompt 字符串测试写成第三方模型已按要求生成的证据。

