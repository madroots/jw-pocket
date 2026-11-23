package app.jwpocket.phone.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jwpocket.core.model.Video
import app.jwpocket.phone.ui.theme.Pocket
import coil.compose.AsyncImage
import kotlin.math.PI
import kotlin.math.sin

/**
 * Shown instead of the picture in Listen mode: the blurred poster with
 * moving sound bars and, unless [compact], the subtitle line. The bars are
 * decorative (no microphone or visualizer permission); they move while
 * [playing] and settle when paused.
 */
@Composable
fun ListenVisual(
    video: Video,
    playing: Boolean,
    cue: String?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    subtitleScale: Float = 1f,
) {
    Box(modifier.background(Color.Black).clipToBounds()) {
        AsyncImage(
            model = video.posterUrl ?: video.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(if (compact) 6.dp else 20.dp),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.75f)))))
        Column(
            Modifier.fillMaxSize().padding(horizontal = if (compact) 6.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            SoundBars(
                playing = playing,
                bars = if (compact) 5 else 9,
                modifier = Modifier.fillMaxWidth(if (compact) 0.7f else 0.42f).then(if (compact) Modifier.fillMaxHeight(0.6f) else Modifier.height(72.dp)),
            )
            if (!compact && cue != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    cue,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = (17 * subtitleScale).sp,
                        lineHeight = (23 * subtitleScale).sp,
                        shadow = Shadow(Color.Black, Offset(0f, 2f), 6f),
                    ),
                )
            }
        }
    }
}

/** Rounded bars that rise and fall in a loop; each bar has its own whole-number rhythm, so the loop is seamless. */
@Composable
private fun SoundBars(playing: Boolean, bars: Int, modifier: Modifier) {
    val level by animateFloatAsState(if (playing) 1f else 0.15f, tween(450), label = "level")
    val time by rememberInfiniteTransition(label = "bars").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(7_000, easing = LinearEasing)),
        label = "time",
    )
    Canvas(modifier) {
        val gap = size.width / (bars * 2 - 1)
        val radius = CornerRadius(gap / 2, gap / 2)
        for (i in 0 until bars) {
            // Two waves per bar, frequencies 3..9 times a cycle: lively but never in step.
            val wave = 0.5f + 0.28f * sin(time * (3 + i % 4) + i * 1.7f) + 0.22f * sin(time * (5 + i % 5) + i * 0.9f)
            // Middle bars reach higher, like a voice.
            val envelope = 0.55f + 0.45f * sin(PI.toFloat() * (i + 0.5f) / bars)
            val h = size.height * (0.12f + 0.88f * wave.coerceIn(0f, 1f) * envelope * level).coerceAtLeast(0.08f)
            drawRoundRect(
                color = Pocket.Accent,
                topLeft = Offset(i * gap * 2, (size.height - h) / 2),
                size = Size(gap, h),
                cornerRadius = radius,
            )
        }
    }
}
