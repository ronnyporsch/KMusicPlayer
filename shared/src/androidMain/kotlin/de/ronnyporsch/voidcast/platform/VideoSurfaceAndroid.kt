package de.ronnyporsch.voidcast.platform

import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
internal actual fun PlatformVideoSurface(player: AudioPlayer, modifier: Modifier) {
    val exoPlayer = (player as? AudioPlayerAndroid)?.exoPlayer ?: return
    // A TextureView (unlike a SurfaceView) is clipped and animated like any other composable
    key(exoPlayer) {
        AndroidView(
            factory = { context -> TextureView(context).also(exoPlayer::setVideoTextureView) },
            modifier = modifier,
            onRelease = exoPlayer::clearVideoTextureView,
        )
    }
}
