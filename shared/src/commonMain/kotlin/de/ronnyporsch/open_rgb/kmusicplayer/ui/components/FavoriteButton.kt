package de.ronnyporsch.open_rgb.kmusicplayer.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.ronnyporsch.open_rgb.kmusicplayer.data.repository.Favorites
import de.ronnyporsch.open_rgb.kmusicplayer.domain.model.FavoriteKind
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.SpotifyGreen
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.SpotifyLightGray

val LocalFavorites = staticCompositionLocalOf<Favorites?> { null }

/** Heart that marks the item [id] as favorite; shows nothing for items that can't be favorites (e.g. YouTube Music). */
@Composable
fun FavoriteButton(
    kind: FavoriteKind,
    id: String?,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
) {
    val favorites = LocalFavorites.current ?: return
    if (id == null || !favorites.canBeFavorite(id)) return
    val ids by favorites.ids.collectAsState()
    val isFavorite = id in ids
    TooltipIconButton(
        onClick = { favorites.toggle(kind, id) },
        icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
        tint = if (isFavorite) SpotifyGreen else SpotifyLightGray,
        modifier = modifier,
        iconSize = iconSize,
    )
}
