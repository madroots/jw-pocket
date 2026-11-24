package app.jwpocket.phone.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jwpocket.phone.ui.theme.Pocket
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
 * One tab. The tab you're on is red: a filled icon on a soft red pill.
 * Pressing squashes the icon (wide and flat, held while the finger stays);
 * letting go springs it back like jelly, width and height out of step and a
 * little bigger than its size, before it settles.
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
    val squashX = remember { Animatable(1f) }
    val squashY = remember { Animatable(1f) }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(interaction) {
        var squash: Job? = null
        interaction.interactions.collect { event ->
            when (event) {
                is PressInteraction.Press -> squash = launch {
                    launch { squashX.animateTo(SQUASH_X, tween(SQUASH_MS, easing = FastOutSlowInEasing)) }
                    squashY.animateTo(SQUASH_Y, tween(SQUASH_MS, easing = FastOutSlowInEasing))
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> launch {
                    // A quick tap still squashes all the way before the spring back.
                    squash?.join()
                    launch { squashX.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 420f)) }
                    launch { squashY.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 520f)) }
                    pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 320f), initialVelocity = 1.4f)
                }
            }
        }
    }
    val on by animateFloatAsState(if (selected) 1f else 0f, tween(180), label = "tab")
    val tint by animateColorAsState(if (selected) Pocket.Accent else Pocket.TextMuted, tween(180), label = "tint")

    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(selected, interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(60.dp, 32.dp).graphicsLayer {
                scaleX = pop.value * squashX.value
                scaleY = pop.value * squashY.value
            },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = on; scaleX = 0.5f + 0.5f * on }
                    .background(Pocket.AccentSoft, RoundedCornerShape(50))
            )
            val glyph = @Composable { Icon(if (selected) selectedIcon else icon, null, tint = tint) }
            if (badge > 0) BadgedBox(badge = { Badge(containerColor = Pocket.Accent) { Text("$badge") } }) { glyph() } else glyph()
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

private const val SQUASH_X = 1.22f
private const val SQUASH_Y = 0.74f
private const val SQUASH_MS = 90
