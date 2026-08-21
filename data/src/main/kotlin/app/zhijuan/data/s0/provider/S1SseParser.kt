package app.zhijuan.data.s0.provider

import java.io.ByteArrayOutputStream

internal data class S1SseEvent(val data: String)

internal class S1SseParser(
    private val maxLineBytes: Int = 1_048_576,
) {
    private val line = ByteArrayOutputStream()
    private val dataLines = mutableListOf<String>()
    private var finished = false
    private var firstLine = true

    fun feed(chunk: ByteArray): List<S1SseEvent> {
        check(!finished)
        val output = mutableListOf<S1SseEvent>()
        chunk.forEach { byte ->
            if (byte == '\n'.code.toByte()) {
                processLine(decodeLine(), output)
                line.reset()
            } else {
                require(line.size() < maxLineBytes) { "SSE_LINE_LIMIT_EXCEEDED" }
                line.write(byte.toInt())
            }
        }
        return output
    }

    fun finish(): List<S1SseEvent> {
        check(!finished)
        finished = true
        line.reset()
        dataLines.clear()
        return emptyList()
    }

    private fun decodeLine(): String {
        val bytes = line.toByteArray()
        val length = if (bytes.lastOrNull() == '\r'.code.toByte()) bytes.size - 1 else bytes.size
        val value = bytes.copyOf(length).toString(Charsets.UTF_8)
        return if (firstLine) {
            firstLine = false
            value.removePrefix("\uFEFF")
        } else {
            value
        }
    }

    private fun processLine(value: String, output: MutableList<S1SseEvent>) {
        if (value.isEmpty()) {
            if (dataLines.isNotEmpty()) output += S1SseEvent(dataLines.joinToString("\n"))
            dataLines.clear()
            return
        }
        if (value.startsWith(':')) return
        val separator = value.indexOf(':')
        val field = if (separator < 0) value else value.substring(0, separator)
        val raw = if (separator < 0) "" else value.substring(separator + 1).removePrefix(" ")
        if (field == "data") dataLines += raw
    }
}
