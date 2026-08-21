---
document_type: V3_NODE_REPORT
marker: V3-NODE-REPORT
node_id: V3-SEPARATION
status: COMPLETE
date: 2026-08-20
version_control: NONE
next_node: V3-S1-REAL-CONNECTION
search_tags: [legacy-cleanup, three-modules, standard-source-layout, no-git, brand-preserved]
---

# 节点报告：V3 项目彻底剥离

## 结论

当前项目已在原目录内完成独立化：只保留 V3 三模块实现、品牌资源、Gradle 工具链和 V3 节点文档。旧工程源码、任务历史、数据库 schema、旧 Provider/feature 模块、旧密钥脚本、Git 与 GitNexus 元数据均已删除。

## 删除范围

- 旧模块：`feature/`、旧 `provider/`。
- 旧数据层：Room schema、SQLCipher 说明与相关依赖目录。
- 旧代码：三个现有模块中未进入 V3 source set 的 Kotlin/测试/debug 组件。
- 旧文档：编号规格、TASK 包、工作报告、旧上下文、ADR/历史/评审目录。
- 旧工具：明文密钥辅助脚本、后台/前台探针、旧自动运行脚本。
- 元数据与缓存：`.git`、`.gitnexus`、旧 Gradle/Kotlin/build 缓存。

脚本记录的最低删除量为 `9,942 files / 610,504,090 bytes`；标准源码布局迁移期间删除的旧模块内源码与一次性脚本未重复计入该数字。

## 保留与迁移

- V3 源码从临时 `src/s0` 逐文件 SHA-256 校验后迁至标准 `src/main`、`src/test`、`src/androidTest`。
- 两份当时未验证的 S1 测试迁移前后哈希一致，随后已完成测试。
- 保留全部 `branding/`；App 使用的 Logo 与 `branding/selected/zhijuan-logo-draft.png` 哈希相同：`AD63D0BB3EBD000ADEBEBE5F1F72C5DAC101DD7DD608A94AED3AB1EDF4DBB8BA`。
- 依赖目录已移除 Room、SQLCipher、Hilt、KSP、WorkManager 等旧版本项，只保留三模块实际使用项。

## 验证证据

| evidence_id | result |
|---|---|
| `SEP-BOUNDARY-001` | 3 modules、4 routes、1 Provider protocol、依赖无环 |
| `SEP-JVM-001` | Core 6 + Data 11 + App 1 = 18/18，0 failure |
| `SEP-API35-001` | Data 2/2、App 1/1 |
| `SEP-BUILD-001` | 无版本控制元数据后 Debug/Release clean assemble 成功 |
| `SEP-SECURITY-001` | 4 个扫描回归、源码与 2 个 APK 扫描、备份排除通过 |
| `SEP-BRAND-001` | selected Logo 与 APK 源资源 SHA-256 相同 |

最终产物：

- Debug：`C27A0172F34B5E03A6636AF0B169D020725068F1DEF5A005FF595E5D8A9C0D03`
- Release unsigned：`9AC17C48D7BF57B3384E42907F5C77BEC78145B587BF068ABE482713E7FD8233`

## 恢复边界

被删旧文件和版本历史无法从当前项目恢复；本节点没有创建旧工程副本。V3 源码、测试、品牌与规格引用均保留，`D:\gptuser` 下的权威规格未被修改。
