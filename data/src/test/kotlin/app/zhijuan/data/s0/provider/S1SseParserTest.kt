package app.zhijuan.data.s0.provider

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class S1SseParserTest {
    @Test
    fun `arbitrary byte boundaries preserve utf8 and ignore heartbeats`() {
        val payload = (
            ": keep-alive\r\n\r\n" +
                "data: {\"text\":\"第一段🌙\"}\r\n\r\n" +
                "data: line-one\n" +
                "data: line-two\n\n" +
                "data: [DONE]\n\n"
            ).toByteArray(Charsets.UTF_8)
        val parser = S1SseParser()

        val events = payload.asList().chunked(1).flatMap { byte ->
            parser.feed(byte.toByteArray())
        } + parser.finish()

        assertEquals(
            listOf("{\"text\":\"第一段🌙\"}", "line-one\nline-two", "[DONE]"),
            events.map(S1SseEvent::data),
        )
    }

    @Test
    fun `unterminated event is discarded at eof`() {
        val parser = S1SseParser()

        val events = parser.feed("data: partial".toByteArray()) + parser.finish()

        assertEquals(emptyList<S1SseEvent>(), events)
    }
}
