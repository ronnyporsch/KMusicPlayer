package de.ronnyporsch.voidcast.ui.components

import androidx.compose.ui.Modifier

actual fun Modifier.onMouseNavigation(
    onBack: () -> Unit,
    onForward: () -> Unit
): Modifier = this
