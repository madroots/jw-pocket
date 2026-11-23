package app.jwpocket.phone.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.jwpocket.core.data.LibraryRepository
import app.jwpocket.core.data.Settings
import app.jwpocket.core.model.Language
import app.jwpocket.core.model.Video
import app.jwpocket.core.util.formatBytes
import app.jwpocket.phone.R
import app.jwpocket.phone.ui.theme.Pocket

/**
 * Keeps scrolling inside the list. Without it the sheet sometimes takes a
 * scroll or fling that reaches the top of the list and drags itself down
 * instead; the sheet still closes by dragging its handle or title, tapping
 * outside, or Back.
 */
private val KeepScrollInList = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = available
    override suspend fun onPostFling(consumed: Velocity, available: Velocity) = available
}

/** A bottom sheet with a title, an optional [header] (a search field) and a list of choices. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoiceSheet(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    header: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Pocket.Surface,
    ) {
        Column(Modifier.navigationBarsPadding().imePadding()) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            }
            header?.invoke()
            Spacer(Modifier.size(8.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp).nestedScroll(KeepScrollInList), content = content)
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
 * language the video is published in, with a search field for long lists.
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
    var query by remember { mutableStateOf("") }
    val q = LibraryRepository.normalize(query).trim()
    val shown = if (q.isEmpty()) others else others.filter {
        LibraryRepository.normalize(it.vernacular).contains(q) || LibraryRepository.normalize(it.name).contains(q)
    }
    ChoiceSheet(
        stringResource(R.string.player_subtitles), stringResource(R.string.player_subtitles_hint), onDismiss,
        header = if (others.size > SEARCH_FROM) {
            {
                TextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.player_search_languages)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, stringResource(R.string.common_clear_text)) } },
                    shape = RoundedCornerShape(14.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Pocket.SurfaceHigh, unfocusedContainerColor = Pocket.SurfaceHigh,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                )
            }
        } else null,
    ) {
        if (q.isEmpty()) {
            item { ChoiceRow(stringResource(R.string.player_subtitles_off_choice), selected == Settings.SUBTITLES_OFF) { onPick(Settings.SUBTITLES_OFF) } }
            if (ownLanguageName != null) {
                item { ChoiceRow(ownLanguageName, selected == null, stringResource(R.string.player_subtitles_same_as_video)) { onPick(null) } }
            }
        }
        if (shown.isNotEmpty()) {
            if (q.isEmpty()) item { HorizontalDivider(Modifier.padding(vertical = 6.dp, horizontal = 24.dp), color = Pocket.SurfaceHighest) }
            items(shown, key = { it.code }) { lang ->
                ChoiceRow(
                    lang.vernacular,
                    selected == lang.code,
                    detail = when {
                        lang.code in pinned -> stringResource(R.string.player_subtitles_recently_used, lang.name)
                        lang.vernacular != lang.name -> lang.name
                        else -> null
                    },
                ) { onPick(lang.code) }
            }
        } else {
            item {
                Text(
                    if (q.isEmpty()) stringResource(R.string.player_subtitles_no_others) else stringResource(R.string.player_subtitles_no_match, query),
                    style = MaterialTheme.typography.bodySmall,
                    color = Pocket.TextSecondary,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        if (q.isEmpty() && showingLanguage == null && selected != Settings.SUBTITLES_OFF && ownLanguageName == null) {
            item { Text(stringResource(R.string.player_subtitles_none), color = Pocket.TextSecondary, modifier = Modifier.padding(24.dp)) }
        }
    }
}

/** Lists longer than this get a search field. */
private const val SEARCH_FROM = 8

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
        if (showAuto) item { ChoiceRow(stringResource(R.string.player_quality_auto), selected == null, stringResource(R.string.player_quality_auto_detail)) { onPick(null) } }
        items(video.qualities.reversed()) { q ->
            val size = video.file(q)?.filesize
            ChoiceRow(
                q,
                selected.equals(q, ignoreCase = true),
                detail = size?.let { formatBytes(it) },
                trailing = if (downloadedQuality.equals(q, ignoreCase = true)) {
                    { Icon(Icons.Rounded.CheckCircle, stringResource(R.string.common_downloaded), tint = Pocket.Success) }
                } else null,
            ) { onPick(q) }
        }
    }
}

@Composable
fun SpeedSheet(selected: Float, onPick: (Float) -> Unit, onDismiss: () -> Unit) {
    ChoiceSheet(stringResource(R.string.player_speed_title), onDismiss = onDismiss) {
        items(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)) { s ->
            ChoiceRow(if (s == 1f) stringResource(R.string.player_speed_normal) else "${s}×", selected == s) { onPick(s) }
        }
    }
}

@Composable
fun SleepSheet(active: Boolean, onPick: (Int?) -> Unit, onDismiss: () -> Unit) {
    ChoiceSheet(stringResource(R.string.player_sleep_timer), stringResource(R.string.player_sleep_hint), onDismiss) {
        if (active) item { ChoiceRow(stringResource(R.string.player_sleep_turn_off), false) { onPick(null) } }
        item { ChoiceRow(stringResource(R.string.player_sleep_end_of_video), false) { onPick(0) } }
        items(listOf(5, 15, 30, 45, 60, 90)) { m -> ChoiceRow(pluralStringResource(R.plurals.player_sleep_minutes, m, m), false) { onPick(m) } }
    }
}
