package com.zebrasoftware.gdir.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

private const val BASE_URL = "https://gdir.telae.net/gdir/"
internal const val SOURCE_URL = "https://gdir.telae.net/"

private const val TIME_RANGE_REGEX =
    "\\d{1,2}:\\d{2}\\s*(?:AM|PM)\\s*[-–]\\s*\\d{1,2}:\\d{2}\\s*(?:AM|PM)"

private const val DURATION_TEXT_REGEX =
    "(?:(?:\\d+\\s*(?:hours?|hrs?|hr|h)\\s*)?(?:\\d+\\s*(?:mins?|minutes?|min))|(?:\\d+\\s*(?:hours?|hrs?|hr|h)))"

private val routeSummaryRegex = Regex(
    "^($TIME_RANGE_REGEX)\\s*(?:\\(([^)]*)\\))?\\s*(.+?)\\s*(?:->|→)\\s*(.+?)\\s+(\\d+(?:\\.\\d+)?\\s*(?:km|m|mi))$",
    RegexOption.IGNORE_CASE
)

private val routeOverviewRegex = Regex(
    "^($DURATION_TEXT_REGEX)\\s+(.+?)\\s*(?:->|→)\\s*(.+?)\\s+(\\d+(?:\\.\\d+)?\\s*(?:km|m|mi))$",
    RegexOption.IGNORE_CASE
)

private val timeRangeAtStartRegex = Regex(
    "^($TIME_RANGE_REGEX)\\s*(.+)$",
    RegexOption.IGNORE_CASE
)

private val durationAtStartRegex = Regex(
    "^($DURATION_TEXT_REGEX)\\s+(.+)$",
    RegexOption.IGNORE_CASE
)

private val walkInstructionRegex = Regex(
    "^(\\d+\\s*(?:mins?|minutes?|min))\\s+walk\\s+to\\s+(.+?)(?:\\s+(\\d+(?:\\.\\d+)?\\s*(?:km|m|mi)))?$",
    RegexOption.IGNORE_CASE
)

private val distanceAtEndRegex = Regex(
    "(\\d+(?:\\.\\d+)?\\s*(?:km|m|mi))$",
    RegexOption.IGNORE_CASE
)

internal data class Country(
    val name: String,
    val code: String
)

internal enum class TravelMode(
    val label: String,
    val requestValue: String
) {
    Transit("Transit", "transit"),
    Driving("Drive", "driving"),
    Bicycling("Bike", "bicycling"),
    Walking("Walk", "walking")
}

internal enum class TransitMode(
    val label: String,
    val requestKey: String?
) {
    All("All", null),
    Rail("Any rail", "rail"),
    Train("Train", "train"),
    Tram("Tram", "tram"),
    Subway("Subway", "subway"),
    Bus("Bus", "bus")
}

internal sealed interface DirectionsState {
    data object Idle : DirectionsState
    data object Loading : DirectionsState

    data class Success(
        val text: String,
        val travelMode: TravelMode,
        val requestUrl: String,
        val origin: String,
        val destination: String,
        val platforms: Map<TransitDeparture, String> = emptyMap(),
        val transitModes: Set<TransitMode> = emptySet(),
        val isRefreshing: Boolean = false,
        val refreshError: String? = null
    ) : DirectionsState

    data class Error(val message: String) : DirectionsState
}

internal data class RouteSummary(
    val title: String,
    val startKey: String,
    val modeLabel: String,
    val from: String,
    val to: String,
    val distance: String
)

internal data class ParsedRoute(
    val summary: RouteSummary,
    val instructions: List<RouteInstruction>
)

internal data class RouteInstruction(
    val type: String,
    val title: String,
    val details: List<String> = emptyList(),
    /** For transit steps: info used to look up the departure platform. */
    val departure: TransitDeparture? = null
)

/** A transit departure: stop name, line (e.g. "R12") and start time (e.g. "9:17 PM"). */
internal data class TransitDeparture(
    val stop: String,
    val line: String,
    val time: String
)

private data class SummaryEntry(
    val index: Int,
    val summary: RouteSummary
)

internal val countries = buildCountryList()

private fun buildCountryList(): List<Country> {
    return Locale.getISOCountries()
        .map { countryCode ->
            Country(
                name = Locale("", countryCode).displayCountry,
                code = countryCode.lowercase(Locale.US)
            )
        }
        .filter { it.name.isNotBlank() }
        .distinctBy { it.code }
        .sortedBy { it.name }
}

internal fun buildDirectionsUrl(
    origin: String,
    destination: String,
    country: Country?,
    mode: TravelMode,
    transitModes: List<TransitMode>
): String {
    val params = mutableListOf<String>()

    if (country != null && country.code.isNotBlank()) {
        params.add("country=${country.code.urlEncode()}")
    }

    params.add("origin=${origin.urlEncode()}")
    params.add("destination=${destination.urlEncode()}")
    params.add("mode_of_travel=${mode.requestValue.urlEncode()}")
    params.add("multiple_routes=on")

    if (mode == TravelMode.Transit) {
        transitModes.requestModes().forEach { transitMode ->
            params.add("${transitMode.requestKey}=on")
        }

        params.add("walking_substeps=on")
    }

    return "$BASE_URL?${params.joinToString("&")}"
}

internal suspend fun fetchDirections(requestUrl: String): String = withContext(Dispatchers.IO) {
    val connection = URL(requestUrl).openConnection() as HttpURLConnection

    connection.requestMethod = "GET"
    connection.connectTimeout = 15_000
    connection.readTimeout = 20_000
    connection.setRequestProperty("User-Agent", "GdirAndroid/1.0")

    try {
        val status = connection.responseCode
        val stream = if (status in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }

        val html = stream.bufferedReader().use { it.readText() }

        if (status !in 200..299) {
            error("Server returned HTTP $status.")
        }

        val readable = html.toReadableText()

        if (readable.isBlank()) {
            error("The server returned an empty response.")
        }

        readable
    } finally {
        connection.disconnect()
    }
}

private fun String.toReadableText(): String {
    val lines = this
        .replace(Regex("(?is)<script.*?</script>"), "")
        .replace(Regex("(?is)<style.*?</style>"), "")
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</(h1|h2|h3|p|li|div|ol|ul|tr)>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .decodeBasicHtmlEntities()
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .filterNot { it.equals("Results", ignoreCase = true) }
        .filterNot { it.equals("Directions", ignoreCase = true) }

    return stripFooterLines(lines).joinToString("\n")
}

internal fun parseDirectionsText(
    text: String,
    travelMode: TravelMode
): List<ParsedRoute> {
    val lines = stripFooterLines(
        text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.equals("Results", ignoreCase = true) }
            .filterNot { it.equals("Directions", ignoreCase = true) }
    )

    if (lines.isEmpty()) {
        return emptyList()
    }

    val summaryEntries = lines.mapIndexedNotNull { index, line ->
        val summary = parseRouteSummary(
            line = line,
            travelMode = travelMode
        )

        if (summary == null) {
            null
        } else {
            SummaryEntry(index, summary)
        }
    }

    if (summaryEntries.isEmpty()) {
        val inferredOverview = inferOverviewFromFirstDetailLine(
            lines = lines,
            travelMode = travelMode
        )

        if (inferredOverview != null) {
            return listOf(
                ParsedRoute(
                    summary = inferredOverview.first,
                    instructions = inferredOverview.second.mapNotNull { parseInstruction(it) }
                )
            )
        }

        return listOf(
            ParsedRoute(
                summary = RouteSummary(
                    title = "Suggested route",
                    startKey = "",
                    modeLabel = travelMode.resultLabel(),
                    from = "Start",
                    to = "Destination",
                    distance = ""
                ),
                instructions = lines.mapNotNull { parseInstruction(it) }
            )
        )
    }

    val firstDetailIndex = lines.indexOfFirst { line ->
        parseRouteSummary(line, travelMode) == null
    }

    val summariesAreTopBlock = firstDetailIndex > 0 &&
            summaryEntries.all { it.index < firstDetailIndex }

    return if (summariesAreTopBlock) {
        parseTopSummaryBlockLayout(
            lines = lines,
            summaryEntries = summaryEntries,
            travelMode = travelMode
        )
    } else {
        parseInterleavedRouteLayout(
            lines = lines,
            travelMode = travelMode
        )
    }
}

private fun parseTopSummaryBlockLayout(
    lines: List<String>,
    summaryEntries: List<SummaryEntry>,
    travelMode: TravelMode
): List<ParsedRoute> {
    val summaries = summaryEntries.map { it.summary }

    val detailLines = lines.filter { line ->
        parseRouteSummary(line, travelMode) == null
    }

    val chunks = splitDetailLinesIntoRouteChunks(
        detailLines = detailLines,
        summaries = summaries
    )

    return summaries.mapIndexed { index, summary ->
        ParsedRoute(
            summary = summary,
            instructions = chunks
                .getOrNull(index)
                .orEmpty()
                .mapNotNull { parseInstruction(it) }
        )
    }
}

private fun parseInterleavedRouteLayout(
    lines: List<String>,
    travelMode: TravelMode
): List<ParsedRoute> {
    val routes = mutableListOf<Pair<RouteSummary, MutableList<String>>>()
    var currentRouteIndex = -1
    val preludeLines = mutableListOf<String>()

    lines.forEach { line ->
        val summary = parseRouteSummary(line, travelMode)

        if (summary != null) {
            routes.add(summary to mutableListOf())
            currentRouteIndex = routes.lastIndex
        } else {
            if (currentRouteIndex >= 0) {
                routes[currentRouteIndex].second.add(line)
            } else {
                preludeLines.add(line)
            }
        }
    }

    if (routes.isEmpty()) {
        return emptyList()
    }

    if (preludeLines.isNotEmpty()) {
        routes.first().second.addAll(0, preludeLines)
    }

    return routes.map { route ->
        ParsedRoute(
            summary = route.first,
            instructions = route.second.mapNotNull { parseInstruction(it) }
        )
    }
}

internal fun parseRouteSummary(
    line: String,
    travelMode: TravelMode
): RouteSummary? {
    val cleaned = line.cleanDirectionLine()

    parseTransitRouteSummary(cleaned)?.let { return it }
    parseOverviewRouteSummary(cleaned, travelMode)?.let { return it }

    return null
}

internal fun parseTransitRouteSummary(line: String): RouteSummary? {
    val match = routeSummaryRegex.find(line) ?: return null

    val rawTimeRange = match.groupValues[1]
    val transportCode = match.groups[2]?.value?.trim()?.takeIf { it.isNotEmpty() }
    val from = match.groupValues[3].trim()
    val to = match.groupValues[4].trim()
    val distance = match.groupValues[5].withSpacedDistanceUnit()

    return RouteSummary(
        title = rawTimeRange.toDisplayTimeRange(),
        startKey = rawTimeRange.routeStartTime(),
        modeLabel = transportCode.transportLabel(),
        from = from,
        to = to,
        distance = distance
    )
}

internal fun parseOverviewRouteSummary(
    line: String,
    travelMode: TravelMode
): RouteSummary? {
    val match = routeOverviewRegex.find(line) ?: return null

    val duration = match.groupValues[1].toDisplayDuration()
    val from = match.groupValues[2].trim()
    val to = match.groupValues[3].trim()
    val distance = match.groupValues[4].withSpacedDistanceUnit()

    return RouteSummary(
        title = duration,
        startKey = "",
        modeLabel = travelMode.resultLabel(),
        from = from,
        to = to,
        distance = distance
    )
}

private fun inferOverviewFromFirstDetailLine(
    lines: List<String>,
    travelMode: TravelMode
): Pair<RouteSummary, List<String>>? {
    if (lines.isEmpty()) {
        return null
    }

    val firstLine = lines.first().cleanDirectionLine()
    val overview = parseOverviewRouteSummary(firstLine, travelMode) ?: return null

    return overview to lines.drop(1)
}

private fun splitDetailLinesIntoRouteChunks(
    detailLines: List<String>,
    summaries: List<RouteSummary>
): List<List<String>> {
    if (summaries.isEmpty()) {
        return listOf(detailLines)
    }

    val chunks = MutableList(summaries.size) { mutableListOf<String>() }

    if (detailLines.isEmpty()) {
        return chunks
    }

    if (summaries.size == 1) {
        chunks[0].addAll(detailLines)
        return chunks
    }

    val hasTimeBasedRoutes = summaries.any { it.startKey.isNotBlank() }

    if (!hasTimeBasedRoutes) {
        chunks[0].addAll(detailLines)
        return chunks
    }

    var currentRouteIndex = 0

    detailLines.forEach { rawLine ->
        val cleanedLine = rawLine.cleanDirectionLine()
        val lineStartTime = extractStartTime(cleanedLine)

        val nextRouteIndex = if (lineStartTime != null) {
            summaries
                .withIndex()
                .firstOrNull { indexedSummary ->
                    indexedSummary.index > currentRouteIndex &&
                            indexedSummary.value.startKey.isNotBlank() &&
                            indexedSummary.value.startKey == lineStartTime &&
                            looksLikeRouteStart(cleanedLine)
                }
                ?.index
        } else {
            null
        }

        if (nextRouteIndex != null && chunks[currentRouteIndex].isNotEmpty()) {
            currentRouteIndex = nextRouteIndex
        }

        chunks[currentRouteIndex].add(rawLine)
    }

    return redistributeSwallowedRoutesIfNeeded(
        chunks = chunks,
        summaries = summaries
    )
}

private fun redistributeSwallowedRoutesIfNeeded(
    chunks: List<MutableList<String>>,
    summaries: List<RouteSummary>
): List<List<String>> {
    val hasOnlyFirstRouteDetails = chunks.size > 1 &&
            chunks.first().isNotEmpty() &&
            chunks.drop(1).all { it.isEmpty() }

    if (!hasOnlyFirstRouteDetails) {
        return chunks
    }

    val firstChunk = chunks.first()
    val redistributed = MutableList(summaries.size) { mutableListOf<String>() }
    var currentRouteIndex = 0

    firstChunk.forEach { line ->
        val cleanedLine = line.cleanDirectionLine()
        val startTime = extractStartTime(cleanedLine)

        val matchingRouteIndex = if (startTime != null && looksLikeRouteStart(cleanedLine)) {
            summaries
                .withIndex()
                .firstOrNull { indexedSummary ->
                    indexedSummary.index >= currentRouteIndex &&
                            indexedSummary.value.startKey == startTime &&
                            redistributed[indexedSummary.index].isEmpty()
                }
                ?.index
        } else {
            null
        }

        if (matchingRouteIndex != null) {
            currentRouteIndex = matchingRouteIndex
        }

        redistributed[currentRouteIndex].add(line)
    }

    return redistributed
}

private fun looksLikeRouteStart(line: String): Boolean {
    val cleaned = line.cleanDirectionLine()

    if (parseTransitInstruction(cleaned) != null) {
        return true
    }

    if (routeOverviewRegex.find(cleaned) != null) {
        return true
    }

    return timeRangeAtStartRegex.find(cleaned) != null &&
            (
                    cleaned.contains(" board ", ignoreCase = true) ||
                            cleaned.contains(" depart", ignoreCase = true) ||
                            cleaned.contains(" start", ignoreCase = true)
                    )
}

private fun parseInstruction(line: String): RouteInstruction? {
    val cleaned = line.cleanDirectionLine()

    if (cleaned.isBlank()) {
        return null
    }

    if (isNoiseLine(cleaned)) {
        return null
    }

    parseTransitInstruction(cleaned)?.let { return it }
    parseWalkToInstruction(cleaned)?.let { return it }

    return parsePlainInstruction(cleaned)
}

private fun parseTransitInstruction(line: String): RouteInstruction? {
    val timeMatch = timeRangeAtStartRegex.find(line) ?: return null

    val timeRange = timeMatch.groupValues[1].toDisplayTimeRange()
    val startTime = timeMatch.groupValues[1].routeStartTime()
    val body = timeMatch.groupValues[2].trim()

    val boardIndex = body.indexOf(" board ", ignoreCase = true)
    if (boardIndex < 0) {
        return null
    }

    val from = body.substring(0, boardIndex).trim().prettyPlace()
    val afterBoard = body.substring(boardIndex + " board ".length).trim()

    val towardsIndex = afterBoard.indexOf(" towards ", ignoreCase = true)

    if (towardsIndex < 0) {
        val title = afterBoard.withoutVehicleWord().cleanDirectionLine()

        return RouteInstruction(
            type = "Transit",
            title = title,
            details = listOf(timeRange, "From $from"),
            departure = TransitDeparture(stop = from, line = title, time = startTime)
        )
    }

    val vehicle = afterBoard.substring(0, towardsIndex).trim()
    val afterTowards = afterBoard.substring(towardsIndex + " towards ".length).trim()

    val alightIndex = afterTowards.indexOf(" alight at ", ignoreCase = true)

    val towards: String
    val alightAt: String?

    if (alightIndex >= 0) {
        towards = afterTowards.substring(0, alightIndex).trim().prettyPlace()
        alightAt = afterTowards.substring(alightIndex + " alight at ".length).trim().prettyPlace()
    } else {
        towards = afterTowards.trim().prettyPlace()
        alightAt = null
    }

    val details = mutableListOf<String>()
    details.add(timeRange)

    if (alightAt != null) {
        details.add("$from → $alightAt")
    } else {
        details.add("From $from")
    }

    details.add("Towards $towards")

    val title = vehicle.withoutVehicleWord()

    return RouteInstruction(
        type = vehicle.toVehicleType(),
        title = title,
        details = details,
        departure = TransitDeparture(stop = from, line = title, time = startTime)
    )
}

private fun parseWalkToInstruction(line: String): RouteInstruction? {
    val match = walkInstructionRegex.find(line) ?: return null

    val duration = match.groupValues[1].toDisplayDuration()
    val destination = match.groupValues[2].trim().prettyPlace()
    val distance = match.groups[3]?.value?.withSpacedDistanceUnit()

    val details = mutableListOf<String>()
    details.add(duration)

    if (distance != null) {
        details.add(distance)
    }

    return RouteInstruction(
        type = "Walk",
        title = "Walk to $destination",
        details = details
    )
}

private fun parsePlainInstruction(line: String): RouteInstruction {
    val withoutTimeRange = timeRangeAtStartRegex.find(line)?.let { match ->
        match.groupValues[2].trim()
    } ?: line

    val durationMatch = durationAtStartRegex.find(withoutTimeRange)
    val duration = durationMatch?.groupValues?.getOrNull(1)?.toDisplayDuration()
    val bodyWithoutDuration = durationMatch?.groupValues?.getOrNull(2)?.trim()
        ?: withoutTimeRange

    val distance = distanceAtEndRegex.find(bodyWithoutDuration)?.value?.withSpacedDistanceUnit()

    val title = if (distance != null) {
        bodyWithoutDuration
            .removeSuffix(distance.replace(" ", ""))
            .removeSuffix(distance)
            .trim()
    } else {
        bodyWithoutDuration.trim()
    }

    val normalizedTitle = title
        .normalizeInstructionSpacing()
        .ifBlank { bodyWithoutDuration.normalizeInstructionSpacing() }

    val details = mutableListOf<String>()

    if (duration != null) {
        details.add(duration)
    }

    if (distance != null) {
        details.add(distance)
    }

    return RouteInstruction(
        type = detectInstructionType(normalizedTitle),
        title = normalizedTitle,
        details = details
    )
}

private fun detectInstructionType(title: String): String {
    val lower = title.trim().lowercase()

    return when {
        lower.startsWith("head") -> "Head"
        lower.startsWith("turn") -> "Turn"
        lower.startsWith("continue") -> "Continue"
        lower.startsWith("keep") -> "Keep"
        lower.startsWith("merge") -> "Merge"
        lower.startsWith("take") -> "Take"
        lower.startsWith("exit") -> "Exit"
        lower.startsWith("at the roundabout") -> "Roundabout"
        lower.contains("roundabout") -> "Roundabout"
        lower.startsWith("walk") -> "Walk"
        lower.startsWith("bike") -> "Bike"
        lower.startsWith("drive") -> "Drive"
        lower.contains("turn") -> "Turn"
        lower.contains("continue") -> "Continue"
        lower.contains("keep") -> "Keep"
        lower.contains("merge") -> "Merge"
        lower.contains("take") -> "Take"
        else -> "Continue"
    }
}

private fun stripFooterLines(lines: List<String>): List<String> {
    val cleaned = mutableListOf<String>()

    for (line in lines) {
        if (isFooterStart(line)) {
            break
        }

        if (!isNoiseLine(line)) {
            cleaned.add(line)
        }
    }

    return cleaned
}

private fun isFooterStart(line: String): Boolean {
    return line.contains("Transport and Copyright Notices", ignoreCase = true) ||
            line.contains("Powered by Google", ignoreCase = true)
}

private fun isNoiseLine(line: String): Boolean {
    val lower = line.trim().lowercase()

    return lower.isBlank() ||
            lower.contains("powered by google") ||
            lower.contains("copyright") ||
            lower.contains("walking directions are in beta") ||
            lower.contains("missing sidewalks") ||
            lower == "ruter" ||
            lower.startsWith("ruter ") ||
            lower.startsWith("vy http") ||
            lower.startsWith("vy buss http") ||
            lower.contains("fa-hjelp") ||
            lower.contains("kontakt-oss") ||
            lower.matches(Regex("^https?://.*"))
}

private fun extractStartTime(line: String): String? {
    val match = timeRangeAtStartRegex.find(line.cleanDirectionLine()) ?: return null
    return match.groupValues[1].routeStartTime()
}

private fun TravelMode.resultLabel(): String {
    return when (this) {
        TravelMode.Transit -> "Transit"
        TravelMode.Driving -> "Drive"
        TravelMode.Bicycling -> "Bike"
        TravelMode.Walking -> "Walk"
    }
}

private fun List<TransitMode>.requestModes(): List<TransitMode> {
    return if (contains(TransitMode.All)) {
        TransitMode.entries.filter { it.requestKey != null }
    } else {
        filter { it.requestKey != null }
    }
}

private fun String.routeStartTime(): String {
    return split(Regex("\\s*[-–]\\s*")).firstOrNull()?.trim().orEmpty()
}

private fun String.toDisplayTimeRange(): String {
    return replace(Regex("\\s*[-–]\\s*"), "–")
}

internal fun String.routeDurationLabel(): String {
    val range = TIME_RANGE_REGEX.toRegex(RegexOption.IGNORE_CASE).find(this)?.value ?: return ""

    val parts = range.split(Regex("\\s*[-–]\\s*"))

    if (parts.size != 2) {
        return ""
    }

    val startMinutes = parts[0].toMinutesAfterMidnight() ?: return ""
    var endMinutes = parts[1].toMinutesAfterMidnight() ?: return ""

    if (endMinutes < startMinutes) {
        endMinutes += 24 * 60
    }

    val duration = endMinutes - startMinutes

    return when {
        duration <= 0 -> ""
        duration < 60 -> "$duration min"
        else -> {
            val hours = duration / 60
            val minutes = duration % 60

            if (minutes == 0) {
                "$hours hr"
            } else {
                "$hours hr $minutes min"
            }
        }
    }
}

private fun String.toMinutesAfterMidnight(): Int? {
    val match = Regex(
        "^(\\d{1,2}):(\\d{2})\\s*(AM|PM)$",
        RegexOption.IGNORE_CASE
    ).find(trim()) ?: return null

    var hours = match.groupValues[1].toIntOrNull() ?: return null
    val minutes = match.groupValues[2].toIntOrNull() ?: return null
    val amPm = match.groupValues[3].uppercase(Locale.US)

    if (hours == 12) {
        hours = 0
    }

    if (amPm == "PM") {
        hours += 12
    }

    return hours * 60 + minutes
}

private fun String.toDisplayDuration(): String {
    return this
        .replace(Regex("(\\d+)\\s*(minutes?|mins?|min)", RegexOption.IGNORE_CASE), "$1 min")
        .replace(Regex("(\\d+)\\s*(hours?|hrs?|hr|h)", RegexOption.IGNORE_CASE), "$1 hr")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun String.cleanDirectionLine(): String {
    return this
        .replace(Regex("^[-•]\\s*"), "")
        .replace(Regex("^\\d+[\\).]\\s*"), "")
        .normalizeInstructionSpacing()
        .trim()
}

private fun String.normalizeInstructionSpacing(): String {
    return this
        .replace(Regex("(Rv\\d+)([A-ZÆØÅ])"), "$1. $2")
        .replace(Regex("(\\d)(Continue|Turn|Head|Keep|Merge|Take|At|Exit)", RegexOption.IGNORE_CASE), "$1. $2")
        .replace(Regex("([a-zæøå])([A-ZÆØÅ])"), "$1. $2")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun String.withSpacedDistanceUnit(): String {
    return this
        .replace(Regex("(\\d)(km|m|mi)$", RegexOption.IGNORE_CASE), "$1 $2")
        .trim()
}

private fun String.toVehicleType(): String {
    val lower = this.lowercase()

    return when {
        lower.contains("intercity") -> "Intercity"
        lower.contains("train") -> "Train"
        lower.contains("bus") -> "Bus"
        lower.contains("tram") -> "Tram"
        lower.contains("subway") -> "Subway"
        lower.contains("metro") -> "Metro"
        lower.contains("ferry") -> "Ferry"
        else -> "Transit"
    }
}

internal fun String.isTransitType(): Boolean {
    return lowercase() in setOf(
        "train",
        "rail",
        "intercity",
        "bus",
        "tram",
        "subway",
        "metro",
        "ferry",
        "transit"
    )
}

private fun String.withoutVehicleWord(): String {
    val cleaned = this.normalizeInstructionSpacing()

    return cleaned
        .replace(Regex("\\s+intercity$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+train$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+bus$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+tram$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+subway$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+metro$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+ferry$", RegexOption.IGNORE_CASE), "")
        .trim()
}

private fun String?.transportLabel(): String {
    val code = this?.trim().orEmpty()

    if (code.isBlank()) {
        return "Transit"
    }

    val labels = code.mapNotNull { character ->
        when (character.uppercaseChar()) {
            'T' -> "Train"
            'B' -> "Bus"
            'R' -> "Rail"
            'I' -> "Intercity"
            'S' -> "Subway"
            'U' -> "Subway"
            'M' -> "Metro"
            'F' -> "Ferry"
            'W' -> "Walk"
            'C' -> "Car"
            else -> null
        }
    }.distinct()

    return if (labels.isEmpty()) {
        "Transit"
    } else {
        labels.joinToString(" + ")
    }
}

internal fun String.prettyPlace(): String {
    val cleaned = this.trim()

    if (cleaned.isBlank()) {
        return cleaned
    }

    val letters = cleaned.filter { it.isLetter() }

    if (letters.isEmpty()) {
        return cleaned
    }

    val uppercaseRatio = letters.count { it.isUpperCase() }.toFloat() / letters.length.toFloat()

    if (uppercaseRatio < 0.65f) {
        return cleaned
    }

    return cleaned
        .lowercase()
        .split(" ")
        .joinToString(" ") { word ->
            word
                .split("-")
                .joinToString("-") { part ->
                    if (part.isBlank()) {
                        part
                    } else {
                        part.take(1).uppercase() + part.drop(1)
                    }
                }
        }
}

private fun String.decodeBasicHtmlEntities(): String {
    val withNumericEntities = Regex("&#(\\d+);").replace(this) { match ->
        match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: match.value
    }

    return withNumericEntities
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
}

private fun String.urlEncode(): String {
    return URLEncoder.encode(this, "UTF-8")
}

/**
 * Google only treats the chosen transit types as a preference, so routes can
 * still use other kinds of transport. Returns true if every public transport
 * leg of this route uses one of [modes].
 */
internal fun ParsedRoute.usesOnly(modes: Set<TransitMode>): Boolean {
    if (modes.isEmpty() || TransitMode.All in modes) {
        return true
    }

    val allowed = modes.flatMap { mode ->
        when (mode) {
            TransitMode.All -> emptyList()
            TransitMode.Bus -> listOf("bus")
            TransitMode.Train -> listOf("train", "intercity")
            TransitMode.Tram -> listOf("tram")
            TransitMode.Subway -> listOf("subway", "metro")
            TransitMode.Rail -> listOf("rail", "train", "intercity", "tram", "subway", "metro")
        }
    }.toSet()

    val usedTypes = instructions
        .map { it.type.lowercase(Locale.ROOT) }
        .filter { it.isTransitType() } +
        summary.modeLabel
            .split("+")
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isTransitType() }

    // "transit" means the type couldn't be worked out, so it isn't held against the route.
    return usedTypes
        .filter { it != "transit" }
        .all { it in allowed }
}
