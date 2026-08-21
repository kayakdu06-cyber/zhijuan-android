package app.zhijuan.reader

import androidx.test.platform.app.InstrumentationRegistry
import app.zhijuan.data.s0.FileS0NovelRepository
import app.zhijuan.data.s0.S4ProjectArchive
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class S4PhysicalArchiveRoundTripTest {
    @Test
    fun realFiveChapterProjectRoundTripsWithMatchingAggregateHash() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.filesDir, "zhijuan-projects")
        val repository = FileS0NovelRepository(root)
        val source = repository.loadProject(PROJECT_ID)
        assumeTrue("five-chapter physical fixture is not present", source?.storyState?.revision == 5)
        val bytes = ByteArrayOutputStream()
        val archive = S4ProjectArchive(root)
        val exported = archive.export(PROJECT_ID, bytes)
        val imported = archive.import(ByteArrayInputStream(bytes.toByteArray()))
        val importedDirectory = File(root, imported.projectId)
        try {
            val restored = repository.loadProject(imported.projectId)!!
            assertEquals(5, restored.storyState.revision)
            assertEquals((1..5).toList(), restored.storyState.committedChapters)
            assertEquals(source!!.chapters.map { it.prose }, restored.chapters.map { it.prose })
            assertEquals(exported.contentSha256, imported.contentSha256)
            assertEquals(exported.totalBytes, imported.totalBytes)
            assertFalse(bytes.toString(Charsets.ISO_8859_1).contains("provider-settings"))
            assertFalse(bytes.toString(Charsets.ISO_8859_1).contains("credentialAlias"))
        } finally {
            check(importedDirectory.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()))
            assertTrue(importedDirectory.deleteRecursively())
        }
    }

    private companion object {
        const val PROJECT_ID = "release_five_chapter"
    }
}
