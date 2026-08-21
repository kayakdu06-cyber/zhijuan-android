package app.zhijuan.core.s0

class S0FakeProvider : S0TextGenerationProvider {
    var proseCalls: Int = 0
        private set
    var settlementCalls: Int = 0
        private set

    override suspend fun streamProse(task: S0ChapterTask, onChunk: (String) -> Unit): String {
        proseCalls += 1
        val text = "雨停在旧车站的檐角。${task.goal} 林岑把写着回卷印记的纸页收进书里，决定先去灯下确认它的来处。"
        text.chunked(12).forEach(onChunk)
        return text
    }

    override suspend fun completeSettlement(task: S0ChapterTask, prose: String): S0Settlement {
        settlementCalls += 1
        check(prose.isNotBlank())
        return S0Settlement(
            taskId = task.taskId,
            chapter = task.chapter,
            baseRevision = task.baseRevision,
            summary = "林岑在旧车站确认回卷印记的来处，带着新的线索离开。",
            eventKey = "chapter_${task.chapter}_station_clue",
            eventDescription = "获得旧车站线索：回卷印记与灯下档案有关。",
        )
    }
}
