package com.zebrasoftware.gdir.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Looks up departure platforms (tracks / quays) for public transport in Norway.
 *
 * gdir.telae.net only says which line to take, not where it leaves from. In
 * Norway that information is available from Entur's open APIs (NLOD licence):
 * the stop is found with the Geocoder, and its upcoming departures are matched
 * by line and departure time using the Journey Planner.
 *
 * Departures that can't be matched are simply left out.
 */
internal object PlatformLookup {
    private const val CLIENT_NAME = "zebrasoftware-gdir"
    private const val GEOCODER_URL = "https://api.entur.io/geocoder/v3/autocomplete"
    private const val JOURNEY_PLANNER_URL = "https://api.entur.io/journey-planner/v3/graphql"

    private val norway = ZoneId.of("Europe/Oslo")
    private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    private val stopIdCache = ConcurrentHashMap<String, List<String>>()

    private val departuresQuery = """
        query(${'$'}ids: [String], ${'$'}start: DateTime) {
          stopPlaces(ids: ${'$'}ids) {
            estimatedCalls(startTime: ${'$'}start, timeRange: 21600, numberOfDepartures: 300) {
              aimedDepartureTime
              expectedDepartureTime
              quay { publicCode }
              serviceJourney { line { publicCode } }
            }
          }
        }
    """.trimIndent()

    private data class Call(
        val line: String,
        val times: Set<String>,
        val platform: String
    )

    /** Returns the platform code for each departure that could be matched. */
    suspend fun lookup(
        departures: Collection<TransitDeparture>
    ): Map<TransitDeparture, String> = withContext(Dispatchers.IO) {
        coroutineScope {
            departures
                .distinct()
                .groupBy { it.stop }
                .map { (stop, stopDepartures) ->
                    async {
                        try {
                            lookupAtStop(stop, stopDepartures)
                        } catch (error: Exception) {
                            emptyMap()
                        }
                    }
                }
                .awaitAll()
                .fold(emptyMap()) { all, found -> all + found }
        }
    }

    private fun lookupAtStop(
        stop: String,
        departures: List<TransitDeparture>
    ): Map<TransitDeparture, String> {
        val stopIds = stopIdCache.getOrPut(stop.lowercase(Locale.ROOT)) {
            findStopPlaceIds(stop)
        }

        if (stopIds.isEmpty()) {
            return emptyMap()
        }

        val calls = fetchCalls(stopIds)
        val platforms = mutableMapOf<TransitDeparture, String>()

        for (departure in departures) {
            val line = departure.line.normalizeLine()
            val time = departure.time.normalizeTime()

            val match = calls.firstOrNull { it.line == line && time in it.times }
                ?: continue

            platforms[departure] = match.platform
        }

        return platforms
    }

    private fun findStopPlaceIds(stop: String): List<String> {
        val url = "$GEOCODER_URL?q=${URLEncoder.encode(stop, "UTF-8")}&layers=stopPlace&limit=5"
        val features = JSONObject(request(url)).optJSONArray("features") ?: return emptyList()

        return (0 until features.length())
            .mapNotNull { index ->
                features.optJSONObject(index)
                    ?.optJSONObject("properties")
                    ?.stringOrNull("id")
            }
            .filter { it.startsWith("NSR:StopPlace:") }
            .distinct()
    }

    private fun fetchCalls(stopIds: List<String>): List<Call> {
        val start = ZonedDateTime.now(norway)
            .minusMinutes(30)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        val body = JSONObject()
            .put("query", departuresQuery)
            .put(
                "variables",
                JSONObject()
                    .put("ids", JSONArray(stopIds))
                    .put("start", start)
            )
            .toString()

        val stopPlaces = JSONObject(request(JOURNEY_PLANNER_URL, body))
            .optJSONObject("data")
            ?.optJSONArray("stopPlaces")
            ?: return emptyList()

        val calls = mutableListOf<Call>()

        for (placeIndex in 0 until stopPlaces.length()) {
            val estimatedCalls = stopPlaces.optJSONObject(placeIndex)
                ?.optJSONArray("estimatedCalls")
                ?: continue

            for (callIndex in 0 until estimatedCalls.length()) {
                val call = estimatedCalls.optJSONObject(callIndex) ?: continue

                val platform = call.optJSONObject("quay")?.stringOrNull("publicCode") ?: continue
                val line = call.optJSONObject("serviceJourney")
                    ?.optJSONObject("line")
                    ?.stringOrNull("publicCode")
                    ?: continue

                val times = listOfNotNull(
                    call.stringOrNull("aimedDepartureTime"),
                    call.stringOrNull("expectedDepartureTime")
                ).mapNotNull { it.toNorwegianClockTime() }.toSet()

                if (times.isNotEmpty()) {
                    calls.add(Call(line = line.normalizeLine(), times = times, platform = platform))
                }
            }
        }

        return calls
    }

    private fun request(url: String, postBody: String? = null): String {
        val connection = URL(url).openConnection() as HttpURLConnection

        connection.connectTimeout = 5_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("ET-Client-Name", CLIENT_NAME)
        connection.setRequestProperty("Accept", "application/json")

        try {
            if (postBody != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(postBody.toByteArray(Charsets.UTF_8)) }
            }

            if (connection.responseCode !in 200..299) {
                error("Entur returned HTTP ${connection.responseCode}.")
            }

            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun String.toNorwegianClockTime(): String? = try {
        OffsetDateTime.parse(this).atZoneSameInstant(norway).format(timeFormat)
    } catch (error: Exception) {
        null
    }

    private fun String.normalizeTime(): String =
        trim().replace(Regex("\\s+"), " ").uppercase(Locale.US)

    private fun String.normalizeLine(): String =
        replace(Regex("\\s+"), "").uppercase(Locale.ROOT)

    private fun JSONObject.stringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}
