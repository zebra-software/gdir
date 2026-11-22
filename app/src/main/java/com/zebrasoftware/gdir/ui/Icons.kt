package com.zebrasoftware.gdir.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsRailway
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import androidx.compose.ui.graphics.vector.ImageVector
import com.zebrasoftware.gdir.data.TransitMode
import com.zebrasoftware.gdir.data.TravelMode

internal fun TravelMode.icon(): ImageVector = when (this) {
    TravelMode.Transit -> Icons.Filled.DirectionsTransit
    TravelMode.Driving -> Icons.Filled.DirectionsCar
    TravelMode.Bicycling -> Icons.AutoMirrored.Filled.DirectionsBike
    TravelMode.Walking -> Icons.AutoMirrored.Filled.DirectionsWalk
}

internal fun TransitMode.icon(): ImageVector = when (this) {
    TransitMode.All -> Icons.Filled.DirectionsTransit
    TransitMode.Rail -> Icons.Filled.DirectionsRailway
    TransitMode.Train -> Icons.Filled.Train
    TransitMode.Tram -> Icons.Filled.Tram
    TransitMode.Subway -> Icons.Filled.Subway
    TransitMode.Bus -> Icons.Filled.DirectionsBus
}

/** Icon for a parsed step type or mode label such as "Bus", "Walk" or "Turn". */
internal fun stepIcon(type: String): ImageVector = when (type.trim().lowercase()) {
    "walk" -> Icons.AutoMirrored.Filled.DirectionsWalk
    "bike" -> Icons.AutoMirrored.Filled.DirectionsBike
    "car", "drive" -> Icons.Filled.DirectionsCar
    "bus" -> Icons.Filled.DirectionsBus
    "train", "intercity" -> Icons.Filled.Train
    "rail" -> Icons.Filled.DirectionsRailway
    "tram" -> Icons.Filled.Tram
    "subway", "metro" -> Icons.Filled.Subway
    "ferry" -> Icons.Filled.DirectionsBoat
    "transit" -> Icons.Filled.DirectionsTransit
    else -> Icons.Filled.Navigation
}
