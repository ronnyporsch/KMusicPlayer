package de.ronnyporsch.voidcast.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun VerticalScrollbar(
    modifier: Modifier,
    state: LazyListState
) {
    // No-op for Android as it has native scrollbars or doesn't need them in this style
}
