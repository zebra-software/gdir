package com.zebrasoftware.gdir.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.zebrasoftware.gdir.data.DirectionsState
import com.zebrasoftware.gdir.data.GdirSettings
import com.zebrasoftware.gdir.data.PlatformLookup
import com.zebrasoftware.gdir.data.TransitDeparture
import com.zebrasoftware.gdir.data.TransitMode
import com.zebrasoftware.gdir.data.TravelMode
import com.zebrasoftware.gdir.data.buildDirectionsUrl
import com.zebrasoftware.gdir.data.countries
import com.zebrasoftware.gdir.data.fetchDirections
import com.zebrasoftware.gdir.data.parseDirectionsText
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private enum class Screen { Search, Results }

/** How long to wait for platforms before showing the results without them. */
private const val PLATFORM_WAIT_MS = 4_000L

/**
 * App root. Holds the search form state and switches between the search and
 * results screens. Settings open as a bottom sheet on top.
 */
@Composable
internal fun GdirApp(settings: GdirSettings) {
    var origin by rememberSaveable { mutableStateOf("") }
    var destination by rememberSaveable { mutableStateOf("") }
    var travelMode by rememberSaveable { mutableStateOf(TravelMode.Transit) }
    var transitModes by rememberSaveable { mutableStateOf(setOf(TransitMode.All)) }

    var screen by remember { mutableStateOf(Screen.Search) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var formState by remember { mutableStateOf<DirectionsState>(DirectionsState.Idle) }
    var result by remember { mutableStateOf<DirectionsState.Success?>(null) }

    val scope = rememberCoroutineScope()
    val country = countries.firstOrNull { it.code == settings.countryCode }

    /**
     * Looks up platforms for the transit steps in [text]. Waits a short while so
     * the results usually appear complete; slower answers are added when they arrive.
     */
    suspend fun loadPlatforms(
        text: String,
        mode: TravelMode,
        requestUrl: String
    ): Map<TransitDeparture, String> {
        val departures = parseDirectionsText(text, mode)
            .flatMap { it.instructions }
            .mapNotNull { it.departure }

        if (departures.isEmpty()) {
            return emptyMap()
        }

        val lookup = scope.async { PlatformLookup.lookup(departures) }

        return withTimeoutOrNull(PLATFORM_WAIT_MS) { lookup.await() } ?: run {
            scope.launch {
                val found = lookup.await()
                val current = result

                if (found.isNotEmpty() && current?.requestUrl == requestUrl) {
                    result = current.copy(platforms = current.platforms + found)
                }
            }

            emptyMap()
        }
    }

    fun search() {
        val cleanOrigin = origin.trim()
        val cleanDestination = destination.trim()

        if (cleanOrigin.isEmpty() || cleanDestination.isEmpty()) {
            formState = DirectionsState.Error("Add both a start and a destination.")
            return
        }

        val mode = travelMode
        val chosenTransitModes = if (mode == TravelMode.Transit) transitModes else emptySet()
        val requestUrl = buildDirectionsUrl(
            origin = cleanOrigin,
            destination = cleanDestination,
            country = country,
            mode = mode,
            transitModes = transitModes.toList()
        )

        formState = DirectionsState.Loading

        scope.launch {
            try {
                val text = fetchDirections(requestUrl)
                val platforms = loadPlatforms(text, mode, requestUrl)

                result = DirectionsState.Success(
                    text = text,
                    travelMode = mode,
                    requestUrl = requestUrl,
                    origin = cleanOrigin,
                    destination = cleanDestination,
                    platforms = platforms,
                    transitModes = chosenTransitModes
                )
                formState = DirectionsState.Idle
                screen = Screen.Results
            } catch (error: Exception) {
                formState = DirectionsState.Error(
                    error.message ?: "Could not load directions."
                )
            }
        }
    }

    fun refresh() {
        val current = result ?: return

        if (current.isRefreshing) {
            return
        }

        val refreshing = current.copy(isRefreshing = true, refreshError = null)
        result = refreshing

        scope.launch {
            val updated = try {
                val text = fetchDirections(refreshing.requestUrl)
                val platforms = loadPlatforms(text, refreshing.travelMode, refreshing.requestUrl)

                refreshing.copy(
                    text = text,
                    platforms = refreshing.platforms + platforms,
                    isRefreshing = false
                )
            } catch (error: Exception) {
                refreshing.copy(
                    isRefreshing = false,
                    refreshError = error.message ?: "Could not refresh directions."
                )
            }

            if (result?.requestUrl == updated.requestUrl) {
                result = updated
            }
        }
    }

    if (showSettings) {
        SettingsSheet(
            settings = settings,
            onDismiss = { showSettings = false }
        )
    }

    BackHandler(enabled = screen != Screen.Search) {
        screen = Screen.Search
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            val forward = targetState != Screen.Search

            (slideInHorizontally { width -> if (forward) width / 4 else -width / 4 } + fadeIn())
                .togetherWith(
                    slideOutHorizontally { width -> if (forward) -width / 4 else width / 4 } + fadeOut()
                )
        },
        label = "screen"
    ) { shownScreen ->
        val shownResult = result

        when {
            shownScreen == Screen.Results && shownResult != null -> ResultsScreen(
                result = shownResult,
                onBack = { screen = Screen.Search },
                onRefresh = { refresh() }
            )

            else -> SearchScreen(
                origin = origin,
                onOriginChange = { origin = it },
                destination = destination,
                onDestinationChange = { destination = it },
                onSwap = {
                    val oldOrigin = origin
                    origin = destination
                    destination = oldOrigin
                },
                travelMode = travelMode,
                onTravelModeChange = { travelMode = it },
                transitModes = transitModes,
                onTransitModesChange = { transitModes = it },
                formState = formState,
                onSearch = { search() },
                onOpenSettings = { showSettings = true }
            )
        }
    }
}
