package app.jwpocket.tv.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.jwpocket.core.data.Settings
import app.jwpocket.tv.BuildConfig

private enum class SettingsPanel { Language, Subtitles, Stream, Download }

private val QUALITIES = listOf("240p", "360p", "480p", "720p", "1080p", "2160p")

@Composable
fun TvSettings() {
    val tv = LocalTv.current
    val settings by tv.core.settings.settings.collectAsState()
    val languages by tv.core.languages.languages.collectAsState()
    val updates by tv.core.updates.state.collectAsState()
    val repo = tv.core.settings
    var panel by remember { mutableStateOf<SettingsPanel?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 32.dp, bottom = 48.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(bottom = 16.dp)) }
        item { SettingRow("Library language", settings.languageName) { panel = SettingsPanel.Language } }
        item {
            SettingRow("Subtitle language", when (val s = settings.subtitleLanguage) {
                null -> "Same as the video"
                Settings.SUBTITLES_OFF -> "Off"
                else -> tv.core.languages.name(s)
            }) { panel = SettingsPanel.Subtitles }
        }
        item { SettingRow("Streaming quality", settings.streamQuality ?: "Auto") { panel = SettingsPanel.Stream } }
        item { SettingRow("Download quality", settings.downloadQuality ?: "Ask every time") { panel = SettingsPanel.Download } }
        item { SettingRow("Autoplay next video", if (settings.autoplayNext) "On" else "Off") { repo.update { it.copy(autoplayNext = !it.autoplayNext) } } }
        item { SettingRow("Download on Wi-Fi only", if (settings.wifiOnlyDownloads) "On" else "Off") { repo.update { it.copy(wifiOnlyDownloads = !it.wifiOnlyDownloads) } } }
        item { SettingRow("Check for updates", updates.message ?: "Version ${BuildConfig.VERSION_NAME}") { tv.core.updates.check(manual = true) } }
        item {
            Text(
                "JW Pocket is a personal, non-commercial project, not affiliated with or endorsed by Jehovah's Witnesses. " +
                    "Videos belong to their owners and come from the public jw.org video service.",
                color = Tv.TextSecondary, modifier = Modifier.padding(top = 24.dp).width(720.dp),
            )
        }
    }

    when (panel) {
        SettingsPanel.Language -> TvSidePanel(
            "Library language",
            options = languages.map { PanelOption(it.code, it.vernacular, it.name.takeIf { n -> n != it.vernacular }, it.code == settings.languageCode) },
            onPick = { o ->
                val lang = languages.first { it.code == o.key }
                repo.setLanguage(lang.code, lang.vernacular)
                tv.core.library.clearMemory()
                tv.core.languages.load(lang.code)
                panel = null
            },
            onDismiss = { panel = null },
        )
        SettingsPanel.Subtitles -> TvSidePanel(
            "Subtitle language", "Shown over every video published in it.",
            options = listOf(
                PanelOption("__same", "Same as the video", selected = settings.subtitleLanguage == null),
                PanelOption(Settings.SUBTITLES_OFF, "Off", selected = settings.subtitleLanguage == Settings.SUBTITLES_OFF),
            ) + tv.core.subtitles.subtitleLanguages(languages, settings.subtitleShortcuts).map {
                PanelOption(it.code, it.vernacular, if (it.code in settings.recentSubtitleLanguages) "Recently used" else null, it.code == settings.subtitleLanguage)
            },
            onPick = { o -> repo.setSubtitleLanguage(o.key.takeUnless { it == "__same" }); panel = null },
            onDismiss = { panel = null },
        )
        SettingsPanel.Stream -> TvSidePanel(
            "Streaming quality",
            options = listOf(PanelOption(null, "Auto", "Up to 720p", settings.streamQuality == null)) + QUALITIES.map { PanelOption(it, it, selected = settings.streamQuality == it) },
            onPick = { o -> repo.update { it.copy(streamQuality = o.key) }; panel = null },
            onDismiss = { panel = null },
        )
        SettingsPanel.Download -> TvSidePanel(
            "Download quality",
            options = listOf(PanelOption(null, "Ask every time", selected = settings.downloadQuality == null)) + QUALITIES.map { PanelOption(it, it, selected = settings.downloadQuality == it) },
            onPick = { o -> repo.update { it.copy(downloadQuality = o.key) }; panel = null },
            onDismiss = { panel = null },
        )
        null -> Unit
    }
}

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, modifier = Modifier.fillMaxWidth(0.7f).padding(vertical = 5.dp), scale = 1.02f) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium) }
            Text(value, color = Tv.TextSecondary)
        }
    }
}
