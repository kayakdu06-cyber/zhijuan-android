package app.zhijuan.data.s0

import app.zhijuan.core.s0.S0WritingQualityCard
import app.zhijuan.core.s0.S0WritingSkillFormat
import app.zhijuan.core.s0.S0WritingSkillImport
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class S5WritingSkillException(message: String) : IllegalArgumentException(message)

/** Deterministic, local-only parser for one untrusted project writing-rule file. */
class S5WritingSkillParser {
    fun parse(fileName: String, input: InputStream): S0WritingSkillImport {
        val safeName = fileName.substringAfterLast('/').substringAfterLast('\\').trim()
        writingSkillRequire(safeName.length in 3..120, "WRITING_SKILL_FILE_NAME_INVALID")
        val format = when (safeName.substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
            "md" -> S0WritingSkillFormat.MARKDOWN
            "json" -> S0WritingSkillFormat.JSON
            else -> throw S5WritingSkillException("WRITING_SKILL_FORMAT_UNSUPPORTED")
        }
        val bytes = readBounded(input, MAX_SOURCE_BYTES)
        writingSkillRequire(bytes.isNotEmpty(), "WRITING_SKILL_EMPTY")
        val sourceText = decodeUtf8Strict(bytes)
        writingSkillRequire('\u0000' !in sourceText, "WRITING_SKILL_BINARY_REJECTED")
        val parsed = when (format) {
            S0WritingSkillFormat.MARKDOWN -> parseMarkdown(safeName, sourceText)
            S0WritingSkillFormat.JSON -> parseJson(sourceText)
        }
        val card = candidateCard(parsed.name, parsed.rules, parsed.avoid, parsed.preferredTerms)
        return S0WritingSkillImport(
            sourceFileName = safeName,
            format = format,
            sourceText = sourceText,
            sourceSha256 = sha256(bytes),
            qualityCard = card,
        )
    }

    fun editQualityCard(
        imported: S0WritingSkillImport,
        name: String,
        rules: List<String>,
        avoid: List<String>,
        preferredTerms: List<String>,
    ): S0WritingSkillImport = validateImport(
        imported.copy(qualityCard = validatedCard(name, rules, avoid, preferredTerms)),
    )

    fun validateImport(imported: S0WritingSkillImport): S0WritingSkillImport {
        val safeName = imported.sourceFileName.substringAfterLast('/').substringAfterLast('\\').trim()
        writingSkillRequire(safeName == imported.sourceFileName && safeName.length in 3..120, "WRITING_SKILL_FILE_NAME_INVALID")
        val expectedFormat = when (safeName.substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
            "md" -> S0WritingSkillFormat.MARKDOWN
            "json" -> S0WritingSkillFormat.JSON
            else -> throw S5WritingSkillException("WRITING_SKILL_FORMAT_UNSUPPORTED")
        }
        writingSkillRequire(expectedFormat == imported.format, "WRITING_SKILL_FORMAT_MISMATCH")
        val bytes = imported.sourceText.toByteArray(StandardCharsets.UTF_8)
        writingSkillRequire(bytes.isNotEmpty() && bytes.size <= MAX_SOURCE_BYTES, "WRITING_SKILL_SOURCE_TOO_LARGE")
        writingSkillRequire(sha256(bytes) == imported.sourceSha256, "WRITING_SKILL_SOURCE_HASH_MISMATCH")
        val card = validatedCard(
            imported.qualityCard.name,
            imported.qualityCard.rules,
            imported.qualityCard.avoid,
            imported.qualityCard.preferredTerms,
        )
        writingSkillRequire(card == imported.qualityCard, "WRITING_SKILL_CARD_NOT_CANONICAL")
        return imported
    }

    private fun parseMarkdown(fileName: String, source: String): ParsedCard {
        var section = MarkdownSection.NONE
        var inCodeFence = false
        val rules = mutableListOf<String>()
        val avoid = mutableListOf<String>()
        val terms = mutableListOf<String>()
        val unscoped = mutableListOf<String>()
        val proseCandidates = mutableListOf<String>()
        source.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.startsWith("```")) {
                inCodeFence = !inCodeFence
                return@forEach
            }
            if (inCodeFence) return@forEach
            if (line.startsWith('#')) {
                val heading = line.trimStart('#').trim().lowercase()
                section = markdownSection(heading)
                return@forEach
            }
            val candidate = BULLET.matchEntire(line)?.groupValues?.get(1)?.trim()
            if (candidate == null) {
                if (line.length in 6..MAX_ITEM_CHARACTERS && !line.startsWith('#')) proseCandidates += line
                return@forEach
            }
            val safeRule = candidateRule(candidate) ?: return@forEach
            when (section) {
                MarkdownSection.RULES -> rules += safeRule
                MarkdownSection.AVOID -> avoid += safeRule
                MarkdownSection.PREFERRED_TERMS -> terms += safeRule
                MarkdownSection.NONE -> unscoped += safeRule
            }
        }
        if (rules.isEmpty() && avoid.isEmpty() && terms.isEmpty()) rules += unscoped
        if (rules.isEmpty() && avoid.isEmpty() && terms.isEmpty()) {
            rules += proseCandidates.mapNotNull(::candidateRule)
        }
        return ParsedCard(
            name = fileName.substringBeforeLast('.').trim().ifBlank { "创作质量卡" }.take(MAX_NAME_CHARS),
            rules = rules,
            avoid = avoid,
            preferredTerms = terms,
        )
    }

    private fun parseJson(source: String): ParsedCard {
        val root = runCatching { Json.parseToJsonElement(source).jsonObject }
            .getOrElse { throw S5WritingSkillException("WRITING_SKILL_JSON_INVALID") }
        if (
            root.keys == JSON_KEYS &&
            root["schemaVersion"]?.jsonPrimitive?.content == "1.0" &&
            root["scope"]?.jsonPrimitive?.content == "chapter_prose_quality_card"
        ) {
            return ParsedCard(
                name = root.string("name").trim().take(MAX_NAME_CHARS),
                rules = root.stringList("rules"),
                avoid = root.stringList("avoid"),
                preferredTerms = root.stringList("preferredTerms"),
            )
        }
        val rules = mutableListOf<String>()
        val avoid = mutableListOf<String>()
        val terms = mutableListOf<String>()
        collectJsonCandidates(root, rules, avoid, terms)
        val name = listOf("name", "title", "id")
            .firstNotNullOfOrNull { key -> (root[key] as? JsonPrimitive)?.takeIf { it.isString }?.content }
            ?.trim()?.takeIf(String::isNotBlank)?.take(MAX_NAME_CHARS)
            ?: "导入的创作质量卡"
        return ParsedCard(
            name = name,
            rules = rules,
            avoid = avoid,
            preferredTerms = terms,
        )
    }

    private fun collectJsonCandidates(
        element: JsonElement,
        rules: MutableList<String>,
        avoid: MutableList<String>,
        terms: MutableList<String>,
    ) {
        when (element) {
            is JsonObject -> element.forEach { (rawKey, value) ->
                val key = rawKey.lowercase().replace("_", "").replace("-", "")
                val target = when {
                    key in JSON_AVOID_KEYS -> avoid
                    key in JSON_TERM_KEYS -> terms
                    key in JSON_RULE_KEYS -> rules
                    else -> null
                }
                target?.addAll(jsonStrings(value).mapNotNull(::candidateRule))
                if (value is JsonObject || value is JsonArray) collectJsonCandidates(value, rules, avoid, terms)
            }
            is JsonArray -> element.forEach { collectJsonCandidates(it, rules, avoid, terms) }
            else -> Unit
        }
    }

    private fun jsonStrings(element: JsonElement): List<String> = when (element) {
        is JsonPrimitive -> if (element.isString) listOf(element.content) else emptyList()
        is JsonArray -> element.flatMap(::jsonStrings)
        is JsonObject -> element.values.flatMap(::jsonStrings)
    }

    private fun markdownSection(heading: String): MarkdownSection = when {
        listOf("避免", "禁止", "禁用", "不要", "avoid", "must not", "forbidden").any(heading::contains) ->
            MarkdownSection.AVOID
        listOf("词汇", "用词", "术语", "preferred terms", "vocabulary", "diction").any(heading::contains) ->
            MarkdownSection.PREFERRED_TERMS
        listOf("风格", "写作", "规则", "指令", "原则", "指南", "必须", "style", "rule", "instruction", "guideline", "must").any(heading::contains) ->
            MarkdownSection.RULES
        else -> MarkdownSection.NONE
    }

    private fun candidateCard(
        name: String,
        rules: List<String>,
        avoid: List<String>,
        preferredTerms: List<String>,
    ): S0WritingQualityCard {
        var remainingCharacters = MAX_CARD_CHARACTERS
        var remainingItems = MAX_CARD_ITEMS
        fun bounded(values: List<String>): List<String> = buildList {
            values.mapNotNull(::candidateRule).distinct().forEach { value ->
                if (remainingItems > 0 && value.length <= remainingCharacters) {
                    add(value)
                    remainingItems--
                    remainingCharacters -= value.length
                }
            }
        }
        val normalizedRules = bounded(rules)
        val normalizedAvoid = bounded(avoid)
        val normalizedTerms = bounded(preferredTerms)
        val card = S0WritingQualityCard(
            name = name.trim().ifBlank { "导入的创作质量卡" }.take(MAX_NAME_CHARS),
            rules = normalizedRules,
            avoid = normalizedAvoid,
            preferredTerms = normalizedTerms,
            sha256 = "",
        )
        return card.copy(sha256 = qualityCardSha256(card))
    }

    private fun validatedCard(
        name: String,
        rules: List<String>,
        avoid: List<String>,
        preferredTerms: List<String>,
    ): S0WritingQualityCard {
        val normalizedName = name.trim()
        writingSkillRequire(normalizedName.length in 1..MAX_NAME_CHARS, "WRITING_SKILL_NAME_INVALID")
        val normalizedRules = rules.map(::validateRule).distinct()
        val normalizedAvoid = avoid.map(::validateRule).distinct()
        val normalizedTerms = preferredTerms.map(::validateRule).distinct()
        val all = normalizedRules + normalizedAvoid + normalizedTerms
        writingSkillRequire(all.isNotEmpty(), "WRITING_SKILL_NO_SUPPORTED_RULES")
        writingSkillRequire(all.size <= MAX_CARD_ITEMS, "WRITING_SKILL_TOO_MANY_RULES")
        writingSkillRequire(all.sumOf(String::length) <= MAX_CARD_CHARACTERS, "WRITING_SKILL_CARD_TOO_LONG")
        val withoutHash = S0WritingQualityCard(
            name = normalizedName,
            rules = normalizedRules,
            avoid = normalizedAvoid,
            preferredTerms = normalizedTerms,
            sha256 = "",
        )
        return withoutHash.copy(sha256 = qualityCardSha256(withoutHash))
    }

    private fun validateRule(raw: String): String {
        val rule = raw
            .removePrefix("[ ]")
            .removePrefix("[x]")
            .trim()
            .trim('*', '_', '`')
            .trim()
        writingSkillRequire(rule.length in 1..MAX_ITEM_CHARACTERS, "WRITING_SKILL_RULE_LENGTH")
        writingSkillRequire(!UNSAFE_RULE.containsMatchIn(rule), "WRITING_SKILL_RULE_UNSAFE")
        writingSkillRequire(!URL_OR_REFERENCE.containsMatchIn(rule), "WRITING_SKILL_REFERENCE_UNSUPPORTED")
        writingSkillRequire('<' !in rule && '>' !in rule, "WRITING_SKILL_HTML_UNSUPPORTED")
        return rule
    }

    private fun candidateRule(raw: String): String? = runCatching { validateRule(raw) }.getOrNull()

    private fun JsonObject.string(key: String): String = this[key]
        ?.takeIf { it is JsonPrimitive && it.isString }
        ?.jsonPrimitive
        ?.content
        ?: throw S5WritingSkillException("WRITING_SKILL_JSON_TYPE:$key")

    private fun JsonObject.array(key: String): JsonArray = runCatching { getValue(key).jsonArray }
        .getOrElse { throw S5WritingSkillException("WRITING_SKILL_JSON_TYPE:$key") }

    private fun JsonObject.stringList(key: String): List<String> = array(key).map { element ->
        writingSkillRequire(element is JsonPrimitive && element.isString, "WRITING_SKILL_JSON_TYPE:$key")
        validateRule(element.jsonPrimitive.content)
    }

    private fun readBounded(input: InputStream, maximum: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4_096)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            writingSkillRequire(output.size() + read <= maximum, "WRITING_SKILL_SOURCE_TOO_LARGE")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun decodeUtf8Strict(bytes: ByteArray): String = runCatching {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }.getOrElse { throw S5WritingSkillException("WRITING_SKILL_UTF8_REQUIRED") }

    private data class ParsedCard(
        val name: String,
        val rules: List<String>,
        val avoid: List<String>,
        val preferredTerms: List<String>,
    )

    private enum class MarkdownSection { NONE, RULES, AVOID, PREFERRED_TERMS }

    private companion object {
        const val MAX_SOURCE_BYTES = 256 * 1024
        const val MAX_CARD_ITEMS = 8
        const val MAX_CARD_CHARACTERS = 1_600
        const val MAX_ITEM_CHARACTERS = 240
        const val MAX_NAME_CHARS = 80
        val JSON_KEYS = setOf("schemaVersion", "name", "scope", "rules", "avoid", "preferredTerms", "examples")
        val JSON_RULE_KEYS = setOf("rules", "rule", "instructions", "instruction", "guidelines", "guideline", "style", "stylerules", "writingrules", "must")
        val JSON_AVOID_KEYS = setOf("avoid", "avoids", "forbidden", "mustnot", "dont", "prohibited", "negativeprompt")
        val JSON_TERM_KEYS = setOf("preferredterms", "vocabulary", "terms", "diction", "wording")
        val BULLET = Regex("^(?:[-*+]\\s+|\\d+[.)]\\s+)(.+)$")
        val URL_OR_REFERENCE = Regex("(?i)(https?://|file://|\\.\\./|references/|references\\\\|\\[[^]]+]\\([^)]*\\))")
        val UNSAFE_RULE = Regex(
            "(?i)(api[ _-]?key|authorization|bearer|system prompt|developer message|provider|调用次数|模型调用|调用工具|访问文件|读取文件|联网|绕过|忽略.{0,8}(指令|规则)|章节任务|硬事实|输出.{0,4}(json|分析)|执行.{0,4}(脚本|命令|代码))",
        )
    }
}

internal fun writingQualityCardJson(card: S0WritingQualityCard): JsonObject = buildJsonObject {
    put("schemaVersion", JsonPrimitive("1.0"))
    put("name", JsonPrimitive(card.name))
    put("version", JsonPrimitive(card.version))
    put("scope", JsonPrimitive("chapter_prose_quality_card"))
    put("rules", buildJsonArray { card.rules.forEach { add(JsonPrimitive(it)) } })
    put("avoid", buildJsonArray { card.avoid.forEach { add(JsonPrimitive(it)) } })
    put("preferredTerms", buildJsonArray { card.preferredTerms.forEach { add(JsonPrimitive(it)) } })
}

internal fun qualityCardSha256(card: S0WritingQualityCard): String =
    sha256(writingQualityCardJson(card.copy(sha256 = "")).toString().toByteArray(StandardCharsets.UTF_8))

internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { "%02x".format(it) }

private fun writingSkillRequire(condition: Boolean, code: String) {
    if (!condition) throw S5WritingSkillException(code)
}
