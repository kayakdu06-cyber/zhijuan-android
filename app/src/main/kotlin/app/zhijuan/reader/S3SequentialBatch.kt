package app.zhijuan.reader

import app.zhijuan.core.s0.S0GenerationResult

internal data class S3SequentialBatchResult(
    val requested: Int,
    val completed: Int,
    val terminal: S0GenerationResult,
)

/**
 * Holds only the current process's explicit batch intent. Each invocation of [generateChapter]
 * must still create and finish one independent one-chapter generation job.
 */
internal suspend fun runS3SequentialBatch(
    requested: Int,
    generateChapter: suspend (position: Int) -> S0GenerationResult,
    afterCommit: suspend (position: Int, result: S0GenerationResult.Committed) -> Unit = { _, _ -> },
): S3SequentialBatchResult {
    require(requested in 1..3) { "BATCH_SIZE_INVALID" }
    var completed = 0
    var terminal: S0GenerationResult? = null
    for (position in 1..requested) {
        val result = generateChapter(position)
        terminal = result
        if (result !is S0GenerationResult.Committed) break
        completed += 1
        afterCommit(position, result)
    }
    return S3SequentialBatchResult(
        requested = requested,
        completed = completed,
        terminal = requireNotNull(terminal),
    )
}
