package app.zhijuan.reader

import android.content.Context

enum class S3ReaderTheme { SYSTEM, LIGHT, DARK }

data class S3ReaderPreferences(
    val fontSizeSp: Int = 18,
    val lineHeightSp: Int = 30,
    val theme: S3ReaderTheme = S3ReaderTheme.SYSTEM,
) {
    init {
        require(fontSizeSp in setOf(16, 18, 20, 22))
        require(lineHeightSp in setOf(26, 30, 34, 38))
    }
}

class S3ReaderPreferencesStore(
    context: Context,
    preferencesName: String = "zhijuan-reader-settings",
) {
    private val preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    fun load(): S3ReaderPreferences = S3ReaderPreferences(
        fontSizeSp = preferences.getInt("fontSizeSp", 18).takeIf { it in setOf(16, 18, 20, 22) } ?: 18,
        lineHeightSp = preferences.getInt("lineHeightSp", 30).takeIf { it in setOf(26, 30, 34, 38) } ?: 30,
        theme = runCatching {
            S3ReaderTheme.valueOf(preferences.getString("theme", S3ReaderTheme.SYSTEM.name).orEmpty())
        }.getOrDefault(S3ReaderTheme.SYSTEM),
    )

    fun save(value: S3ReaderPreferences) {
        preferences.edit()
            .putInt("fontSizeSp", value.fontSizeSp)
            .putInt("lineHeightSp", value.lineHeightSp)
            .putString("theme", value.theme.name)
            .apply()
    }
}
