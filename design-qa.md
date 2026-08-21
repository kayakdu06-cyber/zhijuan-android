# 织卷 V4 编辑式 UI 视觉验收

日期：2026-08-21  
目标：将已确认的“风格 3”四张效果图作为同一设计系统，落实到现有原生 Android 工程。

## 对照基准

- 视觉真值：
  - `docs/design/v4-ui-proposal/references/library-reference.png`
  - `docs/design/v4-ui-proposal/references/workspace-reference.png`
  - `docs/design/v4-ui-proposal/references/directory-reference.png`
  - `docs/design/v4-ui-proposal/references/reader-reference.png`
- 最终实现截图：
  - `docs/design/v4-ui-proposal/implementation/library.png`
  - `docs/design/v4-ui-proposal/implementation/workspace.png`
  - `docs/design/v4-ui-proposal/implementation/directory.png`
  - `docs/design/v4-ui-proposal/implementation/reader.png`
  - `docs/design/v4-ui-proposal/implementation/settings.png`
- 全屏同图对照：`docs/design/v4-ui-proposal/comparison/*-full-comparison.png`
- 顶部聚焦对照：`docs/design/v4-ui-proposal/comparison/*-top-comparison.png`

## 视口与归一化

- 参考图：书库、创作、目录为 `853 × 1844 px`；阅读为 `852 × 1846 px`。
- Android 实现：`1080 × 2400 px`，`420 dpi`，约 `411 × 914 dp`。
- 对照板统一归一化为 `426 × 912 px`；参考和实现分别缩放后并排，不用设备外框或浏览器画布。
- 状态：浅色主题、本地项目《雾港铜钥》、已完成 4 章、当前计划为第 5 章；动态章数和字数与效果图示例不同，不判为视觉漂移。
- 原生 App 无浏览器控制台；最终冷启动后 `AndroidRuntime` 错误检查结果为 `NO_ANDROID_RUNTIME_ERRORS`。

## 必查表面

- 字体与排版：系统宋体回退承担书名、章名和正文，系统无衬线承担状态与操作；标题、正文、标签层级与参考一致。正文行高保留用户设置，未将结算摘要混入正文。
- 间距与布局：使用 4/8dp 节奏、20–28dp 页面边距、低圆角和细分隔线。创作页主按钮及目录/阅读入口在标准视口首屏可达。
- 颜色与令牌：暖纸色、石墨、朱砂主操作、苔绿完成态均由浅/深色语义令牌提供；暗色状态栏图标已独立校正。
- 图像与资产：只复用既有 `zhijuan_logo_draft`；结构图标使用统一的 Android Material 风格矢量图标。界面没有缺失的插画或照片槽位，因此没有为了装饰额外生成 Image 2 栅格素材。
- 文案与内容：保留“织卷”、本地优先、一次一章和明确计划刷新；“结算摘要”不出现在阅读视觉或语义树中。
- 交互与无障碍：底栏 3 项、48dp 触控、项目更多菜单、目录抽屉、上下章、末章返回创作、设置渐进展开均可操作；色彩状态同时有文字或图标。

## 对照历史

### 第 1 轮（blocked）

- [P2] 创作步骤的完成图标、编号和名称顺序与参考相反，当前步骤还重复显示数字。
  - 修复：统一为“编号 → 名称 → 状态图标”，缩短连接线并去掉重复圆形数字。
- [P2] 创作页主操作和次操作在标准视口首屏下方。
  - 修复：压缩页面节奏与步骤轨道，保留大章名，主操作及目录/阅读入口回到首屏。
- [P2] 阅读页顶部缺少参考中的“书名 / 章节进度 / 更多”层级，进度线错误使用章节比例。
  - 修复：重排顶部栏，改用阅读滚动进度和百分比；末章明确显示“返回创作”。

### 第 2 轮（blocked）

- [P2] 暗色主题状态栏仍使用深色系统图标。
  - 修复：`ZhijuanS0Theme` 同步状态栏、导航栏颜色和明暗图标模式；复拍 `library-dark.png` 后通过。
- [P2] 约 343dp 宽、系统字体 `1.3` 时，最近作品详情列过窄、换行过多。
  - 修复：小于 360dp 时使用较小封面、较紧间距、明确两行截断；复拍 `library-small-large-text.png` 后核心操作可见且无重叠。
- [P3] 书库图标轮廓不够像参考的开页书。
  - 修复：改为同一 Material 风格的开页书矢量；不再保留“暂停键”观感。

### 第 3 轮（passed）

- 标准竖屏、暗色、小屏大字和横屏均复拍。
- `library/workspace/directory/reader` 全屏对照无未解决 P0/P1/P2。
- 聚焦检查顶部品牌、标题、主动作、目录行、正文与底部工具栏，无裁切、重叠或失效控件。

## 响应与设备证据

- 标准竖屏：`implementation/library.png`、`workspace.png`、`directory.png`、`reader.png`。
- 暗色：`implementation/library-dark.png`。
- 小屏 + 字体 1.3：`implementation/library-small-large-text.png`。
- 横屏：`implementation/library-landscape.png`；内容保持可滚动、固定底栏可操作。
- 主要交互：书库切创作、顶部打开目录、目录关闭、继续阅读、目录切章、上一章/下一章/末章返回创作。

## 遗留 P3

- 参考效果图使用概念数据（1 或 5 章、约 1.8 万字），验收实现使用可重复的 4 章本地测试夹具；仅内容不同。
- Android 系统字体无法逐像素复现效果图中的定制中文宋体，但字体性格、层级和行距已一致，且没有引入额外字体许可风险。

final result: passed
