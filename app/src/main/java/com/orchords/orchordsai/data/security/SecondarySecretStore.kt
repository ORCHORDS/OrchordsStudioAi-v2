package com.orchords.orchordsai.data.security

import android.content.Context
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

interface SecondarySecretBackend {
    fun isAvailable(): Boolean
    fun get(name: String): String?
    fun put(name: String, value: String): Boolean
    fun remove(name: String): Boolean
}

object SecondarySecretKey {
    const val WEBDAV_PASSWORD = "webdav.password"
    const val S3_SECRET_ACCESS_KEY = "s3.secret_access_key"
    const val PROXY_USERNAME = "network.proxy_username"
    const val PROXY_PASSWORD = "network.proxy_password"
    const val WEB_SERVER_ACCESS_PASSWORD = "web_server.access_password"
}

/**
 * Small encrypted store for settings credentials that must never be written
 * to the Settings DataStore JSON. Values are encrypted with an AES/GCM key
 * held by Android Keystore; SharedPreferences only contains versioned
 * ciphertext, IVs, and logical field names.
 *
 * This intentionally uses platform Keystore APIs directly. AndroidX
 * security-crypto's EncryptedSharedPreferences API is deprecated, so new
 * credential storage must not extend that dependency.
 */
open class SecondarySecretStore(context: Context) : SecondarySecretBackend {
    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val key: SecretKey? = runCatching { loadOrCreateKey() }
        .onFailure { error -> Log.w(TAG, "Secondary secret store unavailable", error) }
        .getOrNull()

    override fun isAvailable(): Boolean = key != null

    override fun get(name: String): String? {
        val secretKey = key ?: return null
        val payload = prefs.getString(name, null) ?: return null
        return runCatching {
            val parts = payload.split(':', limit = 3)
            require(parts.size == 3 && parts[0] == FORMAT_VERSION) { "Unsupported secret payload" }
            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            val encrypted = Base64.decode(parts[2], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        }.onFailure { error -> Log.w(TAG, "Failed to decrypt secondary secret $name", error) }
            .getOrNull()
    }

    override fun put(name: String, value: String): Boolean {
        if (value.isBlank()) return remove(name)
        val secretKey = key ?: return false
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val payload = buildString {
                append(FORMAT_VERSION)
                append(':')
                append(Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                append(':')
                append(Base64.encodeToString(encrypted, Base64.NO_WRAP))
            }
            prefs.edit().putString(name, payload).commit()
        }.onFailure { error -> Log.w(TAG, "Failed to encrypt secondary secret $name", error) }
            .getOrDefault(false)
    }

    override fun remove(name: String): Boolean = runCatching {
        prefs.edit().remove(name).commit()
    }.onFailure { error -> Log.w(TAG, "Failed to remove secondary secret $name", error) }
        .getOrDefault(false)

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }

    companion object {
        private const val TAG = "SecondarySecretStore"
        private const val FILE_NAME = "orchordsai_secondary_secrets"
        private const val KEY_ALIAS = "orchordsai_secondary_secrets_master_key"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        private const val FORMAT_VERSION = "v1"
    }
}
