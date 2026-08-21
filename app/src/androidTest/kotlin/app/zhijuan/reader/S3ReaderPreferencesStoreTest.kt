package app.zhijuan.reader

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class S3ReaderPreferencesStoreTest {
    @Test
    fun preferencesSurviveStoreRecreationAndInvalidValuesFallBackSafely() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "reader-settings-test"
        context.getSharedPreferences(name, android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val store = S3ReaderPreferencesStore(context, name)
        val expected = S3ReaderPreferences(22, 38, S3ReaderTheme.DARK)

        store.save(expected)

        assertEquals(expected, S3ReaderPreferencesStore(context, name).load())
        context.getSharedPreferences(name, android.content.Context.MODE_PRIVATE).edit()
            .putInt("fontSizeSp", 99)
            .putInt("lineHeightSp", -1)
            .putString("theme", "INVALID")
            .commit()
        assertEquals(S3ReaderPreferences(), S3ReaderPreferencesStore(context, name).load())
    }
}
