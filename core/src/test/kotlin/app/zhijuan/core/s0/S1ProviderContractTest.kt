package app.zhijuan.core.s0

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class S1ProviderContractTest {
    @Test
    fun `endpoint normalization follows the one chat completions route`() {
        val cases = mapOf(
            "https://api.example.com/v1" to "https://api.example.com/v1/chat/completions",
            "https://api.example.com/v1/" to "https://api.example.com/v1/chat/completions",
            "https://api.example.com/v1/chat/completions" to "https://api.example.com/v1/chat/completions",
            "https://api.example.com" to "https://api.example.com/chat/completions",
        )

        cases.forEach { (input, expected) ->
            val normalized = S1ProviderSettingsValidator.normalizeEndpoint(input).getOrThrow()
            assertEquals(expected, normalized.chatCompletionsUrl)
        }
    }

    @Test
    fun `release endpoint policy rejects cleartext user info query and fragment`() {
        listOf(
            "http://api.example.com/v1",
            "https://user@api.example.com/v1",
            "https://api.example.com/v1?route=chat",
            "https://api.example.com/v1#chat",
        ).forEach { input ->
            assertTrue(S1ProviderSettingsValidator.normalizeEndpoint(input).isFailure, input)
        }
        assertTrue(
            S1ProviderSettingsValidator.normalizeEndpoint(
                "http://127.0.0.1:8080/v1",
                allowHttpForLocalTests = true,
            ).isSuccess,
        )
    }

    @Test
    fun `setup input never renders the api key`() {
        val input = S1ProviderSetupInput(
            baseUrl = "https://api.example.com/v1",
            apiKey = "SECRET_CANARY_123".toCharArray(),
            model = "model-a",
        )

        assertFalse(input.toString().contains("SECRET_CANARY_123"))
        assertTrue(input.toString().contains("<redacted>"))
    }

    @Test
    fun `recommended quick setup is complete with only an api key`() {
        val input = S1ProviderDefaults.setupInput("SECRET_CANARY_123".toCharArray())
        val endpoint = S1ProviderSettingsValidator.validate(input).getOrThrow()

        assertEquals("DeepSeek V4 Pro", S1ProviderDefaults.DISPLAY_NAME)
        assertEquals("https://api.deepseek.com/chat/completions", endpoint.chatCompletionsUrl)
        assertEquals("deepseek-v4-pro", input.model)
        assertEquals(15, input.connectTimeoutSeconds)
        assertEquals(180, input.readTimeoutSeconds)
        assertEquals(300, input.totalTimeoutSeconds)
        assertEquals(12_000, input.maxProseCharacters)
    }

    @Test
    fun `provider presets cover four mainstream platforms plus one compatible relay`() {
        assertEquals(
            listOf(
                S1ProviderKind.DEEPSEEK,
                S1ProviderKind.QWEN,
                S1ProviderKind.GLM,
                S1ProviderKind.KIMI,
                S1ProviderKind.OPENAI_COMPATIBLE,
            ),
            S1ProviderPresets.ALL.map { it.kind },
        )
        S1ProviderPresets.ALL.filter { it.kind != S1ProviderKind.OPENAI_COMPATIBLE }.forEach { preset ->
            assertTrue(preset.baseUrl.startsWith("https://"))
            assertTrue(preset.models.isNotEmpty())
            assertTrue(S1ProviderSettingsValidator.normalizeEndpoint(preset.baseUrl).isSuccess)
        }
        assertTrue(S1ProviderPresets.find(S1ProviderKind.OPENAI_COMPATIBLE).baseUrl.isEmpty())
    }

    @Test
    fun `legacy provider profile id remains valid for in place edits`() {
        val input = S1ProviderSetupInput(
            baseUrl = "https://api.example.com/v1",
            apiKey = CharArray(0),
            model = "model-a",
            profileId = "provider_main",
            displayName = "旧配置",
        )

        assertTrue(S1ProviderSettingsValidator.validate(input, allowStoredCredential = true).isSuccess)
    }

    @Test
    fun `error catalog exposes stable safe actions`() {
        val authentication = S1ProviderErrors.of(S1ProviderErrorCode.AUTH_REJECTED)
        val unknown = S1ProviderErrors.of(S1ProviderErrorCode.REQUEST_OUTCOME_UNKNOWN)

        assertEquals("EDIT_KEY", authentication.userAction)
        assertFalse(authentication.retryable)
        assertEquals("CONFIRM_RESEND", unknown.userAction)
        assertFalse(unknown.retryable)
    }
}
