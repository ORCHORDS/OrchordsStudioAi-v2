package com.orchords.orchordsai.security

import com.orchords.ai.provider.ProviderSetting
import com.orchords.orchordsai.data.security.ProviderSecretBackend
import com.orchords.orchordsai.data.security.ProviderSecretCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * Behavioural test for [ProviderSecretCodec] using an in-memory fake of
 * [ProviderCredentialStore]. The codec must round-trip apiKey values
 * through the encrypted store and ensure the redacted list never carries
 * a plaintext key (#428).
 */
class ProviderSecretCodecTest {
    @Test
    fun `redactProvidersForWrite copies apiKey into the encrypted store and blanks the wire shape`() {
        val store = FakeCredentialStore(available = true)
        val openAiId = Uuid.random()
        val googleId = Uuid.random()
        val providers = listOf(
            ProviderSetting.OpenAI(id = openAiId, apiKey = "sk-plaintext-secret"),
            ProviderSetting.Google(id = googleId, apiKey = "AIza-plaintext"),
            ProviderSetting.Claude(id = Uuid.random(), apiKey = ""),
        )

        val redacted = ProviderSecretCodec.redactProvidersForWrite(providers, store)

        assertEquals("all three providers must round-trip", 3, redacted?.size)
        redacted!!.forEach { assertEquals("apiKey must be blanked for every provider", "", it.apiKey) }
        assertEquals("OpenAI key must be persisted to the encrypted store", "sk-plaintext-secret", store.get(openAiId))
        assertEquals("Google key must be persisted to the encrypted store", "AIza-plaintext", store.get(googleId))
        assertEquals(
            "Exactly the two providers with non-empty keys must be written",
            2,
            store.storedCount,
        )
    }

    @Test
    fun `redactProvidersForWrite returns null when the encrypted store is unavailable and a real key is present`() {
        val store = FakeCredentialStore(available = false)
        val providers = listOf(
            ProviderSetting.OpenAI(id = Uuid.random(), apiKey = "sk-plaintext-secret"),
        )

        val redacted = ProviderSecretCodec.redactProvidersForWrite(providers, store)

        assertNull(
            "Codec must refuse to redact when the encrypted store is unavailable and a real key is present (#428).",
            redacted,
        )
    }

    @Test
    fun `redactProvidersForWrite allows writes when keys are all blank even if the store is unavailable`() {
        val store = FakeCredentialStore(available = false)
        val providers = listOf(
            ProviderSetting.OpenAI(id = Uuid.random(), apiKey = ""),
            ProviderSetting.Google(id = Uuid.random(), apiKey = ""),
        )

        val redacted = ProviderSecretCodec.redactProvidersForWrite(providers, store)

        assertEquals("Blank-key providers must still be re-emittable with a blanked apiKey", 2, redacted?.size)
        redacted!!.forEach { assertEquals("", it.apiKey) }
    }

    @Test
    fun `hydrateProvidersFromStore populates apiKey for every provider`() {
        val store = FakeCredentialStore(available = true)
        val openAiId = Uuid.random()
        val googleId = Uuid.random()
        store.put(openAiId, "sk-stored")
        store.put(googleId, "AIza-stored")

        val wireShape = listOf(
            ProviderSetting.OpenAI(id = openAiId, apiKey = ""),
            ProviderSetting.Google(id = googleId, apiKey = ""),
            ProviderSetting.Claude(id = Uuid.random(), apiKey = ""),
        )

        val hydrated = ProviderSecretCodec.hydrateProvidersFromStore(wireShape, store)

        assertEquals("sk-stored", hydrated[0].apiKey)
        assertEquals("AIza-stored", hydrated[1].apiKey)
        assertEquals("Missing keys hydrate to empty string", "", hydrated[2].apiKey)
    }

    @Test
    fun `redactProvidersForWrite then hydrateProvidersFromStore round-trips the original key`() {
        val store = FakeCredentialStore(available = true)
        val id = Uuid.random()
        val providers = listOf(ProviderSetting.OpenAI(id = id, apiKey = "sk-sticky"))

        val first = ProviderSecretCodec.redactProvidersForWrite(providers, store)!!
        val second = ProviderSecretCodec.hydrateProvidersFromStore(first, store)

        assertNotEquals("First pass must not carry the original plaintext on the wire", "sk-sticky", first[0].apiKey)
        assertEquals("Second pass must hydrate the same value back", "sk-sticky", second[0].apiKey)
    }

    @Test
    fun `redactProvidersForWrite preserves the encrypted-store failure when the backend rejects the write`() {
        val store = RejectingFakeCredentialStore(available = true, reject = true)
        val id = Uuid.random()
        val providers = listOf(ProviderSetting.OpenAI(id = id, apiKey = "sk-do-not-lose"))

        val redacted = ProviderSecretCodec.redactProvidersForWrite(providers, store)

        assertNull(
            "Codec must surface a backend write failure as a hard refusal so SettingsStore aborts " +
                "rather than overwrite the legacy apiKey with an empty wire value.",
            redacted,
        )
        assertEquals(
            "A failing write must not leave a stale empty key in the encrypted store.",
            false,
            store.get(id) == "",
        )
    }

    @Test
    fun `redactProvidersForWrite removes the encrypted entry when the provider apiKey is cleared`() {
        val store = FakeCredentialStore(available = true)
        val id = Uuid.random()
        store.put(id, "sk-original")
        val providers = listOf(ProviderSetting.OpenAI(id = id, apiKey = ""))

        val redacted = ProviderSecretCodec.redactProvidersForWrite(providers, store)

        assertEquals(1, redacted?.size)
        redacted!!.forEach { assertEquals("", it.apiKey) }
        assertNull("Removing a provider must drop the encrypted entry", store.get(id))
        assertEquals("store should be empty after deletion", 0, store.storedCount)
    }

    @Test
    fun `redactProvidersForWrite surfaces removal failure when the backend rejects the delete`() {
        // `put` succeeds (so the codec sees the legacy key as already
        // stored) but `remove` returns false. The codec must still
        // surface that failure as a hard refusal so SettingsStore
        // aborts instead of overwriting the DataStore JSON.
        val store = PutOnlyFakeCredentialStore()
        val id = Uuid.random()
        store.put(id, "sk-keep")
        val providers = listOf(ProviderSetting.OpenAI(id = id, apiKey = ""))

        val redacted = ProviderSecretCodec.redactProvidersForWrite(providers, store)

        assertNull(
            "A removal failure must abort the redacted write so the DataStore JSON is not overwritten.",
            redacted,
        )
    }
}

/**
 * Pure-JVM fake of [ProviderSecretBackend]. The codec is the unit under
 * test; this fake mirrors the production contract so we can exercise
 * round-trips without an Android Keystore (#428).
 */
private class FakeCredentialStore(private val available: Boolean) : ProviderSecretBackend {
    private val backing = mutableMapOf<Uuid, String>()

    val storedCount: Int get() = backing.size

    override fun isAvailable(): Boolean = available

    override fun get(providerId: Uuid): String? = backing[providerId]

    override fun put(providerId: Uuid, apiKey: String): Boolean {
        if (!available) return false
        if (apiKey.isBlank()) backing.remove(providerId) else backing[providerId] = apiKey
        return true
    }

    override fun remove(providerId: Uuid): Boolean {
        if (!available) return false
        backing.remove(providerId)
        return true
    }

    override fun storedProviderIds(): Set<Uuid> = backing.keys.toSet()
}

/**
 * Codec test double used to exercise failure paths. When `reject = true`,
 * [put] and [remove] report the operation as failed even though the store
 * is "available" — mirroring an Android Keystore write/delete that returns
 * `false` (the production contract).
 */
private class RejectingFakeCredentialStore(
    private val available: Boolean,
    private val reject: Boolean,
) : ProviderSecretBackend {
    private val backing = mutableMapOf<Uuid, String>()

    override fun isAvailable(): Boolean = available

    override fun get(providerId: Uuid): String? = backing[providerId]

    override fun put(providerId: Uuid, apiKey: String): Boolean {
        if (!available || reject) return false
        if (apiKey.isBlank()) backing.remove(providerId) else backing[providerId] = apiKey
        return true
    }

    override fun remove(providerId: Uuid): Boolean {
        if (!available || reject) return false
        backing.remove(providerId)
        return true
    }

    override fun storedProviderIds(): Set<Uuid> = backing.keys.toSet()
}

/**
 * Codec test double used to exercise the removal-failure path: writes
 * succeed (so the codec sees the legacy key as already stored) but
 * deletes report failure. Mirrors an Android Keystore that returns
 * `false` from `EncryptedSharedPreferences.Editor.remove().commit()`.
 */
private class PutOnlyFakeCredentialStore : ProviderSecretBackend {
    private val backing = mutableMapOf<Uuid, String>()

    override fun isAvailable(): Boolean = true

    override fun get(providerId: Uuid): String? = backing[providerId]

    override fun put(providerId: Uuid, apiKey: String): Boolean {
        if (apiKey.isBlank()) backing.remove(providerId) else backing[providerId] = apiKey
        return true
    }

    override fun remove(providerId: Uuid): Boolean = false

    override fun storedProviderIds(): Set<Uuid> = backing.keys.toSet()
}
