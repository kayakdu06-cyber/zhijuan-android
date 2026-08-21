package app.zhijuan.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.zhijuan.core.s0.S0TextGenerationProvider
import app.zhijuan.core.s0.S1ConnectionTestResult
import app.zhijuan.core.s0.S1ProviderDefaults
import app.zhijuan.core.s0.S1ProviderFailure
import app.zhijuan.core.s0.S1ProviderKind
import app.zhijuan.core.s0.S1ProviderPreset
import app.zhijuan.core.s0.S1ProviderPresets
import app.zhijuan.core.s0.S1ProviderSettingsValidator
import app.zhijuan.core.s0.S1ProviderSetupInput
import app.zhijuan.core.s0.S1ProviderSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun S1ProviderSettingsScreen(
    modifier: Modifier,
    provider: S0TextGenerationProvider,
    onSaved: () -> Unit,
    profilesLocked: Boolean = false,
    footer: @Composable () -> Unit = {},
) {
    var profiles by remember(provider) { mutableStateOf(provider.connectionProfiles()) }
    var active by remember(provider) { mutableStateOf(provider.connectionSummary()) }
    var editing by remember { mutableStateOf<S1ProviderSummary?>(null) }
    var editorVisible by rememberSaveable { mutableStateOf(profiles.isEmpty()) }
    var operationMessage by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        profiles = provider.connectionProfiles()
        active = provider.connectionSummary()
        onSaved()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        EditorialBrandBar(title = "设置")
        Text(
            "我的 API",
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineLarge.copy(fontFamily = EditorialSerif),
        )
        Text(
            "保存多组兼容配置，写作前一键切换。API Key 仍只在本机加密保存。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        profiles.forEach { profile ->
            val selected = active?.providerId == profile.providerId
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !profilesLocked && !selected) {
                        provider.selectConnectionProfile(profile.providerId).onSuccess {
                            refresh()
                            operationMessage = "已切换为 ${it.displayName}"
                        }
                    }
                    .testTag("provider-profile-${profile.providerId}"),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                ),
                border = BorderStroke(
                    if (selected) 2.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                ),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            profile.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) Text("当前使用", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Text("${profile.kind.label()} · ${profile.model}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(profile.normalizedChatCompletionsUrl, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = { editing = profile; editorVisible = true },
                            enabled = !profilesLocked,
                            modifier = Modifier.testTag("provider-edit-${profile.providerId}"),
                        ) { Text("编辑") }
                        TextButton(
                            onClick = {
                                provider.deleteConnectionProfile(profile.providerId).onSuccess {
                                    refresh()
                                    operationMessage = "配置已删除"
                                }.onFailure { operationMessage = "当前任务正在使用该配置，暂时不能删除" }
                            },
                            enabled = !profilesLocked,
                            modifier = Modifier.testTag("provider-delete-${profile.providerId}"),
                        ) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }

        if (!editorVisible) {
            EditorialPrimaryButton(
                label = "添加 API",
                onClick = { editing = null; editorVisible = true },
                enabled = !profilesLocked,
                modifier = Modifier.fillMaxWidth().testTag("provider-add"),
            )
        }

        operationMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (profilesLocked) {
            Text("章节生成期间配置已锁定；完成或停止后可以切换。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (editorVisible) {
            S1ProviderEditor(
                provider = provider,
                editing = editing,
                onSaved = {
                    editing = null
                    editorVisible = false
                    refresh()
                    operationMessage = "${it.displayName} 已验证并保存"
                },
                onCancel = {
                    if (profiles.isNotEmpty()) {
                        editing = null
                        editorVisible = false
                    }
                },
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
        footer()
        Spacer(Modifier.size(20.dp))
    }
}

@Composable
private fun S1ProviderEditor(
    provider: S0TextGenerationProvider,
    editing: S1ProviderSummary?,
    onSaved: (S1ProviderSummary) -> Unit,
    onCancel: () -> Unit,
) {
    val initialKind = editing?.kind ?: S1ProviderKind.DEEPSEEK
    var kind by rememberSaveable(editing?.providerId) { mutableStateOf(initialKind) }
    val initialPreset = S1ProviderPresets.find(initialKind)
    var displayName by rememberSaveable(editing?.providerId) {
        mutableStateOf(
            editing?.displayName
                ?: if (initialKind == S1ProviderKind.DEEPSEEK) S1ProviderDefaults.DISPLAY_NAME else initialPreset.displayName,
        )
    }
    var endpoint by rememberSaveable(editing?.providerId) { mutableStateOf(editing?.baseUrl ?: initialPreset.baseUrl) }
    var model by rememberSaveable(editing?.providerId) {
        mutableStateOf(editing?.model ?: initialPreset.models.firstOrNull().orEmpty())
    }
    var apiKey by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var showTuning by rememberSaveable(editing?.providerId) { mutableStateOf(false) }
    var connectTimeout by rememberSaveable(editing?.providerId) {
        mutableStateOf((editing?.connectTimeoutSeconds ?: S1ProviderDefaults.CONNECT_TIMEOUT_SECONDS).toString())
    }
    var readTimeout by rememberSaveable(editing?.providerId) {
        mutableStateOf((editing?.readTimeoutSeconds ?: S1ProviderDefaults.READ_TIMEOUT_SECONDS).toString())
    }
    var totalTimeout by rememberSaveable(editing?.providerId) {
        mutableStateOf((editing?.totalTimeoutSeconds ?: S1ProviderDefaults.TOTAL_TIMEOUT_SECONDS).toString())
    }
    var maxCharacters by rememberSaveable(editing?.providerId) {
        mutableStateOf((editing?.maxProseCharacters ?: S1ProviderDefaults.MAX_PROSE_CHARACTERS).toString())
    }
    var testing by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<S1ProviderFailure?>(null) }
    val scope = rememberCoroutineScope()
    val preview = remember(endpoint) { S1ProviderSettingsValidator.normalizeEndpoint(endpoint).getOrNull() }
    val keyValid = (apiKey.isEmpty() && editing != null) ||
        (apiKey.length in 8..16_384 && apiKey.none(Char::isWhitespace))
    val connectTimeoutValue = connectTimeout.toIntOrNull()
    val readTimeoutValue = readTimeout.toIntOrNull()
    val totalTimeoutValue = totalTimeout.toIntOrNull()
    val maxCharactersValue = maxCharacters.toIntOrNull()
    val numericValid = connectTimeoutValue != null && connectTimeoutValue in 5..60 &&
        readTimeoutValue != null && readTimeoutValue in 30..600 &&
        totalTimeoutValue != null && totalTimeoutValue in 60..1_800 &&
        maxCharactersValue != null && maxCharactersValue in 1_000..30_000
    val canSubmit = !testing && keyValid && preview != null && model.isNotBlank() && displayName.isNotBlank() && numericValid

    fun selectPreset(preset: S1ProviderPreset) {
        kind = preset.kind
        endpoint = preset.baseUrl
        model = preset.models.firstOrNull().orEmpty()
        displayName = if (preset.kind == S1ProviderKind.DEEPSEEK) S1ProviderDefaults.DISPLAY_NAME else preset.displayName
        failure = null
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("provider-editor"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (editing == null) "添加 API" else "编辑 API", style = MaterialTheme.typography.titleLarge.copy(fontFamily = EditorialSerif))
            Text("选择平台", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                S1ProviderPresets.ALL.forEach { preset ->
                    OutlinedButton(
                        onClick = { selectPreset(preset) },
                        border = BorderStroke(
                            if (kind == preset.kind) 2.dp else 1.dp,
                            if (kind == preset.kind) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        ),
                        modifier = Modifier.height(48.dp).testTag(
                            if (preset.kind == S1ProviderKind.OPENAI_COMPATIBLE) "provider-custom-toggle"
                            else "provider-kind-${preset.kind.name}",
                        ),
                    ) { Text(preset.displayName) }
                }
            }
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it.take(80); failure = null },
                label = { Text("配置名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("provider-name"),
            )
            if (kind == S1ProviderKind.OPENAI_COMPATIBLE) {
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it; failure = null },
                    label = { Text("接口地址") },
                    supportingText = {
                        Text(preview?.let { "将请求：${it.host}${it.path}" } ?: "填写 Base URL 或完整 /chat/completions 地址")
                    },
                    isError = endpoint.isNotBlank() && preview == null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("provider-endpoint"),
                )
            } else {
                Text("接口已自动配置：${preview?.host ?: endpoint}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val presetModels = S1ProviderPresets.find(kind).models
            if (presetModels.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presetModels.forEach { option ->
                        OutlinedButton(
                            onClick = { model = option; failure = null },
                            border = BorderStroke(
                                if (model == option) 2.dp else 1.dp,
                                if (model == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            ),
                            modifier = Modifier.height(48.dp),
                        ) { Text(option) }
                    }
                }
            }
            OutlinedTextField(
                value = model,
                onValueChange = { model = it.take(200); failure = null },
                label = { Text("模型 ID") },
                supportingText = { Text("可直接使用预设，也可以按服务商说明手动填写。") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("provider-model"),
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; failure = null },
                label = { Text("API Key") },
                supportingText = {
                    Text(
                        if (editing == null) "Key 只在本机用 AndroidKeyStore 加密保存。"
                        else "留空可继续使用已保存的 Key；输入新 Key 才会替换。",
                    )
                },
                isError = apiKey.isNotEmpty() && !keyValid,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = { TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "隐藏" else "显示") } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("provider-key"),
            )
            TextButton(onClick = { showTuning = !showTuning }, modifier = Modifier.testTag("provider-tuning-toggle")) {
                Text(if (showTuning) "收起高级设置" else "高级设置")
            }
            if (showTuning) {
                NumberField("连接超时（秒）", connectTimeout, { connectTimeout = it }, "5–60")
                NumberField("读取超时（秒）", readTimeout, { readTimeout = it }, "30–600")
                NumberField("单章总超时（秒）", totalTimeout, { totalTimeout = it }, "60–1800")
                NumberField("正文字符上限", maxCharacters, { maxCharacters = it }, "1000–30000")
            }
            failure?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("连接未保存", fontWeight = FontWeight.Bold)
                        Text(error.safeMessage)
                        Text("下一步：${error.actionLabel()}")
                    }
                }
            }
            Button(
                enabled = canSubmit,
                onClick = {
                    testing = true
                    failure = null
                    val input = S1ProviderSetupInput(
                        baseUrl = endpoint,
                        apiKey = apiKey.toCharArray(),
                        model = model,
                        connectTimeoutSeconds = requireNotNull(connectTimeout.toIntOrNull()),
                        readTimeoutSeconds = requireNotNull(readTimeout.toIntOrNull()),
                        totalTimeoutSeconds = requireNotNull(totalTimeout.toIntOrNull()),
                        maxProseCharacters = requireNotNull(maxCharacters.toIntOrNull()),
                        profileId = editing?.providerId,
                        displayName = displayName,
                        kind = kind,
                    )
                    scope.launch {
                        val result = try {
                            withContext(Dispatchers.IO) { provider.testAndSaveConnection(input) }
                        } finally {
                            apiKey = ""
                            showKey = false
                            testing = false
                        }
                        when (result) {
                            is S1ConnectionTestResult.Saved -> onSaved(result.summary)
                            is S1ConnectionTestResult.Failed -> failure = result.failure
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("provider-submit"),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (testing) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(if (testing) "正在验证…" else "测试并保存")
                }
            }
            if (editing != null) {
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("取消编辑") }
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onValueChange: (String) -> Unit, range: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { candidate -> if (candidate.all(Char::isDigit)) onValueChange(candidate) },
        label = { Text(label) },
        supportingText = { Text("允许范围：$range") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun S1ProviderKind.label(): String = when (this) {
    S1ProviderKind.DEEPSEEK -> "DeepSeek"
    S1ProviderKind.QWEN -> "通义千问"
    S1ProviderKind.GLM -> "智谱 GLM"
    S1ProviderKind.KIMI -> "Kimi"
    S1ProviderKind.OPENAI_COMPATIBLE -> "兼容服务"
}

private fun S1ProviderFailure.actionLabel(): String = when (userAction) {
    "EDIT_PROVIDER" -> "检查接口地址"
    "EDIT_KEY" -> "重新输入 API Key"
    "EDIT_MODEL" -> "检查模型 ID"
    "RETRY_LATER" -> "稍后重新验证"
    "CONFIRM_RESEND" -> "确认后手动重试"
    else -> "检查网络后重试"
}
