package de.ronnyporsch.open_rgb.kmusicplayer.ui.components

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.onMouseNavigation(
    onBack: () -> Unit,
    onForward: () -> Unit
): Modifier = this.onPointerEvent(PointerEventType.Press) { event ->
    if (event.button == PointerButton.Back) {
        onBack()
    } else if (event.button == PointerButton.Forward) {
        onForward()
    }
}
