package com.orchords.orchordsai.data.security

import com.orchords.ai.provider.ProviderSetting
import kotlin.uuid.Uuid

/**
 * Pure-Kotlin helpers that bridge the plaintext-free `ProviderSetting` shape
 * (after [ProviderSetting.OpenAI.apiKey] / [ProviderSetting.Google.apiKey] /
 * [ProviderSetting.Claude.apiKey] became `@Transient`) and the encrypted
 * credential store. Splitting these from [ProviderCredentialStore] keeps the
 * codec logic unit-testable on a plain JVM (no Android Keystore required).
 *
 * #428 requires:
 * - No non-empty `apiKey` value ever lands in the Settings DataStore JSON.
 * - The `providers` collection exposed to the rest of the app continues to
 *   carry the live key, hydrated from [ProviderCredentialStore].
 */
object ProviderSecretCodec {
    /**
     * Persist each provider's apiKey into [store] and return a copy with the
     * `apiKey` field redacted to `""`, suitable for serialisation into the
     * Settings DataStore.
     *
     * Returns `null` when [store] is unavailable AND any incoming provider
     * carries a non-empty key — the caller must refuse to write in that
     * case rather than silently lose the credential.
     */
    fun redactProvidersForWrite(
        providers: List<ProviderSetting>,
        store: ProviderSecretBackend,
    ): List<ProviderSetting>? {
        if (!store.isAvailable() && providers.any { it.apiKey.isNotEmpty() }) return null
        // Snapshot the currently-stored provider IDs so we can target
        // removals only at entries that actually exist. Removing an
        // absent entry would be a no-op for the real EncryptedShared-
        // Preferences store but our production contract returns a
        // Boolean to make failures detectable; we must not call
        // `remove` for keys that were never stored, otherwise an
        // unavailable-keystore edge case (legacy install before first
        // backend init) would falsely surface as a write failure.
        val storedBefore = if (store.isAvailable()) store.storedProviderIds() else emptySet()
        return providers.map { provider ->
            if (provider.apiKey.isBlank()) {
                if (provider.id in storedBefore && !store.remove(provider.id)) return null
                provider.withApiKey("")
            } else {
                if (!store.put(provider.id, provider.apiKey)) return null
                provider.withApiKey("")
            }
        }
    }

    /**
     * Populate each provider's `apiKey` from [store]. Missing keys (legacy
     * installs that never had an entry) hydrate to `""`.
     */
    fun hydrateProvidersFromStore(
        providers: List<ProviderSetting>,
        store: ProviderSecretBackend,
    ): List<ProviderSetting> = providers.map { provider ->
        provider.withApiKey(store.get(provider.id) ?: "")
    }

    /**
     * Delete every entry from [store] whose provider id is in [previousIds]
     * but no longer appears in [newProviders]. Used by the deletion path
     * (`SettingsStore.update` removes a provider outright rather than
     * blanking its apiKey) so a removed provider leaves no stale
     * ciphertext behind.
     *
     * Returns `true` when the store was either unavailable (nothing to do)
     * or every targeted removal succeeded. Returns `false` if the store
     * is available but any removal failed — the caller must refuse the
     * pending DataStore write so we never persist a redacted list while
     * a key for a removed provider still exists on disk.
     *
     * Only targets ids that actually exist in [store] to avoid spurious
     * "unavailable-keystore" failures for legacy installs that never had
     * an entry under that id.
     */
    fun removeDroppedProviders(
        previousIds: Set<Uuid>,
        newProviders: List<ProviderSetting>,
        store: ProviderSecretBackend,
    ): Boolean {
        if (!store.isAvailable()) return true
        val stored = store.storedProviderIds()
        val newIds = newProviders.mapTo(HashSet(newProviders.size)) { it.id }
        val dropped = (previousIds intersect stored) - newIds
        for (id in dropped) {
            if (!store.remove(id)) return false
        }
        return true
    }

    private fun ProviderSetting.withApiKey(value: String): ProviderSetting = when (this) {
        is ProviderSetting.OpenAI -> copy(apiKey = value)
        is ProviderSetting.Google -> copy(apiKey = value)
        is ProviderSetting.Claude -> copy(apiKey = value)
    }
}
