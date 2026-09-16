package com.orchords.orchordsai.security

import com.orchords.orchordsai.data.security.ProviderSecretBackend
import com.orchords.orchordsai.data.security.ProviderSecretCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ProviderCredentialRekeyTest {
    @Test
    fun `legacy credential moves to canonical provider id`() {
        val legacyId = Uuid.random()
        val canonicalId = Uuid.random()
        val store = FakeRekeyStore()
        store.seed(legacyId, "legacy-secret")

        val result = ProviderSecretCodec.rekeyProviderCredential(
            legacyProviderId = legacyId,
            canonicalProviderId = canonicalId,
            store = store,
        )

        assertTrue(result)
        assertEquals("legacy-secret", store.get(canonicalId))
        assertNull(store.get(legacyId))
    }

    @Test
    fun `existing canonical credential wins and legacy entry is removed`() {
        val legacyId = Uuid.random()
        val canonicalId = Uuid.random()
        val store = FakeRekeyStore()
        store.seed(legacyId, "legacy-secret")
        store.seed(canonicalId, "canonical-secret")

        val result = ProviderSecretCodec.rekeyProviderCredential(
            legacyProviderId = legacyId,
            canonicalProviderId = canonicalId,
            store = store,
        )

        assertTrue(result)
        assertEquals("canonical-secret", store.get(canonicalId))
        assertNull(store.get(legacyId))
    }

    @Test
    fun `failed canonical write keeps legacy credential for retry`() {
        val legacyId = Uuid.random()
        val canonicalId = Uuid.random()
        val store = FakeRekeyStore(rejectPutFor = canonicalId)
        store.seed(legacyId, "legacy-secret")

        val result = ProviderSecretCodec.rekeyProviderCredential(
            legacyProviderId = legacyId,
            canonicalProviderId = canonicalId,
            store = store,
        )

        assertFalse(result)
        assertNull(store.get(canonicalId))
        assertEquals("legacy-secret", store.get(legacyId))
    }

    @Test
    fun `failed legacy removal reports failure without overwriting canonical credential`() {
        val legacyId = Uuid.random()
        val canonicalId = Uuid.random()
        val store = FakeRekeyStore(rejectRemoveFor = legacyId)
        store.seed(legacyId, "legacy-secret")

        val result = ProviderSecretCodec.rekeyProviderCredential(
            legacyProviderId = legacyId,
            canonicalProviderId = canonicalId,
            store = store,
        )

        assertFalse(result)
        assertEquals("legacy-secret", store.get(canonicalId))
        assertEquals("legacy-secret", store.get(legacyId))
    }
}

private class FakeRekeyStore(
    private val rejectPutFor: Uuid? = null,
    private val rejectRemoveFor: Uuid? = null,
) : ProviderSecretBackend {
    private val backing = mutableMapOf<Uuid, String>()

    fun seed(id: Uuid, value: String) {
        backing[id] = value
    }

    override fun isAvailable(): Boolean = true

    override fun get(providerId: Uuid): String? = backing[providerId]

    override fun put(providerId: Uuid, apiKey: String): Boolean {
        if (providerId == rejectPutFor) return false
        if (apiKey.isBlank()) backing.remove(providerId) else backing[providerId] = apiKey
        return true
    }

    override fun remove(providerId: Uuid): Boolean {
        if (providerId == rejectRemoveFor) return false
        backing.remove(providerId)
        return true
    }

    override fun storedProviderIds(): Set<Uuid> = backing.keys.toSet()
}
