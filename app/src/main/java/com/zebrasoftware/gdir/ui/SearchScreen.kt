package com.zebrasoftware.gdir.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.zebrasoftware.gdir.data.DirectionsState
import com.zebrasoftware.gdir.data.TransitMode
import com.zebrasoftware.gdir.data.TravelMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchScreen(
    origin: String,
    onOriginChange: (String) -> Unit,
    destination: String,
    onDestinationChange: (String) -> Unit,
    onSwap: () -> Unit,
    travelMode: TravelMode,
    onTravelModeChange: (TravelMode) -> Unit,
    transitModes: Set<TransitMode>,
    onTransitModesChange: (Set<TransitMode>) -> Unit,
    formState: DirectionsState,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val focusManager = LocalFocusManager.current

    val isLoading = formState is DirectionsState.Loading

    val submit = {
        focusManager.clearFocus()
        onSearch()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    // Starts where the cards' rounded corners turn straight
                    // (16 dp screen margin + 28 dp corner radius = 44 dp from the edge;
                    // the top app bar already insets its title by 16 dp).
                    Text(
                        text = "Get Directions",
                        modifier = Modifier.padding(start = 28.dp),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = innerPadding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard(title = "Route") {
                RouteFields(
                    origin = origin,
                    onOriginChange = onOriginChange,
                    destination = destination,
                    onDestinationChange = onDestinationChange,
                    onSwap = onSwap,
                    onSubmit = submit
                )
            }

            SectionCard(title = "Travel mode") {
                TravelModeSelector(
                    selectedMode = travelMode,
                    onModeSelected = onTravelModeChange
                )

                AnimatedVisibility(visible = travelMode == TravelMode.Transit) {
                    TransitModeChips(
                        selectedModes = transitModes,
                        onSelectedModesChange = onTransitModesChange
                    )
                }
            }

            Button(
                onClick = submit,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text(
                        text = "Get directions",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }

            if (formState is DirectionsState.Error) {
                ErrorBanner(message = formState.message)
            }
        }
    }
}

@Composable
private fun RouteFields(
    origin: String,
    onOriginChange: (String) -> Unit,
    destination: String,
    onDestinationChange: (String) -> Unit,
    onSwap: () -> Unit,
    onSubmit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlaceField(
                value = origin,
                onValueChange = onOriginChange,
                label = "From",
                icon = Icons.Filled.TripOrigin,
                imeAction = ImeAction.Next,
                onSubmit = onSubmit
            )

            PlaceField(
                value = destination,
                onValueChange = onDestinationChange,
                label = "To",
                icon = Icons.Filled.Place,
                imeAction = ImeAction.Search,
                onSubmit = onSubmit
            )
        }

        FilledTonalIconButton(onClick = onSwap) {
            Icon(
                imageVector = Icons.Filled.SwapVert,
                contentDescription = "Swap start and destination"
            )
        }
    }
}

@Composable
private fun PlaceField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    imeAction: ImeAction,
    onSubmit: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear $label")
                }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        shape = MaterialTheme.shapes.large
    )
}

@Composable
private fun TravelModeSelector(
    selectedMode: TravelMode,
    onModeSelected: (TravelMode) -> Unit
) {
    val modes = TravelMode.entries

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == selectedMode,
                onClick = { onModeSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                colors = gdirSegmentedButtonColors(),
                icon = {},
                label = {
                    Column(
                        modifier = Modifier.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = mode.icon(),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransitModeChips(
    selectedModes: Set<TransitMode>,
    onSelectedModesChange: (Set<TransitMode>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Preferred transit",
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransitMode.entries.forEach { mode ->
                val selected = mode in selectedModes

                FilterChip(
                    selected = selected,
                    onClick = {
                        onSelectedModesChange(
                            when {
                                selected -> (selectedModes - mode).ifEmpty { setOf(TransitMode.All) }
                                mode == TransitMode.All -> setOf(TransitMode.All)
                                else -> selectedModes - TransitMode.All + mode
                            }
                        )
                    },
                    label = { Text(mode.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    leadingIcon = {
                        Icon(
                            imageVector = if (selected) Icons.Filled.Check else mode.icon(),
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                )
            }
        }
    }
}
