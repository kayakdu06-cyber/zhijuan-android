package app.zhijuan.reader

import android.content.Context

data class S5ReaderPosition(
    val chapterNumber: Int,
    val scrollOffset: Int,
)

class S5ReaderPositionStore(
    context: Context,
    preferencesName: String = "zhijuan-reader-positions",
) {
    private val preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    fun load(projectId: String): S5ReaderPosition? {
        val chapter = preferences.getInt("$projectId.chapter", -1)
        if (chapter < 1) return null
        return S5ReaderPosition(
            chapterNumber = chapter,
            scrollOffset = loadChapterOffset(projectId, chapter),
        )
    }

    fun loadChapterOffset(projectId: String, chapterNumber: Int): Int {
        require(projectId.matches(Regex("[A-Za-z0-9_-]{3,80}")))
        require(chapterNumber >= 1)
        val chapterKey = "$projectId.offset.$chapterNumber"
        if (preferences.contains(chapterKey)) {
            return preferences.getInt(chapterKey, 0).coerceAtLeast(0)
        }
        val lastChapter = preferences.getInt("$projectId.chapter", -1)
        return if (lastChapter == chapterNumber) {
            preferences.getInt("$projectId.offset", 0).coerceAtLeast(0)
        } else {
            0
        }
    }

    fun save(projectId: String, position: S5ReaderPosition) {
        require(projectId.matches(Regex("[A-Za-z0-9_-]{3,80}")))
        require(position.chapterNumber >= 1)
        preferences.edit()
            .putInt("$projectId.chapter", position.chapterNumber)
            .putInt("$projectId.offset.${position.chapterNumber}", position.scrollOffset.coerceAtLeast(0))
            .remove("$projectId.offset")
            .apply()
    }

    fun remove(projectId: String) {
        require(projectId.matches(Regex("[A-Za-z0-9_-]{3,80}")))
        val prefix = "$projectId."
        val editor = preferences.edit()
        preferences.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
        editor.apply()
    }
}
