# 织卷第三方组件说明

检索标记：`ZHIJUAN-THIRD-PARTY-NOTICES`  
审查日期：2026-08-20

织卷是个人单设备、本地优先 Android 应用。以下版本以 `gradle/libs.versions.toml` 为准；本文件记录直接依赖，传递依赖由对应组件的依赖清单与许可证共同约束。

## 随 APK 分发的组件

| 组件 | 版本 | 用途 | 许可证 |
|---|---:|---|---|
| AndroidX Core KTX | 1.17.0 | Android 平台 Kotlin 扩展 | Apache License 2.0 |
| AndroidX Activity Compose | 1.13.0 | Activity 与 Compose 集成 | Apache License 2.0 |
| Jetpack Compose UI / Foundation / Material 3 | BOM 2026.06.00 | 原生界面、排版与控件 | Apache License 2.0 |
| Kotlin / Compose Compiler Plugin | 2.3.21 | Kotlin 运行时与 Compose 编译支持 | Apache License 2.0 |
| kotlinx.coroutines | 1.10.2 | 协程、流式生成与取消 | Apache License 2.0 |
| kotlinx.serialization JSON | 1.8.1 | 本地 JSON 与协议解析 | Apache License 2.0 |
| OkHttp | 5.3.0 | HTTPS 与 SSE 网络传输 | Apache License 2.0 |

## 仅构建或测试使用

| 组件 | 版本 | 用途 | 许可证 |
|---|---:|---|---|
| Android Gradle Plugin | 9.2.1 | Android 构建 | Apache License 2.0 |
| AndroidX Test Core / Runner / JUnit | 1.7.0 / 1.7.0 / 1.3.0 | 仪器测试 | Apache License 2.0 |
| JUnit Jupiter / Platform | 5.13.4 | JVM 测试 | Eclipse Public License 2.0 |
| MockWebServer3 | 5.3.0 | 本地 HTTP/SSE 契约测试 | Apache License 2.0 |

## 范围审查

- 未引入语音、录音、朗读、TTS、媒体播放或音频处理依赖。
- 未引入 Room、RAG、向量库、多智能体、富文本编辑器、账号、云同步、广告或统计 SDK。
- 品牌图标来自项目已有 `branding/selected/zhijuan-logo-draft.png`，不是第三方替换素材。
- 完整许可证原文可从各依赖包的 `META-INF`、上游发行包或源码发行页取得；分发时不得移除上游要求保留的版权与许可证声明。
