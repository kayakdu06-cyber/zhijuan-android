---
document_type: V5_NODE_REPORT
marker: V5-NODE-REPORT
node_id: V5-READER-NAV
status: COMPLETE
date: 2026-08-21
requirements: [PV-002, PV-011, PV-016]
change_requests: [ZJ-CR-01, ZJ-CR-09, ZJ-CR-10, ZJ-CR-11, ZJ-CR-12]
search_tags: [back-stack, reader-paper, immersive-reader, directory, auto-next, committed-chapter]
---

# 节点报告：逐层返回与连续阅读

## 可见结果

- 从书库进入阅读后，顶部返回键和 Android 返回手势回书库；从创作页进入则回创作页，不再固定跳到某一顶级页。
- 阅读页浅色纸张固定为 `#EEECDF`；深色阅读主题继续使用深色背景，状态栏和导航栏颜色与当前阅读背景一致。
- 去掉阅读页右上角重复的三点/目录入口；目录只保留在底部阅读工具栏。
- 轻触正文中央安全区可隐藏或显示顶部、底部应用工具栏，正文布局不随工具栏显隐跳动；系统状态栏、系统导航/手势区域不被隐藏。
- 当前章读到底后继续向上滑动超过 `48dp`，仅当紧邻下一章状态为 `COMMITTED` 时自动进入下一章并从顶部开始；未完成章节不会自动进入。
- 章末会区分“继续上滑进入下一章”“下一章尚未完成”“已读至当前最新章节”。

## 交互与边界

- 中央轻触区限定为页面横向 `20%–80%`、纵向 `18%–82%`，降低与顶部返回、底部按钮及边缘返回手势冲突。
- 自动续章要求到达当前滚动最大值后再产生一次明确向上拖动，不会仅因正文首次到达底部就跳章。
- 目录弹层优先消费 Android 返回手势；关闭目录后再次返回才离开阅读页。
- 顶部返回触控目标为 `48dp`，所有底部动作保持 `48dp` 高并提供语义标签。

## 主要改动文件

- `app/src/main/kotlin/app/zhijuan/reader/S0App.kt`
- `app/src/main/kotlin/app/zhijuan/reader/S0Theme.kt`
- `app/src/androidTest/kotlin/app/zhijuan/reader/S0ReaderScreenTest.kt`

## 证据

- `:app:compileDebugKotlin :app:compileDebugAndroidTestKotlin`：通过。
- `:app:assembleDebug :app:assembleDebugAndroidTest`：通过。
- API 35 模拟器 `S0ReaderScreenTest`：8 项，0 失败；新增自动续章和中央工具栏显隐测试均通过。
- 自动测试同时复核：目录切章、上下章边界、阅读位置恢复、结算摘要不可见、阅读设置及书库备份入口。
- 真机存在旧签名的同名 debug 包，按保留用户数据原则未卸载；本节点不以破坏真机数据换取安装测试。

## 安全边界

- 没有新增顶级路由、模块、Provider 或模型调用。
- 自动续章只切换本地已提交正文，不触发自动生成。
- 不隐藏 Android 系统栏，不接管左右边缘手势。

## 下一入口

继续 `V5-PROJECT-PRESETS`：统一项目菜单与选中态，加入题材/基调预设并保持可编辑自定义值。
