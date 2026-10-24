package de.ronnyporsch.voidcast.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Snackbar host whose message uses the snackbar's content color: the app typography hardcodes a grey
 * color in bodyMedium, which would otherwise override it.
 */
@Composable
fun AppSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier) { data ->
        val typography = MaterialTheme.typography
        MaterialTheme(typography = typography.copy(bodyMedium = typography.bodyMedium.copy(color = Color.Unspecified))) {
            Snackbar(data)
        }
    }
}
