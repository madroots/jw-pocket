package app.jwpocket.phone.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.jwpocket.core.data.Settings
import app.jwpocket.core.model.Language
import app.jwpocket.core.model.Video
import app.jwpocket.core.util.formatBytes
import app.jwpocket.phone.ui.theme.Pocket

/** A bottom sheet with a title and a list of choices. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoiceSheet(title: String, subtitle: String? = null, onDismiss: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Pocket.Surface,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            }
            Spacer(Modifier.size(8.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), content = content)
            Spacer(Modifier.size(12.dp))
        }
    }
}

@Composable
fun ChoiceRow(label: String, selected: Boolean, detail: String? = null, trailing: @Composable (() -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = if (selected) Pocket.Accent else MaterialTheme.colorScheme.onSurface)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary)
        }
        trailing?.invoke()
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.Check, null, tint = Pocket.Accent)
        }
    }
}

/**
 * Subtitles: Off, the video's own language, recent picks and every other
 * language the video is published in.
 */
@Composable
fun SubtitleSheet(
    ownLanguageName: String?,
    others: List<Language>,
    pinned: List<String>,
    selected: String?,
    showingLanguage: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ChoiceSheet("Subtitles", "Your choice is remembered for the next videos.", onDismiss) {
        item { ChoiceRow("Off", selected == Settings.SUBTITLES_OFF) { onPick(Settings.SUBTITLES_OFF) } }
        if (ownLanguageName != null) {
            item { ChoiceRow(ownLanguageName, selected == null, "Same as the video") { onPick(null) } }
        }
        if (others.isNotEmpty()) {
            item { HorizontalDivider(Modifier.padding(vertical = 6.dp, horizontal = 24.dp), color = Pocket.SurfaceHighest) }
            items(others, key = { it.code }) { lang ->
                ChoiceRow(
                    lang.vernacular,
                    selected == lang.code,
                    detail = when {
                        lang.code in pinned -> "Recently used · ${lang.name}"
                        lang.vernacular != lang.name -> lang.name
                        else -> null
                    },
                ) { onPick(lang.code) }
            }
        } else {
            item {
                Text(
                    "This video has no other subtitle languages.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Pocket.TextSecondary,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        if (showingLanguage == null && selected != Settings.SUBTITLES_OFF && ownLanguageName == null) {
            item { Text("No subtitles for this video.", color = Pocket.TextSecondary, modifier = Modifier.padding(24.dp)) }
        }
    }
}

/** Auto, then each quality with its size; the downloaded one is marked. */
@Composable
fun QualitySheet(
    video: Video,
    title: String,
    selected: String?,
    downloadedQuality: String?,
    showAuto: Boolean,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ChoiceSheet(title, onDismiss = onDismiss) {
        if (showAuto) item { ChoiceRow("Auto", selected == null, "Best for this screen, up to 720p") { onPick(null) } }
        items(video.qualities.reversed()) { q ->
            val size = video.file(q)?.filesize
            ChoiceRow(
                q,
                selected.equals(q, ignoreCase = true),
                detail = size?.let { formatBytes(it) },
                trailing = if (downloadedQuality.equals(q, ignoreCase = true)) {
                    { Icon(Icons.Rounded.CheckCircle, "Downloaded", tint = Pocket.Success) }
                } else null,
            ) { onPick(q) }
        }
    }
}

@Composable
fun SpeedSheet(selected: Float, onPick: (Float) -> Unit, onDismiss: () -> Unit) {
    ChoiceSheet("Playback speed", onDismiss = onDismiss) {
        items(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)) { s ->
            ChoiceRow(if (s == 1f) "Normal" else "${s}×", selected == s) { onPick(s) }
        }
    }
}

@Composable
fun SleepSheet(active: Boolean, onPick: (Int?) -> Unit, onDismiss: () -> Unit) {
    ChoiceSheet("Sleep timer", "Playback pauses by itself.", onDismiss) {
        if (active) item { ChoiceRow("Turn off timer", false) { onPick(null) } }
        item { ChoiceRow("End of this video", false) { onPick(0) } }
        items(listOf(5, 15, 30, 45, 60, 90)) { m -> ChoiceRow("$m minutes", false) { onPick(m) } }
    }
}
