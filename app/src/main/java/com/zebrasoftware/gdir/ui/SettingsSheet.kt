package com.zebrasoftware.gdir.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.zebrasoftware.gdir.data.Country
import com.zebrasoftware.gdir.data.GdirSettings
import com.zebrasoftware.gdir.data.SOURCE_URL
import com.zebrasoftware.gdir.data.ThemeMode
import com.zebrasoftware.gdir.data.countries
import kotlinx.coroutines.launch

/** Settings, shown as a bottom sheet so the app stays on one screen (like Dude). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSheet(
    settings: GdirSettings,
    onDismiss: () -> Unit
) {
    var showCountrySheet by rememberSaveable { mutableStateOf(false) }
    val country = countries.firstOrNull { it.code == settings.countryCode }

    if (showCountrySheet) {
        CountrySheet(
            selectedCountry = country,
            onCountrySelected = {
                settings.updateCountryCode(it?.code)
                showCountrySheet = false
            },
            onDismiss = { showCountrySheet = false }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Settings",
                modifier = Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.titleLarge
            )

            SectionCard(title = "Appearance") {
                Text(
                    text = "Theme",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ThemeSelector(
                    selectedMode = settings.themeMode,
                    onModeSelected = settings::updateThemeMode
                )
            }

            SectionCard(title = "Directions") {
                CountryRow(
                    country = country,
                    onClick = { showCountrySheet = true }
                )

                Text(
                    text = "Helps find the right place when names exist in more than one country. " +
                        "Platforms are shown for public transport in Norway, whichever country is chosen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionCard(title = "About") {
                Text(
                    text = "Get Directions, by Zebra Software",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = sourceLinkText(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Directions from gdir.telae.net, powered by Google. " +
                        "Platform information from Entur, under the Norwegian Licence for Open Government Data (NLOD).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** "An Android app for gdir.telae.net", with the site as a link. */
@Composable
private fun sourceLinkText() = buildAnnotatedString {
    append("An Android app for ")
    withLink(
        LinkAnnotation.Url(
            url = SOURCE_URL,
            styles = TextLinkStyles(
                style = SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    textDecoration = TextDecoration.Underline
                )
            )
        )
    ) {
        append("gdir.telae.net")
    }
    append(".")
}

@Composable
private fun ThemeSelector(
    selectedMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit
) {
    val modes = ThemeMode.entries

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == selectedMode,
                onClick = { onModeSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                colors = gdirSegmentedButtonColors(),
                icon = {
                    Icon(
                        imageVector = when (mode) {
                            ThemeMode.System -> Icons.Filled.PhoneAndroid
                            ThemeMode.Light -> Icons.Filled.LightMode
                            ThemeMode.Dark -> Icons.Filled.DarkMode
                        },
                        contentDescription = null,
                        modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                    )
                },
                label = { Text(mode.label, maxLines = 1) }
            )
        }
    }
}

@Composable
private fun CountryRow(
    country: Country?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Choose country", onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Public,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Country",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = country?.name ?: "Any country",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = Icons.Filled.ArrowDropDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountrySheet(
    selectedCountry: Country?,
    onCountrySelected: (Country?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }

    val filteredCountries = remember(query) {
        val cleanQuery = query.trim()

        if (cleanQuery.isEmpty()) {
            countries
        } else {
            countries.filter { it.name.contains(cleanQuery, ignoreCase = true) }
        }
    }

    fun select(country: Country?) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onCountrySelected(country)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Text(
            text = "Country",
            modifier = Modifier.padding(horizontal = 24.dp),
            style = MaterialTheme.typography.titleLarge
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            singleLine = true,
            placeholder = { Text("Search countries") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            shape = MaterialTheme.shapes.extraLarge
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
        ) {
            if (query.isBlank()) {
                item(key = "any") {
                    CountryListRow(
                        name = "Any country",
                        selected = selectedCountry == null,
                        onClick = { select(null) }
                    )
                }
            }

            items(items = filteredCountries, key = { it.code }) { country ->
                CountryListRow(
                    name = country.name,
                    selected = country.code == selectedCountry?.code,
                    onClick = { select(country) }
                )
            }
        }
    }
}

@Composable
private fun CountryListRow(
    name: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
        trailingContent = if (selected) {
            {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            null
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
