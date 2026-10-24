package de.ronnyporsch.voidcast.ui.components

import androidx.compose.ui.Modifier

expect fun Modifier.onMouseNavigation(
    onBack: () -> Unit,
    onForward: () -> Unit
): Modifier
