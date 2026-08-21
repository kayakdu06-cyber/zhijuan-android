package app.zhijuan.data.s0.provider

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal class AndroidKeystoreS1SecretStore(
    context: Context,
) : S1ProviderSecretStore {
    private val directory = File(context.noBackupFilesDir, "provider-credentials")

    override fun save(secret: CharArray): String = synchronized(lock) {
        require(secret.size in 8..16_384)
        require(secret.all { it.code in 0x21..0x7e })
        val alias = "novel_api_key_${UUID.randomUUID().toString().replace("-", "")}"
        val plaintext = secret.concatToString().toByteArray(Charsets.US_ASCII)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, generateKey(alias))
            cipher.updateAAD(alias.toByteArray(Charsets.UTF_8))
            val payload = buildJsonObject {
                put("schemaVersion", "1.0")
                put("credentialAlias", alias)
                put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                put("ciphertext", Base64.encodeToString(cipher.doFinal(plaintext), Base64.NO_WRAP))
            }.toString().toByteArray(Charsets.UTF_8)
            try {
                atomicWrite(file(alias), payload)
            } catch (failure: Throwable) {
                deleteKey(alias)
                throw failure
            }
            alias
        } finally {
            plaintext.fill(0)
        }
    }

    override fun <T> withSecret(credentialAlias: String, block: (CharArray) -> T): T = synchronized(lock) {
        require(credentialAlias.matches(ALIAS_PATTERN))
        val credentialFile = file(credentialAlias)
        require(credentialFile.isFile && credentialFile.length() in 1..MAX_RECORD_BYTES)
        val root = Json.parseToJsonElement(credentialFile.readText(Charsets.UTF_8)).jsonObject
        require(root.keys == setOf("schemaVersion", "credentialAlias", "iv", "ciphertext"))
        require(root.getValue("schemaVersion").jsonPrimitive.content == "1.0")
        require(root.getValue("credentialAlias").jsonPrimitive.content == credentialAlias)
        val iv = Base64.decode(root.getValue("iv").jsonPrimitive.content, Base64.NO_WRAP)
        val ciphertext = Base64.decode(root.getValue("ciphertext").jsonPrimitive.content, Base64.NO_WRAP)
        require(iv.size in 12..16 && ciphertext.size in 17..MAX_CIPHERTEXT_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, requireKey(credentialAlias), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(credentialAlias.toByteArray(Charsets.UTF_8))
        val plaintext = cipher.doFinal(ciphertext)
        val characters = plaintext.toString(Charsets.US_ASCII).toCharArray()
        return@synchronized try {
            block(characters)
        } finally {
            characters.fill('\u0000')
            plaintext.fill(0)
        }
    }

    override fun delete(credentialAlias: String) = synchronized(lock) {
        if (!credentialAlias.matches(ALIAS_PATTERN)) return@synchronized
        file(credentialAlias).delete()
        File(directory, "$credentialAlias.json.bak").delete()
        deleteKey(credentialAlias)
    }

    private fun file(alias: String): File = File(directory, "$alias.json")

    private fun generateKey(alias: String): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private fun requireKey(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return requireNotNull(keyStore.getKey(alias, null) as? SecretKey) {
            "PROVIDER_CREDENTIAL_UNAVAILABLE"
        }
    }

    private fun deleteKey(alias: String) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val MAX_RECORD_BYTES = 64 * 1024L
        const val MAX_CIPHERTEXT_BYTES = 32 * 1024
        val ALIAS_PATTERN = Regex("novel_api_key_[A-Za-z0-9_-]+")
        val lock = Any()
    }
}
