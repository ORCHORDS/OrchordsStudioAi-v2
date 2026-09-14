package com.orchords.orchordsai.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlin.uuid.Uuid

/**
 * Behaviour contract for the on-device encrypted store that holds
 * chat-LLM provider `apiKey` values (#428). Splitting this interface out
 * of [ProviderCredentialStore] lets the [ProviderSecretCodec] be unit
 * tested against an in-memory fake without an Android Keystore.
 */
interface ProviderSecretBackend {
    fun isAvailable(): Boolean
    fun get(providerId: Uuid): String?
    fun put(providerId: Uuid, apiKey: String): Boolean
    fun remove(providerId: Uuid): Boolean
    fun storedProviderIds(): Set<Uuid>
}

/**
 * Production [ProviderSecretBackend]. Wraps [EncryptedSharedPreferences]
 * backed by an AES-256 GCM master key in the Android Keystore. A
 * filesystem-only attacker recovers ciphertext only.
 *
 * Keys are the stable `providerId` (Uuid string). All operations are
 * synchronous (SharedPreferences is in-process) and serialised by the
 * caller; the [SettingsStore] writer coroutine is the single owner.
 */
open class ProviderCredentialStore(
    private val context: Context,
) : ProviderSecretBackend {
    private val prefs: SharedPreferences? = runCatching {
        val masterKey = MasterKey.Builder(context, MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.onFailure { error ->
        // Master key creation can fail on emulator profiles without a usable
        // Android Keystore. The SettingsStore treats this as "secrets
        // unavailable" and refuses to persist non-empty apiKeys, so callers
        // degrade safely rather than silently writing plaintext.
        Log.w(TAG, "Encrypted credential store unavailable; falling back to null", error)
    }.getOrNull()

    override fun isAvailable(): Boolean = prefs != null

    override fun get(providerId: Uuid): String? = prefs?.getString(providerId.toString(), null)

    /**
     * Persist [apiKey] for [providerId]. Blank keys are treated as deletes
     * so a removed provider leaves no stale ciphertext behind. Returns
     * `false` if the encrypted store is unavailable — callers should treat
     * that as a hard error and avoid writing a DataStore JSON that would
     * otherwise lose the key.
     */
    override fun put(providerId: Uuid, apiKey: String): Boolean {
        val store = prefs ?: return false
        return runCatching {
            val editor = store.edit()
            if (apiKey.isBlank()) editor.remove(providerId.toString())
            else editor.putString(providerId.toString(), apiKey)
            editor.commit()
        }.onFailure { error ->
            Log.w(TAG, "Failed to write encrypted credential for $providerId", error)
        }.getOrDefault(false)
    }

    override fun remove(providerId: Uuid): Boolean {
        val store = prefs ?: return false
        return runCatching { store.edit().remove(providerId.toString()).commit() }
            .onFailure { error -> Log.w(TAG, "Failed to remove encrypted credential for $providerId", error) }
            .getOrDefault(false)
    }

    /** Snapshot of all stored provider keys, used by the V5 migration. */
    override fun storedProviderIds(): Set<Uuid> = prefs?.all?.keys.orEmpty().mapNotNull { key ->
        runCatching { Uuid.parse(key) }.getOrNull()
    }.toSet()

    companion object {
        private const val TAG = "ProviderCredentialStore"
        private const val FILE_NAME = "orchordsai_provider_secrets"
        private const val MASTER_KEY_ALIAS = "orchordsai_provider_secrets_master_key"
    }
}
