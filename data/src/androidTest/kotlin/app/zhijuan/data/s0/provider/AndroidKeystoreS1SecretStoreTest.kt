package app.zhijuan.data.s0.provider

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.security.KeyStore
import org.junit.Assert.assertThrows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreS1SecretStoreTest {
    @Test
    fun credentialRoundTripUsesKeystoreAndNoBackupCiphertext() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = AndroidKeystoreS1SecretStore(context)
        val original = "ZHIJUAN_ANDROID_KEY_001".toCharArray()

        val alias = store.save(original)
        val recovered = store.withSecret(alias) { it.concatToString() }

        assertEquals("ZHIJUAN_ANDROID_KEY_001", recovered)
        assertTrue(alias.startsWith("novel_api_key_"))
        val record = File(context.noBackupFilesDir, "provider-credentials/$alias.json")
        assertTrue(record.isFile)
        assertFalse(record.readText().contains("ZHIJUAN_ANDROID_KEY_001"))
        store.delete(alias)
        assertFalse(record.exists())
    }

    @Test
    fun missingKeystoreKeyNeverReturnsCiphertextAsASecret() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = AndroidKeystoreS1SecretStore(context)
        val alias = store.save("ZHIJUAN_ANDROID_KEY_002".toCharArray())
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.deleteEntry(alias)

        assertThrows(IllegalArgumentException::class.java) {
            store.withSecret(alias) { it.concatToString() }
        }

        store.delete(alias)
    }
}
