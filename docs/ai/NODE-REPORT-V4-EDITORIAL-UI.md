---
document_type: V4_NODE_REPORT
marker: V4-NODE-REPORT
node_id: V4-EDITORIAL-UI
status: COMPLETE
date: 2026-08-21
requirements: [PV-010, PV-011, PV-016]
search_tags: [editorial-ui, style-3, library, generation, directory, reader, settings, compose, visual-qa]
---

# 节点报告：V4 编辑式界面系统

状态：`COMPLETE`。本节点只改用户界面与测试夹具，没有修改生成系统、Provider、提示词、文件格式或提交协议。

## 可见结果

- 四张“风格 3”效果图被实现为一套界面，不是三个选项：暖纸底、石墨正文、朱砂主动作、苔绿完成态、衬线长文排版、细分隔线和低圆角。
- 书库：当前作品封面、最近章节、完成进度、当前任务、继续创作/阅读、多项目菜单、创建和导入导出入口。
- 创作：故事设定/章节计划/当前写作三步轨道、当前章任务、关键线索、单一主要操作、目录和阅读入口。
- 目录：保持为 Bottom Sheet，不新增第五路由；完成章、当前章、后续计划与唯一主动作统一呈现。
- 阅读：书名/章节进度/更多顶部栏、纯正文、滚动进度、上一章/目录/下一章；末章返回创作。
- 设置：保留只填 API Key 的快速路径，兼容服务和参数继续渐进展开；视觉令牌与其余页面一致。

## 保持不变的硬边界

- Gradle 模块仍恰好为 `:app`、`:core`、`:data`；顶级路由仍为 4 个，底栏仍为 3 项。
- Provider 协议仍为 1 个；一次只生成 1 章，普通章节仍恰好正文 1 调用 + 结构化结算 1 调用。
- 正文仍先保存为可读草稿，再结算并经 PendingCommit 幂等提交。
- 没有新增 Room、RAG、向量库、多智能体、富文本、云同步、语音、自动连写或隐藏调用。
- 结算摘要仍保存在章节元数据中供连续性使用，但阅读页和无障碍语义树均不显示。

## 主要改动文件

- `app/src/main/kotlin/app/zhijuan/reader/S0Theme.kt`
- `app/src/main/kotlin/app/zhijuan/reader/S6EditorialComponents.kt`
- `app/src/main/kotlin/app/zhijuan/reader/S0App.kt`
- `app/src/main/kotlin/app/zhijuan/reader/S5TextProjectUi.kt`
- `app/src/main/kotlin/app/zhijuan/reader/S1ProviderSettingsScreen.kt`
- `app/src/main/res/drawable/ic_*.xml`（统一 Material 风格图标）
- `app/src/androidTest/kotlin/app/zhijuan/reader/S0ReaderScreenTest.kt`
- `app/src/androidTest/kotlin/app/zhijuan/reader/S5TextProjectUiTest.kt`
- `app/src/androidTest/kotlin/app/zhijuan/reader/S6VisualFixtureSeederTest.kt`

`core`、`data` 和 Provider 实现未改。

## 验证结果

- `:core:test + :data:testDebugUnitTest + :app:testDebugUnitTest`：51 项，0 失败，`ASCII_MIRROR_TESTS_OK`。
- `:app:connectedDebugAndroidTest`：19 项，0 失败，2 项外部条件测试按设计跳过。
- `:app:assembleDebug`：`BUILD SUCCESSFUL`。
- 冷启动 AndroidRuntime：`NO_ANDROID_RUNTIME_ERRORS`。
- 视觉 QA：标准竖屏、暗色、小屏字体 1.3、横屏均复拍；最终 `design-qa.md` 为 `passed`。

## 交付物

- APK：`outputs/zhijuan-v4-editorial-debug.apk`
- APK SHA-256：`5165F7254D7399F32FB94FD0A743C8A2ADBAC21BC9E85382A778B7F07EDAC6AD`
- 最终截图：`docs/design/v4-ui-proposal/implementation/`
- 参考图：`docs/design/v4-ui-proposal/references/`
- 并排证据：`docs/design/v4-ui-proposal/comparison/`
- 视觉验收：`design-qa.md`

## Image 2 使用结论

效果图中没有缺失的照片、插画、纹理或不可矢量化品牌素材。节点复用既有织卷 Logo，并用标准平台矢量图标完成其余结构，因此没有为了装饰强行生成新的 Image 2 栅格资产；若后续效果图出现真实插画槽位，再按实际尺寸生成。

## 后续入口

UI 节点已经完成。下一阶段不得自动重写生成系统；先由用户确认此 APK/截图，再决定是否进入新的写作系统功能节点。
