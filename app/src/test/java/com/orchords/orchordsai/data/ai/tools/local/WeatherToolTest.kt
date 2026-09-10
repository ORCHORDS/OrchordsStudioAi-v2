package com.orchords.orchordsai.data.ai.tools.local

import java.time.Instant
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherToolTest {
    private val location = ResolvedWeatherLocation(
        label = "Kuala Lumpur, Malaysia",
        latitude = 3.139,
        longitude = 101.6869,
    )

    @Test
    fun `MET forecast parser returns bounded source-backed current and hourly weather`() {
        val points = (0 until 20).joinToString(",") { hour ->
            """
            {
              "time":"2026-09-10T${hour.toString().padStart(2, '0')}:00:00Z",
              "data":{
                "instant":{"details":{
                  "air_temperature":30.5,
                  "relative_humidity":72.0,
                  "wind_speed":2.4,
                  "wind_from_direction":150.0,
                  "cloud_area_fraction":65.0
                }},
                "next_1_hours":{
                  "summary":{"symbol_code":"partlycloudy_day"},
                  "details":{"precipitation_amount":0.3}
                }
              }
            }
            """.trimIndent()
        }
        val raw = """
            {
              "properties":{
                "meta":{"updated_at":"2026-09-10T13:55:00Z"},
                "timeseries":[$points]
              }
            }
        """.trimIndent()

        val result = parseMetNorwayForecast(
            raw = raw,
            location = location,
            sourceUrl = "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=3.1390&lon=101.6869",
            retrievedAt = Instant.parse("2026-09-10T14:00:00Z"),
        )

        assertEquals("MET Norway", result["provider"]!!.jsonPrimitive.content)
        assertEquals("2026-09-10T13:55:00Z", result["provider_updated_at"]!!.jsonPrimitive.content)
        assertEquals("Data from MET Norway", result["attribution"]!!.jsonPrimitive.content)
        assertEquals(12, result["hourly"]!!.jsonArray.size)
        val current = result["current"]!!.jsonObject
        assertEquals("30.5", current["air_temperature_c"]!!.jsonPrimitive.content)
        assertEquals("partlycloudy_day", current["symbol_code"]!!.jsonPrimitive.content)
        assertEquals("0.3", current["precipitation_mm"]!!.jsonPrimitive.content)
        val resolved = result["location"]!!.jsonObject
        assertEquals("Kuala Lumpur, Malaysia", resolved["label"]!!.jsonPrimitive.content)
        assertTrue(result["source_url"]!!.jsonPrimitive.content.startsWith("https://api.met.no/"))
    }

    @Test
    fun `MET forecast parser rejects responses without timeseries`() {
        val failure = runCatching {
            parseMetNorwayForecast(
                raw = """{"properties":{"meta":{},"timeseries":[]}}""",
                location = location,
                sourceUrl = "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=3.1390&lon=101.6869",
                retrievedAt = Instant.parse("2026-09-10T14:00:00Z"),
            )
        }.exceptionOrNull()

        assertTrue(failure is WeatherToolException)
        assertEquals("NO_FORECAST_DATA", (failure as WeatherToolException).code)
    }

    @Test
    fun `weather coordinates are normalized to four decimals for provider caching`() {
        assertEquals("3.1390", formatWeatherCoordinate(3.1390001))
        assertEquals("101.6869", formatWeatherCoordinate(101.68694))
    }
}
