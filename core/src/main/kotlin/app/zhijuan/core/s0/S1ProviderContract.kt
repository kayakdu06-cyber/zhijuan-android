package app.zhijuan.core.s0

import java.net.URI

class S1ProviderSetupInput(
    val baseUrl: String,
    val apiKey: CharArray,
    val model: String,
    val connectTimeoutSeconds: Int = 15,
    val readTimeoutSeconds: Int = 180,
    val totalTimeoutSeconds: Int = 300,
    val maxProseCharacters: Int = 12_000,
    val profileId: String? = null,
    val displayName: String = model,
    val kind: S1ProviderKind = S1ProviderKind.OPENAI_COMPATIBLE,
) {
    override fun toString(): String =
        "S1ProviderSetupInput(baseUrl=$baseUrl, apiKey=<redacted>, model=$model, " +
            "connectTimeoutSeconds=$connectTimeoutSeconds, readTimeoutSeconds=$readTimeoutSeconds, " +
            "totalTimeoutSeconds=$totalTimeoutSeconds, maxProseCharacters=$maxProseCharacters, " +
            "profileId=$profileId, displayName=$displayName, kind=$kind)"
}

enum class S1ProviderKind {
    DEEPSEEK,
    QWEN,
    GLM,
    KIMI,
    OPENAI_COMPATIBLE,
}

data class S1ProviderPreset(
    val kind: S1ProviderKind,
    val displayName: String,
    val baseUrl: String,
    val models: List<String>,
)

object S1ProviderDefaults {
    const val DISPLAY_NAME = "DeepSeek V4 Pro"
    const val BASE_URL = "https://api.deepseek.com"
    const val MODEL = "deepseek-v4-pro"
    const val CONNECT_TIMEOUT_SECONDS = 15
    const val READ_TIMEOUT_SECONDS = 180
    const val TOTAL_TIMEOUT_SECONDS = 300
    const val MAX_PROSE_CHARACTERS = 12_000

    fun setupInput(apiKey: CharArray): S1ProviderSetupInput = S1ProviderSetupInput(
        baseUrl = BASE_URL,
        apiKey = apiKey,
        model = MODEL,
        connectTimeoutSeconds = CONNECT_TIMEOUT_SECONDS,
        readTimeoutSeconds = READ_TIMEOUT_SECONDS,
        totalTimeoutSeconds = TOTAL_TIMEOUT_SECONDS,
        maxProseCharacters = MAX_PROSE_CHARACTERS,
        displayName = DISPLAY_NAME,
        kind = S1ProviderKind.DEEPSEEK,
    )
}

object S1ProviderPresets {
    val ALL = listOf(
        S1ProviderPreset(
            S1ProviderKind.DEEPSEEK,
            "DeepSeek",
            "https://api.deepseek.com",
            listOf("deepseek-v4-pro", "deepseek-v4-flash"),
        ),
        S1ProviderPreset(
            S1ProviderKind.QWEN,
            "通义千问",
            "https://dashscope.aliyuncs.com/compatible-mode/v1",
            listOf("qwen3-max", "qwen3-235b-a22b-instruct-2507"),
        ),
        S1ProviderPreset(
            S1ProviderKind.GLM,
            "智谱 GLM",
            "https://open.bigmodel.cn/api/paas/v4",
            listOf("glm-4.5", "glm-4.5-flash"),
        ),
        S1ProviderPreset(
            S1ProviderKind.KIMI,
            "Kimi",
            "https://api.moonshot.cn/v1",
            listOf("kimi-k2.6", "kimi-k2.5"),
        ),
        S1ProviderPreset(
            S1ProviderKind.OPENAI_COMPATIBLE,
            "中转站 / 其他兼容服务",
            "",
            emptyList(),
        ),
    )

    fun find(kind: S1ProviderKind): S1ProviderPreset = ALL.first { it.kind == kind }
}

data class S1NormalizedEndpoint(
    val baseUrl: String,
    val chatCompletionsUrl: String,
    val host: String,
    val path: String,
)

data class S1ProviderSummary(
    val providerId: String,
    val baseUrl: String,
    val normalizedChatCompletionsUrl: String,
    val model: String,
    val connectTimeoutSeconds: Int,
    val readTimeoutSeconds: Int,
    val totalTimeoutSeconds: Int,
    val maxProseCharacters: Int,
    val lastConnectionTestAt: String?,
    val displayName: String = model,
    val kind: S1ProviderKind = S1ProviderKind.OPENAI_COMPATIBLE,
)

enum class S1ProviderErrorCode {
    CFG_INVALID_ENDPOINT,
    AUTH_REJECTED,
    MODEL_UNAVAILABLE,
    NETWORK_OFFLINE,
    PROVIDER_RATE_LIMIT,
    PROVIDER_SERVER_ERROR,
    REQUEST_OUTCOME_UNKNOWN,
    PROSE_EMPTY,
    PROSE_LIMIT_EXCEEDED,
    PROSE_TRUNCATED_LENGTH,
    PROSE_CONTENT_FILTERED,
    PROSE_RESOURCE_INTERRUPTED,
    PROSE_FINISH_REASON_UNKNOWN,
    SETTLEMENT_NOT_JSON,
    SETTLEMENT_SCHEMA_INVALID,
    STORAGE_WRITE_FAILED,
    USER_CANCELLED,
}

data class S1ProviderFailure(
    val code: S1ProviderErrorCode,
    val safeMessage: String,
    val userAction: String,
    val retryable: Boolean,
)

class S1ProviderException(
    val failure: S1ProviderFailure,
    cause: Throwable? = null,
) : RuntimeException(failure.code.name, cause)

sealed interface S1ConnectionTestResult {
    data class Saved(
        val summary: S1ProviderSummary,
        val requestIdHash: String?,
        val durationMillis: Long,
    ) : S1ConnectionTestResult

    data class Failed(val failure: S1ProviderFailure) : S1ConnectionTestResult
}

enum class S1CancelResult {
    CANCEL_REQUESTED,
    ALREADY_REQUESTED,
    NOT_ACTIVE,
}

object S1RequestIds {
    fun prose(taskId: String): String = "$taskId:prose"

    fun settlement(taskId: String): String = "$taskId:settlement"

    fun connectionTest(providerId: String): String = "$providerId:connection-test"
}

object S1ProviderErrors {
    fun of(code: S1ProviderErrorCode): S1ProviderFailure = when (code) {
        S1ProviderErrorCode.CFG_INVALID_ENDPOINT -> S1ProviderFailure(
            code,
            "接口地址无效，请检查 HTTPS 地址。",
            "EDIT_PROVIDER",
            false,
        )
        S1ProviderErrorCode.AUTH_REJECTED -> S1ProviderFailure(
            code,
            "认证失败，请检查 API Key。",
            "EDIT_KEY",
            false,
        )
        S1ProviderErrorCode.MODEL_UNAVAILABLE -> S1ProviderFailure(
            code,
            "模型不可用，请检查模型名或接口权限。",
            "EDIT_MODEL",
            false,
        )
        S1ProviderErrorCode.NETWORK_OFFLINE -> S1ProviderFailure(
            code,
            "网络不可用，已保留当前进度。",
            "RETRY_SAFE_STAGE",
            true,
        )
        S1ProviderErrorCode.PROVIDER_RATE_LIMIT -> S1ProviderFailure(
            code,
            "接口暂时限流，请稍后重试。",
            "RETRY_LATER",
            true,
        )
        S1ProviderErrorCode.PROVIDER_SERVER_ERROR -> S1ProviderFailure(
            code,
            "接口暂时异常，已保留当前进度。",
            "RETRY_SAFE_STAGE",
            true,
        )
        S1ProviderErrorCode.REQUEST_OUTCOME_UNKNOWN -> S1ProviderFailure(
            code,
            "无法确认服务端是否已完成本次请求，请确认后再重发。",
            "CONFIRM_RESEND",
            false,
        )
        S1ProviderErrorCode.PROSE_EMPTY -> S1ProviderFailure(
            code,
            "本次未获得有效正文。",
            "RETRY_PROSE",
            true,
        )
        S1ProviderErrorCode.PROSE_LIMIT_EXCEEDED -> S1ProviderFailure(
            code,
            "正文超过安全长度，已停止接收并保留可用内容。",
            "REVIEW_DRAFT",
            false,
        )
        S1ProviderErrorCode.PROSE_TRUNCATED_LENGTH -> S1ProviderFailure(
            code,
            "正文达到模型输出上限，已保存为未完成草稿。",
            "RETRY_PROSE",
            true,
        )
        S1ProviderErrorCode.PROSE_CONTENT_FILTERED -> S1ProviderFailure(
            code,
            "Provider 已停止此内容，已保存收到的片段。",
            "REVIEW_DRAFT",
            false,
        )
        S1ProviderErrorCode.PROSE_RESOURCE_INTERRUPTED -> S1ProviderFailure(
            code,
            "Provider 资源中断，已保存收到的片段。",
            "RETRY_PROSE",
            true,
        )
        S1ProviderErrorCode.PROSE_FINISH_REASON_UNKNOWN -> S1ProviderFailure(
            code,
            "Provider 未确认正文自然结束，已保存收到的片段。",
            "RETRY_PROSE",
            true,
        )
        S1ProviderErrorCode.SETTLEMENT_NOT_JSON -> S1ProviderFailure(
            code,
            "正文已保存，但状态整理格式无效。",
            "EXPLICIT_RETRY_SETTLEMENT",
            true,
        )
        S1ProviderErrorCode.SETTLEMENT_SCHEMA_INVALID -> S1ProviderFailure(
            code,
            "正文已保存，但状态整理缺少必要信息。",
            "EXPLICIT_RETRY_SETTLEMENT",
            true,
        )
        S1ProviderErrorCode.STORAGE_WRITE_FAILED -> S1ProviderFailure(
            code,
            "无法安全保存，请检查存储空间。",
            "FREE_SPACE_AND_RETRY",
            true,
        )
        S1ProviderErrorCode.USER_CANCELLED -> S1ProviderFailure(
            code,
            "已停止生成，已保存的正文不会被删除。",
            "RETRY_OR_DISCARD",
            true,
        )
    }

    fun configurationUnavailable(): S1ProviderFailure = of(S1ProviderErrorCode.CFG_INVALID_ENDPOINT)
}

object S1ProviderSettingsValidator {
    fun normalizeEndpoint(rawBaseUrl: String, allowHttpForLocalTests: Boolean = false): Result<S1NormalizedEndpoint> =
        runCatching {
            val trimmed = rawBaseUrl.trim()
            require(trimmed.isNotEmpty())
            val parsed = URI(trimmed)
            val scheme = parsed.scheme?.lowercase()
            require(scheme == "https" || (allowHttpForLocalTests && scheme == "http"))
            require(!parsed.host.isNullOrBlank())
            require(parsed.userInfo == null && parsed.query == null && parsed.fragment == null)
            val rawPath = parsed.rawPath.orEmpty().ifBlank { "" }
            val normalizedPath = if (rawPath.endsWith("/chat/completions")) {
                rawPath
            } else {
                rawPath.trimEnd('/') + "/chat/completions"
            }
            val normalizedUri = URI(
                scheme,
                null,
                parsed.host,
                parsed.port,
                normalizedPath,
                null,
                null,
            )
            val normalizedBaseUri = URI(
                scheme,
                null,
                parsed.host,
                parsed.port,
                rawPath.ifEmpty { null },
                null,
                null,
            )
            S1NormalizedEndpoint(
                baseUrl = normalizedBaseUri.toASCIIString(),
                chatCompletionsUrl = normalizedUri.toASCIIString(),
                host = parsed.host,
                path = normalizedPath,
            )
        }

    fun validate(
        input: S1ProviderSetupInput,
        allowHttpForLocalTests: Boolean = false,
        allowStoredCredential: Boolean = false,
    ): Result<S1NormalizedEndpoint> =
        normalizeEndpoint(input.baseUrl, allowHttpForLocalTests).mapCatching { normalized ->
            require(
                (allowStoredCredential && input.apiKey.isEmpty()) ||
                    (input.apiKey.size in 8..16_384 && input.apiKey.none(Char::isWhitespace)),
            )
            require(input.model.isNotBlank() && input.model.length <= 200)
            require(input.displayName.isNotBlank() && input.displayName.length <= 80)
            input.profileId?.let { require(it.matches(Regex("^provider_[A-Za-z0-9_-]{4,}$"))) }
            require(input.connectTimeoutSeconds in 5..60)
            require(input.readTimeoutSeconds in 30..600)
            require(input.totalTimeoutSeconds in 60..1_800)
            require(input.maxProseCharacters in 1_000..30_000)
            normalized
        }
}
