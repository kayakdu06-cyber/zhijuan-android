---
document_type: V6_IMPLEMENTATION_PLAN
marker: V6-IMPLEMENTATION-PLAN
node_id: V6-CR14-20
status: COMPLETE
date: 2026-08-21
requirements: [PV-001, PV-002, PV-004, PV-009, PV-010, PV-011, PV-014, PV-015, PV-017]
change_requests: [ZJ-CR-14, ZJ-CR-15, ZJ-CR-16, ZJ-CR-17, ZJ-CR-18, ZJ-CR-19, ZJ-CR-20]
---

# V6 CR14–20 实施计划

## 基线

- `verify-build.ps1 -Offline`：PASS。
- 三个模块、四个顶级路由、单 Provider 协议：PASS。
- JVM 基线 64/0；debug/release 构建和安全扫描：PASS。
- 实体手机 `d2b15cce` 与 API 35 模拟器在线。
- Git：NONE；不执行 reset/checkout。

## 改动顺序

1. Skill 来源上限迁移至 256 KiB，兼容通用 Markdown/JSON，并提供本地候选规则编辑；最终质量卡仍为 8 条/1600 字符。
2. Provider 单配置文件原地迁移为多配置档案；保持一套 OpenAI-compatible 协议与每档独立 Keystore 凭据。
3. 底部导航改为书库/设置；生成路由保留但只由书库和阅读页进入，并恢复逐层返回。
4. 所有用户可见章节标题只显示 `第 N 章`；内部计划标题继续存储并进入生成上下文。
5. 阅读页右上增加“续写”，复用 1/2/3 章顺序队列；生成页不展示内部规划、摘要或线索。
6. 运行 JVM、仪器、lint、debug/release、边界与安全扫描；覆盖安装实体手机并执行关键旅程。

## 不做

- 不新增无障碍专项功能或专项验收；不删除既有语义。
- 不新增路由、模块、Provider 协议、Room、RAG、向量库、第三次普通章调用或并行生成。
- 不在前台展示计划方向、内部摘要、关键线索或质量卡细节。

## 完成条件

- 旧单 API 配置无损迁移，多 API 可新增、验证、切换、编辑与删除，活动任务锁定配置。
- 通用 `.md/.json` 可产生可编辑候选；256 KiB 边界、最终质量卡边界和安全拒绝有测试。
- 两项底栏、阅读续写、逐层返回、纯章节序号与无规划生成页有仪器证据。
- 所有相关自动化通过，APK 覆盖安装并冷启动；无用户数据清除。

## 完成记录

- JVM：71 项，0 失败；包含 50 章无费用影子长篇回归。
- API 35 仪器：29 项被报告，27 项执行、2 项按设计跳过、0 失败。
- debug/release lint：均 0 error、26 warning；完整构建、安全和备份门禁通过。
- release 已用 `adb install -r` 覆盖安装实体手机 `d2b15cce`，保留数据；冷启动 111 ms，fatal 0。
- 真机续写面板已验证可以滚动到确认按钮；没有确认生成，真实模型调用为 0。
- 详细证据：`docs/ai/NODE-REPORT-V6-CR14-20.md`。
