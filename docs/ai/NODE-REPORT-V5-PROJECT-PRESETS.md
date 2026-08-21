---
document_type: V5_NODE_REPORT
marker: V5-NODE-REPORT
node_id: V5-PROJECT-PRESETS
status: COMPLETE
date: 2026-08-21
requirements: [PV-002, PV-010, PV-016]
change_requests: [ZJ-CR-02, ZJ-CR-03, ZJ-CR-04, ZJ-CR-06]
search_tags: [genre-presets, tone-presets, editable-preset, project-menu, selection-state, hard-facts]
---

# 节点报告：项目预设、菜单与选中态

## 可见结果

- 新建小说提供 16 个主分类及各自具体细分类；细分类最多选择 3 项，关系与视角作为独立可选维度。
- 预设最终合成为兼容现有项目格式的文本，例如 `悬疑推理 / 规则怪谈 / 群像 / 无CP`；最终题材输入框仍可继续编辑，也可完全手写自定义题材。
- 基调提供 10 个预设，并明确说明它控制语气、节奏、描写密度和情绪温度，不改变题材、模型、篇幅或内容规则；选择后仍可继续编辑。
- 最终题材现已加入每章生成任务 `hardFacts`，不再只是书库展示字段；基调原有接线保留。
- 项目菜单改为暖纸色、1dp 轮廓、8dp 小圆角、紧凑 48dp 行高和线性图标；删除项有分隔线及危险色，不再使用默认淡紫松散菜单。
- 当前项目使用填充底色、左侧 4dp 标记、“当前”文字和加粗标题；底部导航增加稳定底标和加粗文字；目录当前章使用底色、侧标和“当前”文字。
- 题材/基调预设同时使用实色底、2dp 主色描边、勾选图标和加粗文字，并保留 `selected=true` 语义。

## 分类清单

主分类：玄幻、奇幻、武侠、仙侠、都市、现实、历史、军事、科幻、悬疑推理、游戏电竞、体育、古代言情、现代言情、幻想言情、轻小说。

独立关系：言情、纯爱、百合、无CP、多元；独立视角：男主、女主、双主角、群像、多视角、第一人称。自定义内容直接写入最终题材字段。

基调：克制冷峻、温暖治愈、轻快幽默、紧张凌厉、阴郁压迫、宏大史诗、浪漫唯美、现实质朴、荒诞讽刺、黑暗残酷。

## 主要改动文件

- `app/src/main/kotlin/app/zhijuan/reader/S5TextProjectUi.kt`
- `app/src/main/kotlin/app/zhijuan/reader/S6EditorialComponents.kt`
- `app/src/main/kotlin/app/zhijuan/reader/S0App.kt`
- `core/src/main/kotlin/app/zhijuan/core/s0/S2Continuity.kt`
- 对应 core 与 Compose 测试。

## 证据

- JVM ASCII 镜像回归：55 项，0 失败，0 跳过；题材和基调均进入生成硬事实。
- API 35 `S5TextProjectUiTest`：5 项，0 失败；覆盖预设合成、强选中语义、自由编辑、项目选择、菜单与删除确认。
- `:app:compileDebugKotlin :app:compileDebugAndroidTestKotlin`：通过。
- 菜单实机尺寸模拟证据：`docs/design/v4-ui-proposal/implementation/v5-project-menu.png`。
- 当前项目与底部导航证据：`docs/design/v4-ui-proposal/implementation/v5-library.png`。
- 新建预设证据：`docs/design/v4-ui-proposal/implementation/v5-create-presets.png`；后续已进一步把创建/计划 Bottom Sheet 强制为暖纸 `surface`，消除默认淡紫容器。

## 边界

- 分类使用本地静态通用名称，不联网、不复制平台品牌排序或作品数据。
- 项目仍只持久化一个 `genre: String` 和一个 `tone: String`，不引入迁移或新数据库。
- 没有新增模型调用；预设合成和初始计划仍为本地确定性逻辑。

## 下一入口

继续 `V5-WRITING-SKILL`：导入单个 Markdown/严格 JSON，确定性转换为项目质量卡，完成请求注入、哈希诊断、重启和归档闭环。
