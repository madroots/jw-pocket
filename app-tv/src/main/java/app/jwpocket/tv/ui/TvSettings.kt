package app.jwpocket.tv.ui

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.jwpocket.core.data.Settings
import app.jwpocket.core.model.Language
import app.jwpocket.core.util.AppLanguage
import app.jwpocket.tv.BuildConfig
import app.jwpocket.tv.R

private enum class SettingsPanel { AppLanguage, AppCascade, Language, VideoCascade, Subtitles, Stream, Download }

private val QUALITIES = listOf("240p", "360p", "480p", "720p", "1080p", "2160p")

@Composable
fun TvSettings() {
    val tv = LocalTv.current
    val settings by tv.core.settings.settings.collectAsState()
    val languages by tv.core.languages.languages.collectAsState()
    val updates by tv.core.updates.state.collectAsState()
    val repo = tv.core.settings
    val activity = LocalContext.current as? Activity
    var panel by remember { mutableStateOf<SettingsPanel?>(null) }
    // The row that opened a panel gets focus back when the panel closes.
    val rows = remember { SettingsPanel.entries.associateWith { FocusRequester() } }
    var opened by remember { mutableStateOf<SettingsPanel?>(null) }
    fun open(p: SettingsPanel) { opened = p; panel = p }
    fun row(p: SettingsPanel) = Modifier.focusRequester(rows.getValue(p))
    LaunchedEffect(panel) { if (panel == null) opened?.let { runCatching { rows.getValue(it).requestFocus() } } }

    // A language change waiting for the answer to "the ones below too?".
    var pendingAppLanguage by remember { mutableStateOf<String?>(null) }
    var pendingVideos by remember { mutableStateOf<Language?>(null) }
    fun applyVideos(lang: Language, subtitlesToo: Boolean) {
        repo.setLanguage(lang.code, lang.vernacular)
        if (subtitlesToo) repo.setSubtitleLanguage(null)
        tv.core.library.clearMemory()
        tv.core.languages.load(lang.code)
    }
    fun applyApp(tag: String?, videos: Language?, subtitlesToo: Boolean) {
        repo.setAppLanguage(tag)
        if (videos != null) applyVideos(videos, subtitlesToo)
        panel = null
        activity?.recreate()
    }
    val subtitleValue = when (val s = settings.subtitleLanguage) {
        null -> stringResource(R.string.player_subtitles_same_as_video)
        Settings.SUBTITLES_OFF -> stringResource(R.string.settings_off)
        else -> tv.core.languages.name(s)
    }
    val deviceLanguage = stringResource(R.string.settings_device_language, AppLanguage.name(AppLanguage.effective(null)))
    val on = stringResource(R.string.settings_on)
    val off = stringResource(R.string.settings_off)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 32.dp, bottom = 48.dp)) {
        item { Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(bottom = 16.dp)) }
        // Top to bottom: each language follows the one above it unless changed.
        item { SettingRow(stringResource(R.string.settings_app_language), settings.appLanguage?.let(AppLanguage::name) ?: deviceLanguage, row(SettingsPanel.AppLanguage)) { open(SettingsPanel.AppLanguage) } }
        item { SettingRow(stringResource(R.string.settings_video_language), settings.languageName, row(SettingsPanel.Language)) { open(SettingsPanel.Language) } }
        item { SettingRow(stringResource(R.string.settings_subtitle_language), subtitleValue, row(SettingsPanel.Subtitles)) { open(SettingsPanel.Subtitles) } }
        item { SettingRow(stringResource(R.string.settings_streaming_quality), settings.streamQuality ?: stringResource(R.string.common_quality_auto), row(SettingsPanel.Stream)) { open(SettingsPanel.Stream) } }
        item { SettingRow(stringResource(R.string.settings_download_quality), settings.downloadQuality ?: stringResource(R.string.settings_ask_every_time), row(SettingsPanel.Download)) { open(SettingsPanel.Download) } }
        item { SettingRow(stringResource(R.string.settings_autoplay), if (settings.autoplayNext) on else off) { repo.update { it.copy(autoplayNext = !it.autoplayNext) } } }
        item { SettingRow(stringResource(R.string.settings_wifi_only), if (settings.wifiOnlyDownloads) on else off) { repo.update { it.copy(wifiOnlyDownloads = !it.wifiOnlyDownloads) } } }
        item { SettingRow(stringResource(R.string.settings_check_updates), updates.message ?: stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)) { tv.core.updates.check(manual = true) } }
        item {
            Text(
                stringResource(R.string.settings_disclaimer),
                color = Tv.TextSecondary, modifier = Modifier.padding(top = 24.dp).width(720.dp),
            )
        }
    }

    when (panel) {
        SettingsPanel.AppLanguage -> TvSidePanel(
            stringResource(R.string.settings_app_language),
            options = listOf(PanelOption(null, deviceLanguage, selected = settings.appLanguage == null)) +
                AppLanguage.supported.map { (tag, name) -> PanelOption(tag, name, selected = settings.appLanguage == tag) },
            onPick = { o ->
                val match = tv.core.languages.forLocale(AppLanguage.effective(o.key))
                if (match != null && match.code != settings.languageCode) {
                    pendingAppLanguage = o.key
                    pendingVideos = match
                    panel = SettingsPanel.AppCascade
                } else applyApp(o.key, null, false)
            },
            onDismiss = { panel = null },
        )
        SettingsPanel.AppCascade -> pendingVideos?.let { videos ->
            TvSidePanel(
                stringResource(R.string.cascade_app_title, videos.vernacular),
                stringResource(R.string.cascade_app_text, videos.vernacular),
                options = listOf(
                    PanelOption("all", stringResource(R.string.cascade_app_yes), selected = true),
                    PanelOption("videos", stringResource(R.string.cascade_app_videos_only)),
                    PanelOption("app", stringResource(R.string.cascade_app_no)),
                ),
                onPick = { o -> applyApp(pendingAppLanguage, videos.takeIf { o.key != "app" }, subtitlesToo = o.key == "all") },
                onDismiss = { applyApp(pendingAppLanguage, null, false) },
            )
        }
        SettingsPanel.Language -> TvSidePanel(
            stringResource(R.string.settings_video_language),
            options = languages.map { PanelOption(it.code, it.vernacular, it.name.takeIf { n -> n != it.vernacular }, it.code == settings.languageCode) },
            onPick = { o ->
                val lang = languages.first { it.code == o.key }
                val subs = settings.subtitleLanguage
                if (subs != null && subs != Settings.SUBTITLES_OFF && subs != lang.code) {
                    pendingVideos = lang
                    panel = SettingsPanel.VideoCascade
                } else {
                    applyVideos(lang, subtitlesToo = false)
                    panel = null
                }
            },
            onDismiss = { panel = null },
        )
        SettingsPanel.VideoCascade -> pendingVideos?.let { videos ->
            val subs = tv.core.languages.name(settings.subtitleLanguage.orEmpty())
            TvSidePanel(
                stringResource(R.string.cascade_video_title, videos.vernacular),
                stringResource(R.string.cascade_video_text, subs),
                options = listOf(
                    PanelOption("yes", stringResource(R.string.cascade_video_yes), selected = true),
                    PanelOption("no", stringResource(R.string.cascade_video_no, subs)),
                ),
                onPick = { o -> applyVideos(videos, subtitlesToo = o.key == "yes"); panel = null },
                onDismiss = { applyVideos(videos, subtitlesToo = false); panel = null },
            )
        }
        SettingsPanel.Subtitles -> {
            val mostUsed = stringResource(R.string.settings_most_used)
            TvSidePanel(
                stringResource(R.string.settings_subtitle_language), stringResource(R.string.settings_subtitles_note),
                options = listOf(
                    PanelOption("__same", stringResource(R.string.player_subtitles_same_as_video), selected = settings.subtitleLanguage == null),
                    PanelOption(Settings.SUBTITLES_OFF, stringResource(R.string.settings_off), selected = settings.subtitleLanguage == Settings.SUBTITLES_OFF),
                ) + tv.core.subtitles.subtitleLanguages(languages, settings.pinnedLanguages).map {
                    PanelOption(it.code, it.vernacular, if (it.code in settings.mostUsedLanguages) mostUsed else null, it.code == settings.subtitleLanguage)
                },
                onPick = { o -> repo.setSubtitleLanguage(o.key.takeUnless { it == "__same" }); panel = null },
                onDismiss = { panel = null },
            )
        }
        SettingsPanel.Stream -> TvSidePanel(
            stringResource(R.string.settings_streaming_quality),
            options = listOf(PanelOption(null, stringResource(R.string.common_quality_auto), stringResource(R.string.common_quality_auto_detail), settings.streamQuality == null)) +
                QUALITIES.map { PanelOption(it, it, selected = settings.streamQuality == it) },
            onPick = { o -> repo.update { it.copy(streamQuality = o.key) }; panel = null },
            onDismiss = { panel = null },
        )
        SettingsPanel.Download -> TvSidePanel(
            stringResource(R.string.settings_download_quality), stringResource(R.string.settings_download_quality_note),
            options = listOf(PanelOption(null, stringResource(R.string.settings_ask_every_time), selected = settings.downloadQuality == null)) +
                QUALITIES.map { PanelOption(it, it, selected = settings.downloadQuality == it) },
            onPick = { o -> repo.update { it.copy(downloadQuality = o.key) }; panel = null },
            onDismiss = { panel = null },
        )
        null -> Unit
    }
}

@Composable
private fun SettingRow(title: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, modifier = modifier.fillMaxWidth(0.7f).padding(vertical = 5.dp), scale = 1.02f) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium) }
            Text(value, color = Tv.TextSecondary)
        }
    }
}
