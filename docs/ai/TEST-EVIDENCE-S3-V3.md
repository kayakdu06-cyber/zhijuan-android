# V3-S3 测试证据

检索标记：`S3-TEST-EVIDENCE` `foreground-service` `active-job` `settlement-only-retry` `reader-preferences`。

日期：2026-08-20。项目根：`D:/deepseekuser/projects/织卷1`。版本控制：`NONE`。

## 自动化

- JVM：33/33（Core 15、Data 16、App 2），failures=0。
- API 35 App 仪器：8/8；在 `POST_NOTIFICATIONS` 被拒绝时前台服务仍可启动并安全落盘检查点。
- 阅读压力组合：3/3；568×320 dp、1.3 字体倍率、深色模式，覆盖目录、摘要隐藏、字号/行距/主题选择。
- API 36 真机阅读偏好持久化：1/1。
- API 35 前台服务：声明 `dataSync`；Provider 未配置的受控失败没有联网，`jobs/active.json` 保留阶段/错误且不含 Authorization 或 Prompt。
- API 35 进程重建：强停前后 `jobs/active.json` 均存在；冷启动 `TotalTime=1205ms`，fatal=0，恢复审计不自动重发。

## 恢复与调用预算

- `S3RecoveryAuditor` 覆盖：可读草稿→只重试结算；未知正文结果→确认后重发；已提交章→清理陈旧活动任务。
- `retrySettlement` 测试证明：正文调用=0、结算调用=1、复用同一正文/任务 ID，提交后状态推进。
- 正常章检查点顺序精确为 `PREPARE → PROSE_REQUEST → PROSE_SAVED → SETTLEMENT_REQUEST → VALIDATE → COMMIT → DONE`，正常调用预算仍为 1+1。

## 构建、设备与安全

- Debug/Release（R8 + lint vital）：PASS。
- Debug SHA-256：`8A9AFBAF0521841FE061B0505F788F8578133982442A1F7AF416DACA5F5249F6`。
- Release unsigned SHA-256：`50C58C664420A81F44EDF5B2FBEACCE651CA1EB2BBFE537AEEA6034B1DEE43F6`。
- API 36 真机覆盖安装保留既有第 1 章、state/plan/events 和 Provider 设置；冷启动 `TotalTime=326ms`，fatal=0。
- 项目完整性、模块边界、备份排除、安全扫描（4 APK）和规格 lint：全部 PASS。

## 边界

本节点没有发起真实模型请求。物理设备的 Compose 组合测试因双屏系统自动化会话未完成，不据此声明 PASS；API 35 组合测试和 API 36 持久化/冷启动分别作为证据，完整 TalkBack/真机五章留在 S4 发布门禁。
