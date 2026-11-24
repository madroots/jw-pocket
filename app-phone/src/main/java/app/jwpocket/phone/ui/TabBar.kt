package app.jwpocket.phone.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jwpocket.phone.ui.theme.Pocket
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

/** The bottom tab bar; fill it with [PocketTab]s. */
@Composable
internal fun PocketTabBar(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Pocket.Surface).navigationBarsPadding().height(76.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * One tab. The tab you're on is red, its icon filled and a little bigger.
 * Pressing squashes the icon; letting go makes it jelly: it puffs up, wobbles
 * side to side and settles, a drop of red splashes out and back, and a glint
 * sweeps across it. Tuned in mockups/jelly-playground.html.
 */
@Composable
internal fun RowScope.PocketTab(
    selected: Boolean,
    icon: ImageVector,
    selectedIcon: ImageVector,
    label: String,
    badge: Int = 0,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val jelly = remember { Jelly() }
    LaunchedEffect(interaction) {
        var pressedAt = 0L
        interaction.interactions.collect { event ->
            when (event) {
                is PressInteraction.Press -> {
                    pressedAt = System.currentTimeMillis()
                    launch { jelly.press() }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> launch {
                    // A quick tap still squashes before the jelly.
                    delay((SQUASH_MS - (System.currentTimeMillis() - pressedAt)).coerceAtLeast(0))
                    jelly.release(this)
                }
            }
        }
    }
    val grow by animateFloatAsState(if (selected) ACTIVE_SCALE else 1f, tween(SELECT_MS), label = "grow")
    val tint by animateColorAsState(if (selected) Pocket.Accent else Pocket.TextMuted, tween(SELECT_MS), label = "tint")

    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(selected, interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(60.dp, 32.dp).drawBehind {
                // The drop splashes out of the icon and is pulled back, shrinking.
                val life = jelly.drop.value
                if (life < 1f) {
                    val radius = jelly.dropSize.dp.toPx() / 2 * (1 - life)
                    drawCircle(Pocket.Accent.copy(alpha = 0.45f), radius, center + Offset(jelly.dropX.value.dp.toPx(), jelly.dropY.value.dp.toPx()))
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .graphicsLayer {
                        val puff = jelly.pop.value * grow
                        scaleX = puff * jelly.squashX.value
                        scaleY = puff * jelly.squashY.value
                        rotationZ = jelly.rotation.value
                        translationY = -jelly.lift.value.dp.toPx()
                    }
                    .drawWithContent {
                        // Side-to-side jelly: a skew around the middle (layers can't skew).
                        val skew = Matrix().apply { values[Matrix.SkewX] = tan(jelly.skew.value * PI.toFloat() / 180f) }
                        withTransform({ translate(center.x, center.y); transform(skew); translate(-center.x, -center.y) }) { this@drawWithContent.drawContent() }
                    },
                contentAlignment = Alignment.Center,
            ) {
                val glyph = @Composable {
                    Icon(
                        if (selected) selectedIcon else icon, null, tint = tint,
                        modifier = Modifier
                            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                            .drawWithContent {
                                drawContent()
                                // The glint: a white band sweeping across the icon's own shape.
                                val g = jelly.glint.value
                                if (g in 0f..0.999f) {
                                    val x = -0.6f * size.width + g * 2.2f * size.width
                                    val band = size.width * 0.35f
                                    drawRect(
                                        Brush.linearGradient(
                                            listOf(Color.Transparent, Color.White, Color.Transparent),
                                            start = Offset(x - band, 0f), end = Offset(x + band, size.height * 0.5f),
                                        ),
                                        blendMode = BlendMode.SrcAtop,
                                    )
                                }
                            },
                    )
                }
                if (badge > 0) BadgedBox(badge = { Badge(containerColor = Pocket.Accent) { Text("$badge") } }) { glyph() } else glyph()
            }
        }
        Spacer(Modifier.height(4.dp))
        // Five tabs: labels stay on one line (long words in some languages).
        Text(
            label, style = MaterialTheme.typography.labelSmall, color = tint,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The springs of one tab's icon (mass 1; the playground uses the same numbers). */
private class Jelly {
    val squashX = Animatable(1f)
    val squashY = Animatable(1f)
    val lift = Animatable(0f)
    val pop = Animatable(1f)
    val skew = Animatable(0f)
    val rotation = Animatable(0f)
    /** 0..1 across the icon; 1 = done. */
    val glint = Animatable(1f)
    /** Life of the drop, 0..1; 1 = gone. */
    val drop = Animatable(1f)
    val dropX = Animatable(0f)
    val dropY = Animatable(0f)
    var dropSize = 10f

    suspend fun press() = kotlinx.coroutines.coroutineScope {
        val firm = spring<Float>(dampingRatio = 1f, stiffness = 1400f)
        launch { squashX.animateTo(1 + SQUASH, firm) }
        launch { squashY.animateTo(1 - SQUASH, firm) }
        lift.animateTo(2f, firm)
    }

    fun release(scope: kotlinx.coroutines.CoroutineScope) = with(scope) {
        val k = STIFFNESS
        val z = WOBBLE
        val root = sqrt(k)
        fun side() = if (Random.nextBoolean()) 1f else -1f
        launch { squashX.animateTo(1f, spring(z, k)) }
        launch { squashY.animateTo(1f, spring(z * 0.9f, k * 1.2f)) }
        launch { lift.animateTo(0f, spring(z, k)) }
        launch { pop.animateTo(1f, spring(min(0.9f, z + 0.08f), k * 0.8f), initialVelocity = BLOW * root * 2.2f) }
        launch { skew.animateTo(0f, spring(z * 0.8f, k * 0.9f), initialVelocity = side() * SHAKE * root * 9f) }
        launch { rotation.animateTo(0f, spring(z, k * 0.7f), initialVelocity = side() * SHAKE * root * 3f) }
        launch { glint.snapTo(0f); glint.animateTo(1f, tween(GLINT_MS, easing = LinearEasing)) }
        launch {
            val angle = Random.nextFloat() * 2 * PI.toFloat()
            val speed = 156f + Random.nextFloat() * 96f
            dropSize = 8f + Random.nextFloat() * 8f
            dropX.snapTo(0f); dropY.snapTo(0f); drop.snapTo(0f)
            val pull = spring<Float>(dampingRatio = 0.37f, stiffness = 90f)
            launch { dropX.animateTo(0f, pull, initialVelocity = cos(angle) * speed) }
            launch { dropY.animateTo(0f, pull, initialVelocity = sin(angle) * speed * 0.7f) }
            drop.animateTo(1f, tween(DROP_MS, easing = LinearEasing))
        }
    }
}

private const val SQUASH = 0.1f
private const val STIFFNESS = 530f
private const val WOBBLE = 0.13f
private const val BLOW = 0.23f
private const val SHAKE = 0.9f
private const val SQUASH_MS = 90L
private const val GLINT_MS = 550
private const val DROP_MS = 1100
private const val SELECT_MS = 220
/** The tab you're on: its icon a little bigger than the others. */
private const val ACTIVE_SCALE = 1.15f
