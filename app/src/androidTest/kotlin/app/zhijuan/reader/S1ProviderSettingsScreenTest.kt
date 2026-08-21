package app.zhijuan.reader

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import app.zhijuan.core.s0.S0ChapterTask
import app.zhijuan.core.s0.S0Settlement
import app.zhijuan.core.s0.S0TextGenerationProvider
import app.zhijuan.core.s0.S1ConnectionTestResult
import app.zhijuan.core.s0.S1ProviderDefaults
import app.zhijuan.core.s0.S1ProviderSettingsValidator
import app.zhijuan.core.s0.S1ProviderSetupInput
import app.zhijuan.core.s0.S1ProviderSummary
import app.zhijuan.core.s0.S1ProviderKind
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class S1ProviderSettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun quickSetupNeedsOnlyKeyAndClearsItAfterSuccess() {
        val provider = SavingProvider()
        var callbackReceived = false
        compose.setContent {
            ZhijuanS0Theme {
                S1ProviderSettingsScreen(
                    modifier = androidx.compose.ui.Modifier,
                    provider = provider,
                    onSaved = { callbackReceived = true },
                )
            }
        }

        compose.onNodeWithText("DeepSeek V4 Pro").assertExists()
        compose.onNodeWithTag("provider-endpoint").assertDoesNotExist()
        compose.onNodeWithTag("provider-model").assertExists()
        compose.onNodeWithTag("provider-submit").assertIsNotEnabled()
        compose.onNodeWithTag("provider-key").performTextInput("TEST_KEY_12345678")
        compose.onNodeWithTag("provider-submit").assertIsEnabled().performClick()

        compose.waitUntil(5_000) { callbackReceived }
        compose.onNodeWithText("当前使用").assertExists()
        compose.onNodeWithTag("provider-key").assertDoesNotExist()
        assertTrue(provider.receivedKeyWasCleared)
        assertEquals(S1ProviderDefaults.BASE_URL, provider.receivedBaseUrl)
        assertEquals(S1ProviderDefaults.MODEL, provider.receivedModel)
    }

    @Test
    fun compatibleServiceFieldsStayBehindOneDisclosure() {
        val provider = SavingProvider()
        compose.setContent {
            ZhijuanS0Theme {
                S1ProviderSettingsScreen(
                    modifier = androidx.compose.ui.Modifier,
                    provider = provider,
                    onSaved = {},
                )
            }
        }

        compose.onNodeWithTag("provider-endpoint").assertDoesNotExist()
        compose.onNodeWithTag("provider-custom-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("provider-endpoint").assertExists()
        compose.onNodeWithTag("provider-model").assertExists()
        compose.onNodeWithTag("provider-tuning-toggle").assertExists()
    }

    @Test
    fun savedCustomConfigurationRestoresWithoutRevealingKey() {
        val saved = S1ProviderSummary(
            providerId = "provider_main",
            baseUrl = "https://api.example.com/v1",
            normalizedChatCompletionsUrl = "https://api.example.com/v1/chat/completions",
            model = "model-custom",
            connectTimeoutSeconds = 20,
            readTimeoutSeconds = 200,
            totalTimeoutSeconds = 360,
            maxProseCharacters = 14_000,
            lastConnectionTestAt = "2026-08-20T00:00:00Z",
        )
        val provider = SavingProvider(saved)
        compose.setContent {
            ZhijuanS0Theme {
                S1ProviderSettingsScreen(
                    modifier = androidx.compose.ui.Modifier,
                    provider = provider,
                    onSaved = {},
                )
            }
        }

        compose.onNodeWithTag("provider-edit-provider_main").performClick()
        assertEquals(
            "",
            compose.onNodeWithTag("provider-key")
                .fetchSemanticsNode().config[SemanticsProperties.EditableText].text,
        )
        assertEquals(
            "https://api.example.com/v1",
            compose.onNodeWithTag("provider-endpoint")
                .fetchSemanticsNode().config[SemanticsProperties.EditableText].text,
        )
        assertEquals(
            "model-custom",
            compose.onNodeWithTag("provider-model")
                .fetchSemanticsNode().config[SemanticsProperties.EditableText].text,
        )
    }

    private class SavingProvider(
        private val initialSummary: S1ProviderSummary? = null,
    ) : S0TextGenerationProvider {
        private val profiles = mutableListOf<S1ProviderSummary>().apply { initialSummary?.let(::add) }
        private var activeProfileId: String? = initialSummary?.providerId
        var receivedKeyWasCleared = false
        var receivedBaseUrl: String? = null
        var receivedModel: String? = null

        override suspend fun streamProse(task: S0ChapterTask, onChunk: (String) -> Unit): String = error("unused")

        override suspend fun completeSettlement(task: S0ChapterTask, prose: String): S0Settlement = error("unused")

        override fun connectionSummary(): S1ProviderSummary? = profiles.firstOrNull { it.providerId == activeProfileId }

        override fun connectionProfiles(): List<S1ProviderSummary> = profiles.toList()

        override fun selectConnectionProfile(profileId: String): Result<S1ProviderSummary> = runCatching {
            profiles.first { it.providerId == profileId }.also { activeProfileId = profileId }
        }

        override fun deleteConnectionProfile(profileId: String): Result<Unit> = runCatching {
            profiles.removeAll { it.providerId == profileId }
            if (activeProfileId == profileId) activeProfileId = profiles.firstOrNull()?.providerId
        }

        override suspend fun testAndSaveConnection(input: S1ProviderSetupInput): S1ConnectionTestResult {
            receivedBaseUrl = input.baseUrl
            receivedModel = input.model
            val normalized = S1ProviderSettingsValidator.normalizeEndpoint(input.baseUrl).getOrThrow()
            val summary = S1ProviderSummary(
                providerId = input.profileId ?: "provider_saved",
                baseUrl = input.baseUrl,
                normalizedChatCompletionsUrl = normalized.chatCompletionsUrl,
                model = input.model,
                connectTimeoutSeconds = input.connectTimeoutSeconds,
                readTimeoutSeconds = input.readTimeoutSeconds,
                totalTimeoutSeconds = input.totalTimeoutSeconds,
                maxProseCharacters = input.maxProseCharacters,
                lastConnectionTestAt = "2026-08-17T00:00:00Z",
                displayName = input.displayName,
                kind = input.kind,
            )
            profiles.removeAll { it.providerId == summary.providerId }
            profiles += summary
            activeProfileId = summary.providerId
            input.apiKey.fill('\u0000')
            receivedKeyWasCleared = input.apiKey.all { it == '\u0000' }
            return S1ConnectionTestResult.Saved(summary, requestIdHash = "safehash", durationMillis = 1)
        }
    }
}
