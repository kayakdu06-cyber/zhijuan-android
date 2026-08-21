---
document_type: V5_NODE_REPORT
marker: V5-NODE-REPORT
node_id: V5-RELEASE-REGRESSION
status: COMPLETE
date: 2026-08-21
requirements: [PV-002, PV-004, PV-005, PV-006, PV-009, PV-010, PV-011, PV-012, PV-016]
change_requests: [ZJ-CR-01, ZJ-CR-02, ZJ-CR-03, ZJ-CR-04, ZJ-CR-05, ZJ-CR-06, ZJ-CR-07, ZJ-CR-08, ZJ-CR-09, ZJ-CR-10, ZJ-CR-11, ZJ-CR-12, ZJ-CR-13]
search_tags: [v5-release, full-regression, apk, lint, physical-install, api35, security-scan]
---

# 节点报告：V5 变更集发布回归

## 完成范围

`ZJ-CR-01` 至 `ZJ-CR-13` 已全部实现并形成四个前置节点：

1. `V5-CR07-INTEGRITY`：非 `stop` 正文只保存未完成草稿，不结算、不提交。
2. `V5-READER-NAV`：逐层返回、阅读纸色 `#EEECDF`、移除重复三点、中央轻触工具栏、已提交章节连续阅读。
3. `V5-PROJECT-PRESETS`：题材/基调预设、生成接线、暖纸项目菜单和强化选中态。
4. `V5-WRITING-SKILL`：单个 Markdown/严格 JSON 转项目质量卡，只注入正文并带哈希证据。
5. `V5-SEQUENTIAL-BATCH`：用户明确选择 1/2/3 章，逐章独立 Job 与两调用，失败即停。

产品仍为“织卷”；没有语音、账号、后台服务端、云同步、广告、Room、RAG、向量库、多智能体、富文本或分支版本系统。

## 最终自动化证据

- JVM ASCII 镜像：64 项，0 失败，0 跳过。
- API 35 全套 Android 仪器测试：26 项，0 失败；2 项按设计条件跳过（实体五章归档夹具、手工真实 Provider Harness）。
- `verify-build.ps1 -Offline`：通过；包含完整性、JVM、debug/release、Security Scan 与备份排除策略。
- `:app:lintDebug :app:lintRelease`：两者均 0 error、26 warning；warning 为既有非阻断建议。
- 模块边界：`:app/:core/:data` 恰好 3 个；顶级路由 4；Provider 协议 1；依赖图无环。
- release 签名：APK Signature Scheme v2 通过，1 个签名者，release 不可调试。
- API 35 release 覆盖安装成功；冷启动 1354 ms，AndroidRuntime fatal 0。
- 品牌源 SHA-256 保持 `AD63D0BB3EBD000ADEBEBE5F1F72C5DAC101DD7DD608A94AED3AB1EDF4DBB8BA`。

## APK

- debug：`outputs/zhijuan-v5-debug.apk`，32,998,081 bytes，SHA-256 `AE0B71FA331D652922D57DDAC49AFEBFD4E3B75DCB1C2B40C2BC38E682A5A46E`。
- release：`outputs/zhijuan-v5-release.apk`，2,991,440 bytes，SHA-256 `5EDD2039AA31E718FCA9088B25D2858D3CC0CD15C492B1E19425ECF19FFA139B`。

## 实体手机安装结果

- 设备 `d2b15cce` 在线。
- 已执行保留数据的 `adb install -r`，系统返回 `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`。
- 未卸载实体手机上的旧包、未清除应用数据、未绕过 OEM 授权弹窗；因此 V5 release APK 尚未覆盖到实体手机。
- 此项是设备安装授权门禁，不是构建、签名或 APK 错误；同一 release APK 已在 API 35 模拟器安装并冷启动通过。

## 能力边界

- 创作 Skill 可保证当前有效质量卡随每次正文请求发送并记录名称/版本/哈希，但不能让 API 永久学习，也不承诺模型 100% 遵循。
- 内容过滤由所选 Provider 决定；织卷不会规避 Provider 策略。技术性截断、资源中断和网络中断可被识别并安全保留草稿。
- 2/3 章模式不会并行，不会一次 Prompt 写多章；它减少的是用户逐章人工校准机会，因此默认仍为 1 章。

## 最终状态

软件节点 `V5-CHANGESET-CR01-13` 完成；唯一外部未落地项是实体手机安装授权。后续智能体从 `docs/ai/PROJECT-STATE.json` 和本报告进入，不应重复实现本变更集。
