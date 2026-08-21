package app.zhijuan.reader

import app.zhijuan.core.s0.S0Chapter
import app.zhijuan.core.s0.S0ChapterState
import app.zhijuan.core.s0.S0GenerationResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class S3SequentialBatchTest {
    @Test
    fun `three chapter request runs strictly in order and reports three commits`() = runBlocking {
        val started = mutableListOf<Int>()
        val committed = mutableListOf<Int>()

        val result = runS3SequentialBatch(
            requested = 3,
            generateChapter = { position ->
                started += position
                committed(position)
            },
            afterCommit = { position, _ -> committed += position },
        )

        assertEquals(listOf(1, 2, 3), started)
        assertEquals(listOf(1, 2, 3), committed)
        assertEquals(3, result.completed)
        assertEquals(3, (result.terminal as S0GenerationResult.Committed).chapter.number)
    }

    @Test
    fun `first non committed result stops the remaining batch`() = runBlocking {
        val started = mutableListOf<Int>()

        val result = runS3SequentialBatch(requested = 3, generateChapter = { position ->
            started += position
            if (position == 2) S0GenerationResult.Rejected("NETWORK_OFFLINE") else committed(position)
        })

        assertEquals(listOf(1, 2), started)
        assertEquals(1, result.completed)
        assertEquals("NETWORK_OFFLINE", (result.terminal as S0GenerationResult.Rejected).reason)
    }

    @Test
    fun `batch size is limited to explicit one two or three choices`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                runS3SequentialBatch(
                    requested = 4,
                    generateChapter = { committed(it) },
                )
            }
        }
    }

    private fun committed(chapter: Int) = S0GenerationResult.Committed(
        chapter = S0Chapter(
            number = chapter,
            title = "第${chapter}章",
            taskId = "task_$chapter",
            prose = "正文$chapter",
            state = S0ChapterState.COMMITTED,
        ),
        proseCalls = 1,
        settlementCalls = 1,
    )
}
