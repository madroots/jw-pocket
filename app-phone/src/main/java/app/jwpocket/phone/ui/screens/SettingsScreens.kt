package app.jwpocket.phone.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.jwpocket.core.data.LibraryRepository
import app.jwpocket.core.data.Settings
import app.jwpocket.core.model.Language
import app.jwpocket.phone.BuildConfig
import app.jwpocket.phone.ui.LocalPocket
import app.jwpocket.phone.ui.player.ChoiceSheet
import app.jwpocket.phone.ui.player.ChoiceRow
import app.jwpocket.phone.ui.theme.Pocket

private val QUALITIES = listOf("240p", "360p", "480p", "720p", "1080p")

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onOpenLanguage: () -> Unit,
    onOpenSubtitles: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val pocket = LocalPocket.current
    val settings by pocket.core.settings.settings.collectAsState()
    val repo = pocket.core.settings
    var pick by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item { Group("Library") }
        item { Entry("Language", settings.languageName, onClick = onOpenLanguage) }

        item { Group("Playback") }
        item { Toggle("Keep playing in the background", "Continue as audio with the screen locked or in other apps", settings.backgroundPlayback) { on -> repo.update { it.copy(backgroundPlayback = on) } } }
        item { Toggle("Picture-in-picture", "Shrink the video to a floating window when you leave the app", settings.autoPictureInPicture) { on -> repo.update { it.copy(autoPictureInPicture = on) } } }
        item { Toggle("Autoplay next video", "Play the next video of the same list", settings.autoplayNext) { on -> repo.update { it.copy(autoplayNext = on) } } }
        item { Entry("Streaming quality", settings.streamQuality ?: "Auto") { pick = "stream" } }

        item { Group("Subtitles") }
        item { Entry("Subtitle language", subtitleLabel(settings, pocket.core.languages::name), onClick = onOpenSubtitles) }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Subtitle size", style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value = settings.subtitleScale,
                    onValueChange = { v -> repo.update { it.copy(subtitleScale = v) } },
                    valueRange = 0.75f..1.75f,
                    steps = 3,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Pocket.Accent),
                )
            }
        }

        item { Group("Downloads") }
        item { Entry("Download quality", settings.downloadQuality ?: "Ask every time") { pick = "download" } }
        item { Toggle("Wi-Fi only", "Wait for Wi-Fi before downloading", settings.wifiOnlyDownloads) { on -> repo.update { it.copy(wifiOnlyDownloads = on) } } }
        item { Entry("Manage downloads", "See and delete downloaded videos", onClick = onOpenDownloads) }

        item { Group("App") }
        item { Entry("Check for updates", "Version ${BuildConfig.VERSION_NAME}") { pocket.core.updates.check(manual = true) } }
        item {
            Text(
                "JW Pocket is a personal, non-commercial project. It isn't affiliated with or endorsed by Jehovah's Witnesses. " +
                    "All videos belong to their owners and come from the public jw.org video service.",
                style = MaterialTheme.typography.bodySmall,
                color = Pocket.TextMuted,
                modifier = Modifier.padding(20.dp),
            )
        }
    }

    when (pick) {
        "stream" -> ChoiceSheet("Streaming quality", "Downloaded videos always play in their downloaded quality.", { pick = null }) {
            item { ChoiceRow("Auto", settings.streamQuality == null, "Up to 720p") { repo.update { it.copy(streamQuality = null) }; pick = null } }
            items(QUALITIES) { q -> ChoiceRow(q, settings.streamQuality == q) { repo.update { it.copy(streamQuality = q) }; pick = null } }
        }
        "download" -> ChoiceSheet("Download quality", "Smaller qualities save space.", { pick = null }) {
            item { ChoiceRow("Ask every time", settings.downloadQuality == null) { repo.update { it.copy(downloadQuality = null) }; pick = null } }
            items(QUALITIES) { q -> ChoiceRow(q, settings.downloadQuality == q) { repo.update { it.copy(downloadQuality = q) }; pick = null } }
        }
    }
}

fun subtitleLabel(settings: Settings, name: (String) -> String): String = when (val s = settings.subtitleLanguage) {
    null -> "Same as the video"
    Settings.SUBTITLES_OFF -> "Off"
    else -> name(s)
}

@Composable
private fun Group(title: String) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = Pocket.Accent, modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 4.dp))
}

@Composable
private fun Entry(title: String, value: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary)
    }
}

@Composable
private fun Toggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Pocket.Accent))
    }
}

/** A searchable list of languages. */
@Composable
private fun LanguageList(
    contentPadding: PaddingValues,
    languages: List<Language>,
    selected: String?,
    header: (@Composable () -> Unit)? = null,
    onPick: (Language) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val q = LibraryRepository.normalize(query)
    val shown = if (q.isEmpty()) languages else languages.filter {
        LibraryRepository.normalize(it.vernacular).contains(q) || LibraryRepository.normalize(it.name).contains(q)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            TextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Search languages") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Pocket.SurfaceHigh, unfocusedContainerColor = Pocket.SurfaceHigh,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
        if (q.isEmpty() && header != null) item { header() }
        items(shown, key = { it.code }) { lang ->
            Row(Modifier.fillMaxWidth().clickable { onPick(lang) }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(lang.vernacular, style = MaterialTheme.typography.bodyLarge, color = if (lang.code == selected) Pocket.Accent else MaterialTheme.colorScheme.onSurface)
                    if (lang.name != lang.vernacular) Text(lang.name, style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary)
                }
                if (lang.code == selected) Icon(Icons.Rounded.Check, null, tint = Pocket.Accent)
            }
        }
    }
}

@Composable
fun LanguageScreen(contentPadding: PaddingValues, onDone: () -> Unit) {
    val pocket = LocalPocket.current
    val settings by pocket.core.settings.settings.collectAsState()
    val languages by pocket.core.languages.languages.collectAsState()
    LanguageList(contentPadding, languages, settings.languageCode) { lang ->
        pocket.core.settings.setLanguage(lang.code, lang.vernacular)
        pocket.core.library.clearMemory()
        pocket.core.languages.load(lang.code)
        onDone()
    }
}

@Composable
fun SubtitleLanguageScreen(contentPadding: PaddingValues, onDone: () -> Unit) {
    val pocket = LocalPocket.current
    val settings by pocket.core.settings.settings.collectAsState()
    val languages by pocket.core.languages.languages.collectAsState()
    val list = remember(languages, settings.subtitleShortcuts) { pocket.core.subtitles.subtitleLanguages(languages, settings.subtitleShortcuts) }
    LanguageList(
        contentPadding, list, settings.subtitleLanguage,
        header = {
            Column {
                ChoiceRow("Same as the video", settings.subtitleLanguage == null, "Subtitles in the language of the library") { pocket.core.settings.setSubtitleLanguage(null); onDone() }
                ChoiceRow("Off", settings.subtitleLanguage == Settings.SUBTITLES_OFF) { pocket.core.settings.setSubtitleLanguage(Settings.SUBTITLES_OFF); onDone() }
                Text(
                    "Any language: shown over every video published in it, whatever language the video is in.",
                    style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        },
    ) { lang ->
        pocket.core.settings.setSubtitleLanguage(lang.code)
        onDone()
    }
}
