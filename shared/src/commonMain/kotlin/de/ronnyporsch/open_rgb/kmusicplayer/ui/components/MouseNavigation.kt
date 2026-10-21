package de.ronnyporsch.open_rgb.kmusicplayer.ui.components

import androidx.compose.ui.Modifier

expect fun Modifier.onMouseNavigation(
    onBack: () -> Unit,
    onForward: () -> Unit
): Modifier
