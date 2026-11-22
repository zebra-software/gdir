package com.zebrasoftware.gdir.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zebrasoftware.gdir.data.DirectionsState
import com.zebrasoftware.gdir.data.ParsedRoute
import com.zebrasoftware.gdir.data.TransitDeparture
import com.zebrasoftware.gdir.data.RouteInstruction
import com.zebrasoftware.gdir.data.RouteSummary
import com.zebrasoftware.gdir.data.isTransitType
import com.zebrasoftware.gdir.data.parseDirectionsText
import com.zebrasoftware.gdir.data.prettyPlace
import com.zebrasoftware.gdir.data.routeDurationLabel
import com.zebrasoftware.gdir.data.usesOnly

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ResultsScreen(
    result: DirectionsState.Success,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    val allRoutes = remember(result.text, result.travelMode) {
        parseDirectionsText(
            text = result.text,
            travelMode = result.travelMode
        )
    }

    // Google may return routes using other transport than the chosen types,
    // so filter them here. If nothing is left, show everything with a note.
    val matchingRoutes = remember(allRoutes, result.transitModes) {
        allRoutes.filter { it.usesOnly(result.transitModes) }
    }
    val noMatchingRoutes = matchingRoutes.isEmpty() && allRoutes.isNotEmpty()
    val routes = if (noMatchingRoutes) allRoutes else matchingRoutes

    val platforms = result.platforms

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "${result.origin.prettyPlace()} → ${result.destination.prettyPlace()}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = result.travelMode.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onRefresh,
                        enabled = !result.isRefreshing
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = result.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                result.refreshError?.let { message ->
                    item(key = "refresh_error") {
                        ErrorBanner(message = message)
                    }
                }

                if (noMatchingRoutes) {
                    item(key = "no_matching_routes") {
                        InfoBanner(
                            message = "No routes using only " +
                                result.transitModes.joinToString(" or ") { it.label.lowercase().removePrefix("any ") } +
                                " were found. Showing other options."
                        )
                    }
                }

                if (routes.isEmpty()) {
                    item(key = "raw") {
                        RawDirectionsCard(text = result.text)
                    }
                } else {
                    itemsIndexed(routes) { _, route ->
                        RouteCard(
                            route = route,
                            collapsible = routes.size > 1,
                            platforms = platforms
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteCard(
    route: ParsedRoute,
    collapsible: Boolean,
    platforms: Map<TransitDeparture, String>
) {
    val mainTransitIndex = route.instructions.indexOfFirst { it.type.isTransitType() }
    val mainTransitStep = route.instructions.getOrNull(mainTransitIndex)

    val steps = if (mainTransitIndex >= 0) {
        route.instructions.filterIndexed { index, _ -> index != mainTransitIndex }
    } else {
        route.instructions
    }

    // Only show the expand control when there are steps to reveal.
    val expandable = collapsible && steps.isNotEmpty()

    var expanded by rememberSaveable(route.summary.title, route.summary.from, expandable) {
        mutableStateOf(!expandable)
    }

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "chevron"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            RouteHeader(
                summary = route.summary,
                mainTransitStep = mainTransitStep,
                mainPlatform = mainTransitStep?.departure?.let { platforms[it] },
                expandable = expandable,
                expanded = expanded,
                chevronRotation = chevronRotation,
                modifier = if (expandable) {
                    Modifier.clickable(
                        onClickLabel = if (expanded) "Hide steps" else "Show steps",
                        onClick = { expanded = !expanded }
                    )
                } else {
                    Modifier
                }
            )

            if (expanded && steps.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    steps.forEach { step ->
                        StepRow(
                            step = step,
                            platform = step.departure?.let { platforms[it] }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RouteHeader(
    summary: RouteSummary,
    mainTransitStep: RouteInstruction?,
    mainPlatform: String?,
    expandable: Boolean,
    expanded: Boolean,
    chevronRotation: Float,
    modifier: Modifier = Modifier
) {
    val duration = summary.title.routeDurationLabel()

    val detailText = mainTransitStep
        ?.details
        ?.drop(1)
        ?.joinToString(" · ")
        ?.takeIf { it.isNotBlank() }
        ?: "${summary.from.prettyPlace()} → ${summary.to.prettyPlace()}"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = summary.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = detailText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (duration.isNotBlank()) {
                Text(
                    text = duration,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (expandable) {
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.rotate(chevronRotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RoutePill(
                text = summary.modeLabel,
                icon = stepIcon(summary.modeLabel.substringBefore(" "))
            )

            if (mainTransitStep != null && mainTransitStep.title.isNotBlank()) {
                RoutePill(
                    text = mainTransitStep.title,
                    icon = stepIcon(mainTransitStep.type)
                )
            }

            if (mainPlatform != null) {
                RoutePill(
                    text = "Platform $mainPlatform",
                    icon = Icons.Filled.Place
                )
            }

            if (summary.distance.isNotBlank()) {
                RoutePill(
                    text = summary.distance,
                    icon = Icons.Filled.Straighten
                )
            }
        }
    }
}

@Composable
private fun RoutePill(
    text: String,
    icon: ImageVector
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )

            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun StepRow(
    step: RouteInstruction,
    platform: String?
) {
    val isTransit = step.type.isTransitType()
    val details = if (platform != null) step.details + "Platform $platform" else step.details

    ListItem(
        headlineContent = {
            Text(text = step.title)
        },
        supportingContent = if (details.isNotEmpty()) {
            { Text(text = details.joinToString(" · ")) }
        } else {
            null
        },
        leadingContent = {
            Surface(
                shape = CircleShape,
                color = if (isTransit) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                contentColor = if (isTransit) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            ) {
                Icon(
                    imageVector = stepIcon(step.type),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp)
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun RawDirectionsCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        SelectionContainer {
            Text(
                text = text,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
