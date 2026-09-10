package com.orchords.orchordsai.data.ai.tools.local

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.orchords.ai.core.InputSchema
import com.orchords.ai.core.Tool
import com.orchords.ai.ui.UIMessagePart
import com.orchords.orchordsai.BuildConfig
import com.orchords.orchordsai.utils.JsonInstant
import java.io.IOException
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

private const val MET_FORECAST_BASE_URL =
    "https://api.met.no/weatherapi/locationforecast/2.0/compact"
private const val MAX_WEATHER_RESPONSE_BYTES = 512L * 1024L
private const val MAX_WEATHER_LOCATION_CHARS = 200
private const val MAX_FORECAST_POINTS = 12
private const val MET_USER_AGENT_PREFIX = "ORCHORDS-AI-Android"

internal data class ResolvedWeatherLocation(
    val label: String,
    val latitude: Double,
    val longitude: Double,
)

internal class WeatherToolException(
    val code: String,
    message: String,
    val httpStatus: Int? = null,
) : IOException(message)

private data class CachedMetForecast(
    val raw: String,
    val expiresAtMillis: Long?,
    val lastModified: String?,
    val fetchedAtMillis: Long,
)

internal fun formatWeatherCoordinate(value: Double): String =
    String.format(Locale.US, "%.4f", value)

internal class MetNorwayWeatherClient(
    private val httpClient: OkHttpClient,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
) {
    private val cache = ConcurrentHashMap<String, CachedMetForecast>()

    suspend fun forecast(location: ResolvedWeatherLocation): JsonObject = withContext(Dispatchers.IO) {
        val latitude = formatWeatherCoordinate(location.latitude)
        val longitude = formatWeatherCoordinate(location.longitude)
        val cacheKey = "$latitude,$longitude"
        val url = MET_FORECAST_BASE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("lat", latitude)
            .addQueryParameter("lon", longitude)
            .build()
        val now = clockMillis()
        val cached = cache[cacheKey]
        if (cached?.expiresAtMillis?.let { now < it } == true) {
            return@withContext parseMetNorwayForecast(
                raw = cached.raw,
                location = location,
                sourceUrl = url.toString(),
                retrievedAt = Instant.ofEpochMilli(cached.fetchedAtMillis),
            )
        }

        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "$MET_USER_AGENT_PREFIX/${BuildConfig.VERSION_NAME} https://orchords.com",
            )
            .header("Accept", "application/json")
            .apply {
                cached?.lastModified?.let { header("If-Modified-Since", it) }
            }
            .build()

        return@withContext httpClient.newCall(request).execute().use { response ->
            if (response.code == 304 && cached != null) {
                val refreshed = cached.copy(
                    expiresAtMillis = parseHttpDateMillis(response.header("Expires")),
                    fetchedAtMillis = now,
                )
                cache[cacheKey] = refreshed
                return@use parseMetNorwayForecast(
                    raw = refreshed.raw,
                    location = location,
                    sourceUrl = url.toString(),
                    retrievedAt = Instant.ofEpochMilli(now),
                )
            }

            if (!response.isSuccessful) {
                throw WeatherToolException(
                    code = when (response.code) {
                        429 -> "RATE_LIMITED"
                        403 -> "PROVIDER_REJECTED_CLIENT"
                        else -> "PROVIDER_HTTP_ERROR"
                    },
                    message = "Weather provider request failed with HTTP ${response.code}.",
                    httpStatus = response.code,
                )
            }

            val raw = response.readBoundedWeatherBody()
            val parsed = parseMetNorwayForecast(
                raw = raw,
                location = location,
                sourceUrl = url.toString(),
                retrievedAt = Instant.ofEpochMilli(now),
            )
            cache[cacheKey] = CachedMetForecast(
                raw = raw,
                expiresAtMillis = parseHttpDateMillis(response.header("Expires")),
                lastModified = response.header("Last-Modified"),
                fetchedAtMillis = now,
            )
            parsed
        }
    }
}

internal fun buildWeatherTool(context: Context, httpClient: OkHttpClient): Tool {
    val client = MetNorwayWeatherClient(httpClient)
    return Tool(
        name = "get_weather",
        description = """
            Get current and near-term weather from MET Norway Locationforecast 2.0.
            Provide either a place name in 'location' or both latitude and longitude.
            Never guess the user's location from timezone, locale, IP address, or prior assumptions.
            If the user did not provide a location, ask for one before calling this tool.
            Returns source, provider freshness, attribution, current conditions, and up to 12 forecast points.
        """.trimIndent().replace("\n", " "),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("location", buildJsonObject {
                        put("type", "string")
                        put("description", "Place name, for example 'Kuala Lumpur, Malaysia'.")
                    })
                    put("latitude", buildJsonObject {
                        put("type", "number")
                        put("description", "Latitude in decimal degrees, -90 through 90. Supply together with longitude.")
                    })
                    put("longitude", buildJsonObject {
                        put("type", "number")
                        put("description", "Longitude in decimal degrees, -180 through 180. Supply together with latitude.")
                    })
                }
            )
        },
        execute = { args ->
            try {
                val params = args.jsonObject
                val locationName = params["location"]?.jsonPrimitive?.contentOrNull?.trim()
                val latitudeElement = params["latitude"]
                val longitudeElement = params["longitude"]

                if ((latitudeElement == null) != (longitudeElement == null)) {
                    throw WeatherToolException(
                        "INVALID_COORDINATES",
                        "Latitude and longitude must be supplied together.",
                    )
                }

                val resolved = if (latitudeElement != null && longitudeElement != null) {
                    val latitude = latitudeElement.jsonPrimitive.contentOrNull?.toDoubleOrNull()
                        ?: throw WeatherToolException("INVALID_COORDINATES", "Latitude must be numeric.")
                    val longitude = longitudeElement.jsonPrimitive.contentOrNull?.toDoubleOrNull()
                        ?: throw WeatherToolException("INVALID_COORDINATES", "Longitude must be numeric.")
                    validateWeatherCoordinates(latitude, longitude)
                    ResolvedWeatherLocation(
                        label = locationName?.takeIf { it.isNotBlank() }
                            ?: "${formatWeatherCoordinate(latitude)}, ${formatWeatherCoordinate(longitude)}",
                        latitude = latitude,
                        longitude = longitude,
                    )
                } else {
                    if (locationName.isNullOrBlank()) {
                        throw WeatherToolException(
                            "LOCATION_REQUIRED",
                            "A place name or latitude/longitude pair is required. Ask the user for the location instead of guessing.",
                        )
                    }
                    resolveNamedWeatherLocation(context, locationName)
                }

                listOf(UIMessagePart.Text(client.forecast(resolved).toString()))
            } catch (error: WeatherToolException) {
                listOf(UIMessagePart.Text(weatherError(error).toString()))
            } catch (_: IOException) {
                listOf(UIMessagePart.Text(weatherError(
                    WeatherToolException(
                        "NETWORK_ERROR",
                        "Weather data could not be reached. Check the network connection and try again.",
                    )
                ).toString()))
            } catch (_: Exception) {
                listOf(UIMessagePart.Text(weatherError(
                    WeatherToolException(
                        "WEATHER_DATA_ERROR",
                        "Weather data could not be processed safely.",
                    )
                ).toString()))
            }
        },
    )
}

private fun validateWeatherCoordinates(latitude: Double, longitude: Double) {
    if (!latitude.isFinite() || latitude !in -90.0..90.0 ||
        !longitude.isFinite() || longitude !in -180.0..180.0
    ) {
        throw WeatherToolException(
            "INVALID_COORDINATES",
            "Latitude must be -90 through 90 and longitude must be -180 through 180.",
        )
    }
}

private suspend fun resolveNamedWeatherLocation(
    context: Context,
    rawLocation: String,
): ResolvedWeatherLocation {
    val query = rawLocation.trim()
    if (query.isEmpty() || query.length > MAX_WEATHER_LOCATION_CHARS) {
        throw WeatherToolException("INVALID_LOCATION", "The weather location is empty or too long.")
    }
    if (!Geocoder.isPresent()) {
        throw WeatherToolException(
            "LOCATION_LOOKUP_UNAVAILABLE",
            "This device has no geocoding service. Supply latitude and longitude instead.",
        )
    }

    val geocoder = Geocoder(context, Locale.getDefault())
    val addresses: List<Address> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        suspendCancellableCoroutine { continuation ->
            geocoder.getFromLocationName(query, 1, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    if (continuation.isActive) continuation.resume(addresses)
                }

                override fun onError(errorMessage: String?) {
                    if (continuation.isActive) continuation.resume(emptyList())
                }
            })
        }
    } else {
        withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            geocoder.getFromLocationName(query, 1).orEmpty()
        }
    }

    val address = addresses.firstOrNull { it.hasLatitude() && it.hasLongitude() }
        ?: throw WeatherToolException(
            "LOCATION_NOT_FOUND",
            "No weather location matched '$query'. Ask the user to provide a more specific place or coordinates.",
        )
    validateWeatherCoordinates(address.latitude, address.longitude)
    val label = listOfNotNull(
        address.locality,
        address.adminArea,
        address.countryName,
    ).map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(", ")
        .ifBlank { query }

    return ResolvedWeatherLocation(
        label = label,
        latitude = address.latitude,
        longitude = address.longitude,
    )
}

internal fun parseMetNorwayForecast(
    raw: String,
    location: ResolvedWeatherLocation,
    sourceUrl: String,
    retrievedAt: Instant,
): JsonObject {
    val root = try {
        JsonInstant.parseToJsonElement(raw).jsonObject
    } catch (_: Exception) {
        throw WeatherToolException(
            "INVALID_PROVIDER_RESPONSE",
            "Weather provider returned malformed JSON.",
        )
    }
    val properties = root["properties"]?.jsonObject
        ?: throw WeatherToolException(
            "INVALID_PROVIDER_RESPONSE",
            "Weather provider response is missing properties.",
        )
    val timeseries = properties["timeseries"]?.jsonArray
        ?: throw WeatherToolException(
            "INVALID_PROVIDER_RESPONSE",
            "Weather provider response is missing timeseries data.",
        )
    if (timeseries.isEmpty()) {
        throw WeatherToolException("NO_FORECAST_DATA", "No forecast data is available for this location.")
    }

    val hourly = buildJsonArray {
        timeseries.take(MAX_FORECAST_POINTS).forEach { element ->
            weatherPoint(element.jsonObject)?.let { add(it) }
        }
    }
    if (hourly.isEmpty()) {
        throw WeatherToolException("NO_FORECAST_DATA", "No usable forecast points are available for this location.")
    }
    val meta = properties["meta"]?.jsonObject

    return buildJsonObject {
        put("location", buildJsonObject {
            put("label", location.label)
            put("latitude", location.latitude)
            put("longitude", location.longitude)
        })
        put("provider", "MET Norway")
        put("product", "Locationforecast 2.0 compact")
        meta?.get("updated_at")?.jsonPrimitive?.contentOrNull?.let {
            put("provider_updated_at", it)
        }
        put("retrieved_at", retrievedAt.toString())
        put("source_url", sourceUrl)
        put("attribution", "Data from MET Norway")
        put("license", "CC BY 4.0 / NLOD 2.0")
        put("current", hourly.first().jsonObject)
        put("hourly", hourly)
    }
}

private fun weatherPoint(point: JsonObject): JsonObject? {
    val time = point["time"]?.jsonPrimitive?.contentOrNull ?: return null
    val data = point["data"]?.jsonObject ?: return null
    val instant = data["instant"]?.jsonObject?.get("details")?.jsonObject ?: return null
    val periodName = when {
        data["next_1_hours"] != null -> "next_1_hours"
        data["next_6_hours"] != null -> "next_6_hours"
        data["next_12_hours"] != null -> "next_12_hours"
        else -> null
    }
    val period = periodName?.let { data[it]?.jsonObject }
    val summary = period?.get("summary")?.jsonObject
    val periodDetails = period?.get("details")?.jsonObject

    return buildJsonObject {
        put("time_utc", time)
        instant.number("air_temperature")?.let { put("air_temperature_c", it) }
        instant.number("relative_humidity")?.let { put("relative_humidity_percent", it) }
        instant.number("wind_speed")?.let { put("wind_speed_mps", it) }
        instant.number("wind_from_direction")?.let { put("wind_from_direction_degrees", it) }
        instant.number("cloud_area_fraction")?.let { put("cloud_area_fraction_percent", it) }
        summary?.get("symbol_code")?.jsonPrimitive?.contentOrNull?.let { put("symbol_code", it) }
        periodDetails?.number("precipitation_amount")?.let { put("precipitation_mm", it) }
        periodName?.let { put("summary_period", it) }
    }
}

private fun JsonObject.number(name: String): Double? =
    this[name]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()

private fun weatherError(error: WeatherToolException): JsonObject = buildJsonObject {
    put("error", error.code)
    put("message", error.message ?: "Weather request failed.")
    error.httpStatus?.let { put("http_status", it) }
    put("provider", "MET Norway")
}

private fun Response.readBoundedWeatherBody(): String {
    val body = body ?: throw WeatherToolException(
        "EMPTY_PROVIDER_RESPONSE",
        "Weather provider returned no response body.",
    )
    val declaredLength = body.contentLength()
    if (declaredLength > MAX_WEATHER_RESPONSE_BYTES) {
        throw WeatherToolException(
            "PROVIDER_RESPONSE_TOO_LARGE",
            "Weather provider response exceeded the allowed size.",
        )
    }
    val source = body.source()
    if (source.request(MAX_WEATHER_RESPONSE_BYTES + 1L)) {
        throw WeatherToolException(
            "PROVIDER_RESPONSE_TOO_LARGE",
            "Weather provider response exceeded the allowed size.",
        )
    }
    return source.readUtf8()
}

private fun parseHttpDateMillis(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return runCatching {
        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
            .toInstant()
            .toEpochMilli()
    }.getOrNull()
}
