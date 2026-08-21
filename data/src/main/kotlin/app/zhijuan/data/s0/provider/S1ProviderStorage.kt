package app.zhijuan.data.s0.provider

import app.zhijuan.core.s0.S1ProviderSummary
import app.zhijuan.core.s0.S1ProviderKind
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal data class S1StoredProviderSettings(
    val providerId: String,
    val baseUrl: String,
    val normalizedChatCompletionsUrl: String,
    val model: String,
    val credentialAlias: String,
    val connectTimeoutSeconds: Int,
    val readTimeoutSeconds: Int,
    val totalTimeoutSeconds: Int,
    val maxProseCharacters: Int,
    val lastConnectionTestAt: String?,
    val displayName: String = model,
    val kind: S1ProviderKind = S1ProviderKind.OPENAI_COMPATIBLE,
) {
    fun summary(): S1ProviderSummary = S1ProviderSummary(
        providerId = providerId,
        baseUrl = baseUrl,
        normalizedChatCompletionsUrl = normalizedChatCompletionsUrl,
        model = model,
        connectTimeoutSeconds = connectTimeoutSeconds,
        readTimeoutSeconds = readTimeoutSeconds,
        totalTimeoutSeconds = totalTimeoutSeconds,
        maxProseCharacters = maxProseCharacters,
        lastConnectionTestAt = lastConnectionTestAt,
        displayName = displayName,
        kind = kind,
    )
}

private data class S1StoredProviderCollection(
    val activeProfileId: String?,
    val profiles: List<S1StoredProviderSettings>,
)

internal class S1ProviderSettingsStore(
    private val file: File,
) {
    fun load(): S1StoredProviderSettings? = synchronized(lock) {
        val collection = readCollection()
        collection.profiles.firstOrNull { it.providerId == collection.activeProfileId }
            ?: collection.profiles.firstOrNull()
    }

    fun loadAll(): List<S1StoredProviderSettings> = synchronized(lock) { readCollection().profiles }

    fun find(profileId: String): S1StoredProviderSettings? = synchronized(lock) {
        readCollection().profiles.firstOrNull { it.providerId == profileId }
    }

    fun save(settings: S1StoredProviderSettings) = synchronized(lock) {
        val current = readCollection()
        val profiles = (current.profiles.filterNot { it.providerId == settings.providerId } + settings)
            .takeLast(MAX_PROFILES)
        writeCollection(S1StoredProviderCollection(settings.providerId, profiles))
    }

    fun select(profileId: String): S1StoredProviderSettings = synchronized(lock) {
        val current = readCollection()
        val selected = current.profiles.firstOrNull { it.providerId == profileId }
            ?: error("PROVIDER_PROFILE_NOT_FOUND")
        writeCollection(current.copy(activeProfileId = profileId))
        selected
    }

    fun remove(profileId: String): S1StoredProviderSettings = synchronized(lock) {
        val current = readCollection()
        val removed = current.profiles.firstOrNull { it.providerId == profileId }
            ?: error("PROVIDER_PROFILE_NOT_FOUND")
        val remaining = current.profiles.filterNot { it.providerId == profileId }
        val nextActive = when {
            current.activeProfileId != profileId -> current.activeProfileId
            else -> remaining.firstOrNull()?.providerId
        }
        writeCollection(S1StoredProviderCollection(nextActive, remaining))
        removed
    }

    private fun readCollection(): S1StoredProviderCollection {
        if (!file.isFile) return S1StoredProviderCollection(null, emptyList())
        require(file.length() in 1..MAX_SETTINGS_BYTES)
        val root = Json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject
        return when (root.requiredString("schemaVersion")) {
            LEGACY_SCHEMA_VERSION -> {
                require(root.keys == LEGACY_KEYS)
                val legacy = root.toStoredSettings(
                    displayName = if (root.requiredString("model") == "deepseek-v4-pro") "DeepSeek V4 Pro" else root.requiredString("model"),
                    kind = if (root.requiredString("baseUrl").contains("deepseek.com")) {
                        S1ProviderKind.DEEPSEEK
                    } else {
                        S1ProviderKind.OPENAI_COMPATIBLE
                    },
                )
                S1StoredProviderCollection(legacy.providerId, listOf(legacy))
            }
            SCHEMA_VERSION -> {
                require(root.keys == COLLECTION_KEYS)
                val profiles = root.getValue("profiles").jsonArray.map { element ->
                    val item = element.jsonObject
                    require(item.keys == PROFILE_KEYS)
                    item.toStoredSettings(
                        displayName = item.requiredString("displayName"),
                        kind = S1ProviderKind.valueOf(item.requiredString("kind")),
                    )
                }
                require(profiles.size <= MAX_PROFILES && profiles.map { it.providerId }.distinct().size == profiles.size)
                val active = root["activeProfileId"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
                require(active == null || profiles.any { it.providerId == active })
                S1StoredProviderCollection(active, profiles)
            }
            else -> error("PROVIDER_SETTINGS_VERSION_UNSUPPORTED")
        }
    }

    private fun JsonObject.toStoredSettings(displayName: String, kind: S1ProviderKind) = S1StoredProviderSettings(
        providerId = requiredString("providerId"),
        baseUrl = requiredString("baseUrl"),
        normalizedChatCompletionsUrl = requiredString("normalizedChatCompletionsUrl"),
        model = requiredString("model"),
        credentialAlias = requiredString("credentialAlias"),
        connectTimeoutSeconds = requiredInt("connectTimeoutSeconds"),
        readTimeoutSeconds = requiredInt("readTimeoutSeconds"),
        totalTimeoutSeconds = requiredInt("totalTimeoutSeconds"),
        maxProseCharacters = requiredInt("maxProseCharacters"),
        lastConnectionTestAt = this["lastConnectionTestAt"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content,
        displayName = displayName,
        kind = kind,
    )

    private fun writeCollection(collection: S1StoredProviderCollection) {
        val payload = buildJsonObject {
            put("schemaVersion", SCHEMA_VERSION)
            if (collection.activeProfileId != null) {
                put("activeProfileId", collection.activeProfileId)
            } else {
                put("activeProfileId", JsonNull)
            }
            put("profiles", buildJsonArray {
                collection.profiles.forEach { settings ->
                    add(buildJsonObject {
                        put("providerId", settings.providerId)
                        put("displayName", settings.displayName)
                        put("kind", settings.kind.name)
                        put("baseUrl", settings.baseUrl)
                        put("normalizedChatCompletionsUrl", settings.normalizedChatCompletionsUrl)
                        put("model", settings.model)
                        put("credentialAlias", settings.credentialAlias)
                        put("connectTimeoutSeconds", settings.connectTimeoutSeconds)
                        put("readTimeoutSeconds", settings.readTimeoutSeconds)
                        put("totalTimeoutSeconds", settings.totalTimeoutSeconds)
                        put("maxProseCharacters", settings.maxProseCharacters)
                        if (settings.lastConnectionTestAt != null) {
                            put("lastConnectionTestAt", settings.lastConnectionTestAt)
                        } else {
                            put("lastConnectionTestAt", JsonNull)
                        }
                    })
                }
            })
        }.toString()
        require(payload.toByteArray(Charsets.UTF_8).size <= MAX_SETTINGS_BYTES)
        atomicWrite(file, payload.toByteArray(Charsets.UTF_8))
    }

    private fun kotlinx.serialization.json.JsonObject.requiredString(key: String): String =
        requireNotNull(this[key]).jsonPrimitive.content

    private fun kotlinx.serialization.json.JsonObject.requiredInt(key: String): Int =
        requireNotNull(this[key]).jsonPrimitive.int

    private companion object {
        const val LEGACY_SCHEMA_VERSION = "1.0"
        const val SCHEMA_VERSION = "2.0"
        const val MAX_SETTINGS_BYTES = 128 * 1024L
        const val MAX_PROFILES = 20
        val LEGACY_KEYS = setOf(
            "schemaVersion",
            "providerId",
            "baseUrl",
            "normalizedChatCompletionsUrl",
            "model",
            "credentialAlias",
            "connectTimeoutSeconds",
            "readTimeoutSeconds",
            "totalTimeoutSeconds",
            "maxProseCharacters",
            "lastConnectionTestAt",
        )
        val COLLECTION_KEYS = setOf("schemaVersion", "activeProfileId", "profiles")
        val PROFILE_KEYS = LEGACY_KEYS - "schemaVersion" + setOf("displayName", "kind")
        val lock = Any()
    }
}

internal interface S1ProviderSecretStore {
    fun save(secret: CharArray): String

    fun <T> withSecret(credentialAlias: String, block: (CharArray) -> T): T

    fun delete(credentialAlias: String)
}

internal class S1InMemoryProviderSecretStore : S1ProviderSecretStore {
    private val values = mutableMapOf<String, CharArray>()

    override fun save(secret: CharArray): String = synchronized(values) {
        val alias = "novel_api_key_${UUID.randomUUID().toString().replace("-", "")}"
        values[alias] = secret.copyOf()
        alias
    }

    override fun <T> withSecret(credentialAlias: String, block: (CharArray) -> T): T {
        val copy = synchronized(values) { values[credentialAlias]?.copyOf() }
            ?: error("PROVIDER_CREDENTIAL_UNAVAILABLE")
        return try {
            block(copy)
        } finally {
            copy.fill('\u0000')
        }
    }

    override fun delete(credentialAlias: String) {
        synchronized(values) { values.remove(credentialAlias)?.fill('\u0000') }
    }
}

internal data class S1ProviderDiagnostic(
    val stage: String,
    val errorCode: String?,
    val requestIdHash: String,
    val durationMillis: Long,
    val responseBytes: Long,
    val httpStatus: Int?,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val finishReason: String? = null,
    val validationRule: String? = null,
    val qualityCardName: String? = null,
    val qualityCardVersion: Int? = null,
    val qualityCardSha256: String? = null,
)

internal class S1ProviderDiagnosticSink(
    private val file: File,
    private val now: () -> String = { Instant.now().toString() },
) {
    fun record(diagnostic: S1ProviderDiagnostic) = synchronized(lock) {
        runCatching {
            val line = buildJsonObject {
                put("at", now())
                put("stage", diagnostic.stage)
                diagnostic.errorCode?.let { put("errorCode", it) }
                put("requestIdHash", diagnostic.requestIdHash)
                put("durationMillis", diagnostic.durationMillis)
                put("responseBytes", diagnostic.responseBytes)
                diagnostic.httpStatus?.let { put("httpStatus", it) }
                diagnostic.inputTokens?.let { put("inputTokens", it) }
                diagnostic.outputTokens?.let { put("outputTokens", it) }
                diagnostic.finishReason?.let { put("finishReason", it.take(80)) }
                diagnostic.validationRule?.let { put("validationRule", it.take(80)) }
                diagnostic.qualityCardName?.let { put("qualityCardName", it.take(80)) }
                diagnostic.qualityCardVersion?.let { put("qualityCardVersion", it) }
                diagnostic.qualityCardSha256?.takeIf { it.matches(Regex("[a-f0-9]{64}")) }
                    ?.let { put("qualityCardSha256", it) }
            }.toString()
            file.parentFile?.let { check(it.exists() || it.mkdirs()) }
            file.appendText(line + "\n", Charsets.UTF_8)
        }
    }

    private companion object {
        val lock = Any()
    }
}

internal fun atomicWrite(target: File, bytes: ByteArray) {
    target.parentFile?.let { check(it.exists() || it.mkdirs()) }
    val temporary = File(target.parentFile, ".${target.name}.tmp")
    java.io.FileOutputStream(temporary).use { fileOutput ->
        fileOutput.write(bytes)
        fileOutput.flush()
        fileOutput.fd.sync()
    }
    if (target.exists()) {
        val backup = File(target.parentFile, "${target.name}.bak")
        target.copyTo(backup, overwrite = true)
    }
    try {
        java.nio.file.Files.move(
            temporary.toPath(),
            target.toPath(),
            java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
        )
    } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
        java.nio.file.Files.move(
            temporary.toPath(),
            target.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
        )
    }
}
