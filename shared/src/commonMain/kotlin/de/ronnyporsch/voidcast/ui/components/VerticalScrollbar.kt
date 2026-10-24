package de.ronnyporsch.voidcast.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun VerticalScrollbar(
    modifier: Modifier,
    state: LazyListState
)
