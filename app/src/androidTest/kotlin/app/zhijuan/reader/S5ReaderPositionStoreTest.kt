package app.zhijuan.reader

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class S5ReaderPositionStoreTest {
    @Test
    fun positionSurvivesStoreRecreationAndCanBeRemovedWithItsProject() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "reader-position-test"
        context.getSharedPreferences(name, android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val expected = S5ReaderPosition(chapterNumber = 3, scrollOffset = 1280)

        S5ReaderPositionStore(context, name).save("project_position", S5ReaderPosition(chapterNumber = 1, scrollOffset = 420))
        S5ReaderPositionStore(context, name).save("project_position", expected)
        assertEquals(expected, S5ReaderPositionStore(context, name).load("project_position"))
        assertEquals(420, S5ReaderPositionStore(context, name).loadChapterOffset("project_position", 1))
        assertEquals(1280, S5ReaderPositionStore(context, name).loadChapterOffset("project_position", 3))

        S5ReaderPositionStore(context, name).remove("project_position")
        assertNull(S5ReaderPositionStore(context, name).load("project_position"))
        assertEquals(0, S5ReaderPositionStore(context, name).loadChapterOffset("project_position", 1))
    }
}
