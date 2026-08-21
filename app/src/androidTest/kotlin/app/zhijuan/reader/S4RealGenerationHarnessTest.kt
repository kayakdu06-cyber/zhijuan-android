package app.zhijuan.reader

import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import app.zhijuan.core.s0.S0ChapterState
import app.zhijuan.data.s0.FileS0NovelRepository
import app.zhijuan.data.s0.provider.OpenAiCompatibleS1Provider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit physical-device acceptance harness. It never reads or prints the protected API key. */
class S4RealGenerationHarnessTest {
    @Test
    fun runOneExplicitForegroundChapterAction() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("manual physical-device harness", arguments.containsKey("expectedRevision"))
        val context = instrumentation.targetContext
        val projectId = arguments.getString("projectId") ?: "release_five_chapter"
        val expectedRevision = arguments.getString("expectedRevision")?.toIntOrNull()
            ?: error("expectedRevision argument is required")
        val action = when (arguments.getString("generationAction")) {
            "retry-settlement" -> ACTION_RETRY_SETTLEMENT
            else -> ACTION_GENERATE
        }
        val fault = arguments.getString("debugFault")
        val repository = FileS0NovelRepository(File(context.filesDir, "zhijuan-projects"))
        assertNotNull(repository.loadProject(projectId))
        assertNotNull(OpenAiCompatibleS1Provider.forApplication(context).connectionSummary())

        val intent = Intent(context, S3GenerationForegroundService::class.java)
            .setAction(action)
            .putExtra(EXTRA_PROJECT_ID, projectId)
        if (!fault.isNullOrBlank()) intent.putExtra(EXTRA_DEBUG_FAULT, fault)
        ContextCompat.startForegroundService(context, intent)

        val deadline = System.currentTimeMillis() + 8L * 60L * 1_000L
        val expectedDraft = arguments.getString("expectDraftChapter")?.toIntOrNull()
        while (System.currentTimeMillis() < deadline) {
            val snapshot = repository.loadProject(projectId)!!
            if (expectedDraft == null && snapshot.storyState.revision >= expectedRevision) {
                assertEquals(expectedRevision, snapshot.storyState.revision)
                assertEquals(S0ChapterState.COMMITTED, snapshot.chapters.single { it.number == expectedRevision }.state)
                return
            }
            if (expectedDraft != null) {
                val chapter = snapshot.chapters.firstOrNull { it.number == expectedDraft }
                if (chapter?.state == S0ChapterState.READABLE_DRAFT || chapter?.state == S0ChapterState.NEEDS_REVIEW) {
                    assertEquals(expectedRevision, snapshot.storyState.revision)
                    return
                }
            }
            Thread.sleep(1_000)
        }
        val finalSnapshot = repository.loadProject(projectId)!!
        assertTrue(
            "generation timeout: revision=${finalSnapshot.storyState.revision}, chapters=${finalSnapshot.chapters.map { it.number to it.state }}",
            false,
        )
    }
}
