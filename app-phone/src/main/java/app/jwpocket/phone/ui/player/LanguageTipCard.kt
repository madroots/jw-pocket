package app.jwpocket.phone.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.jwpocket.core.data.LanguageTip
import app.jwpocket.core.data.Settings
import app.jwpocket.core.data.TipKind
import app.jwpocket.phone.R
import app.jwpocket.phone.ui.LocalPocket
import app.jwpocket.phone.ui.theme.Pocket

/**
 * The tip that comes after the same language (or subtitles) was picked for
 * video after video: make it the default, or put it away (× or "Not now").
 * Either way it never comes back for that choice.
 */
@Composable
fun LanguageTipCard(modifier: Modifier = Modifier) {
    val pocket = LocalPocket.current
    val tip by pocket.core.tips.tip.collectAsState()
    AnimatedVisibility(tip != null, modifier = modifier) {
        val t = tip ?: return@AnimatedVisibility
        val name = pocket.core.languages.name(t.language)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pocket.SurfaceHigh).padding(start = 14.dp, top = 6.dp, end = 4.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lightbulb, null, tint = Pocket.Accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tipTitle(t, name), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { pocket.core.tips.dismiss(t) }) { Icon(Icons.Rounded.Close, stringResource(R.string.tip_not_now), tint = Pocket.TextSecondary) }
            }
            Text(tipBody(t, name), style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary, modifier = Modifier.padding(end = 10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                TextButton(onClick = { pocket.core.tips.dismiss(t) }) { Text(stringResource(R.string.tip_not_now), color = Pocket.TextSecondary) }
                TextButton(onClick = { pocket.core.acceptTip(t) }) { Text(stringResource(R.string.tip_make_default), color = Pocket.Accent) }
            }
        }
    }
}

@Composable
private fun tipTitle(tip: LanguageTip, name: String): String = when {
    tip.kind == TipKind.VideoLanguage -> stringResource(R.string.tip_video_title, name)
    tip.language == Settings.SUBTITLES_OFF -> stringResource(R.string.tip_subtitles_off_title)
    else -> stringResource(R.string.tip_subtitles_title, name)
}

@Composable
private fun tipBody(tip: LanguageTip, name: String): String = when {
    tip.kind == TipKind.VideoLanguage -> stringResource(R.string.tip_video_body, name)
    tip.language == Settings.SUBTITLES_OFF -> stringResource(R.string.tip_subtitles_off_body)
    else -> stringResource(R.string.tip_subtitles_body, name)
}
