package app.jwpocket.phone.ui.player

import android.graphics.Color
import android.util.TypedValue
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView

/**
 * The video picture. Only one surface shows the player at a time: when this
 * one leaves the screen it lets go, so the mini player, the full player and
 * picture-in-picture can hand the picture to each other.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(player: Player?, modifier: Modifier = Modifier, subtitleScale: Float = 1f, subtitles: Boolean = true, zoom: Boolean = false) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                setShutterBackgroundColor(Color.BLACK)
                setKeepContentOnPlayerReset(true)
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                subtitleView?.apply {
                    setApplyEmbeddedStyles(false)
                    setStyle(CaptionStyleCompat(Color.WHITE, 0x99000000.toInt(), Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_NONE, Color.WHITE, null))
                }
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
            view.resizeMode = if (zoom) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
            view.subtitleView?.visibility = if (subtitles) android.view.View.VISIBLE else android.view.View.GONE
            view.subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * subtitleScale)
        },
        onRelease = { it.player = null },
        modifier = modifier,
    )
}
