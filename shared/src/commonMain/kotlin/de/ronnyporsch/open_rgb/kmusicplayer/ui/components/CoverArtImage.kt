package de.ronnyporsch.open_rgb.kmusicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.AccentColor
import de.ronnyporsch.open_rgb.kmusicplayer.ui.theme.SpotifySurface

@Composable
fun CoverArtImage(
    url: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 0.dp,
    placeholderText: String = "\u266B",
    placeholderFontSize: TextUnit = 20.sp,
) {
    if (url != null) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(cornerRadius))
                .background(SpotifySurface),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(cornerRadius))
                .background(SpotifySurface),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = placeholderText,
                fontSize = placeholderFontSize,
                color = AccentColor,
            )
        }
    }
}

@Composable
fun ArtistImage(
    url: String?,
    artistName: String,
    modifier: Modifier = Modifier,
) {
    if (url != null) {
        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(SpotifySurface),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = url,
                contentDescription = artistName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    } else {
        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(SpotifySurface),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = artistName.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = AccentColor,
            )
        }
    }
}
