package com.orchords.orchordsai.data.datastore

import com.orchords.ai.provider.ProviderSetting
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstPartyConfigurationHealthTest {
    private fun provider(apiKey: String = "gateway-secret"): ProviderSetting.OpenAI =
        (DEFAULT_PROVIDERS.single() as ProviderSetting.OpenAI).copy(apiKey = apiKey)

    @Test
    fun `canonical route with credential is healthy`() {
        val result = evaluateFirstPartyChatHealth(listOf(provider()))

        assertEquals(ConfigurationHealthState.HEALTHY, result.state)
        assertTrue(result.findings.isEmpty())
    }

    @Test
    fun `missing credential is incomplete rather than runtime failure`() {
        val result = evaluateFirstPartyChatHealth(listOf(provider(apiKey = "   ")))

        assertEquals(ConfigurationHealthState.INCOMPLETE, result.state)
        assertEquals(
            listOf(ConfigurationHealthFinding.MISSING_GATEWAY_CREDENTIAL),
            result.findings,
        )
    }

    @Test
    fun `canonical route with legacy provider id is degraded`() {
        val result = evaluateFirstPartyChatHealth(
            listOf(provider().copy(id = Uuid.random()))
        )

        assertEquals(ConfigurationHealthState.DEGRADED, result.state)
        assertEquals(
            listOf(ConfigurationHealthFinding.NONCANONICAL_PROVIDER_ID),
            result.findings,
        )
    }

    @Test
    fun `missing first-party registration is blocked`() {
        val result = evaluateFirstPartyChatHealth(emptyList())

        assertEquals(ConfigurationHealthState.BLOCKED, result.state)
        assertEquals(
            listOf(ConfigurationHealthFinding.MISSING_FIRST_PARTY_PROVIDER),
            result.findings,
        )
    }

    @Test
    fun `unexpected gateway route is blocked`() {
        val result = evaluateFirstPartyChatHealth(
            listOf(provider().copy(baseUrl = "https://example.invalid/v1"))
        )

        assertEquals(ConfigurationHealthState.BLOCKED, result.state)
        assertTrue(ConfigurationHealthFinding.UNEXPECTED_GATEWAY_ROUTE in result.findings)
    }

    @Test
    fun `missing oai model registration is blocked`() {
        val result = evaluateFirstPartyChatHealth(
            listOf(provider().copy(models = emptyList()))
        )

        assertEquals(ConfigurationHealthState.BLOCKED, result.state)
        assertTrue(ConfigurationHealthFinding.MISSING_OAI_MODEL in result.findings)
    }


    @Test
    fun `missing oai-1_2 registration is degraded and repairable`() {
        val legacyOnly = provider().copy(
            models = provider().models.filter { it.modelId == ORCHORDS_MODEL_ID },
        )

        val result = evaluateFirstPartyChatHealth(listOf(legacyOnly))

        assertEquals(ConfigurationHealthState.DEGRADED, result.state)
        assertEquals(
            listOf(ConfigurationHealthFinding.MISSING_OAI_1_2_MODEL),
            result.findings,
        )
    }

    @Test
    fun `health result never contains gateway credential value`() {
        val sentinel = "SENTINEL-DO-NOT-EXPOSE"
        val source = provider(apiKey = sentinel)

        val result = evaluateFirstPartyChatHealth(listOf(source))

        assertFalse(result.toString().contains(sentinel))
        assertEquals(sentinel, source.apiKey)
    }
}
