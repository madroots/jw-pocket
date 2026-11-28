package app.jwpocket.phone.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.jwpocket.core.data.LibraryRepository
import app.jwpocket.core.data.ListenSaver
import app.jwpocket.core.data.Settings
import app.jwpocket.core.model.Language
import app.jwpocket.core.util.AppLanguage
import app.jwpocket.phone.BuildConfig
import app.jwpocket.phone.R
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
    onOpenBibleLanguage: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val pocket = LocalPocket.current
    val settings by pocket.core.settings.settings.collectAsState()
    val repo = pocket.core.settings
    var pick by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // New-video switches show as on only while the system lets the app notify (re-read on return here).
    var notificationsAllowed by remember { mutableStateOf(canNotify(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { notificationsAllowed = canNotify(context) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsAllowed = canNotify(context) }
    // Turning one on keeps the choice and asks: the Android 13+ prompt, or the app's
    // notification settings when notifications were turned off there. Refused: it simply stays off.
    fun setNotify(on: Boolean, change: (Settings) -> Settings) {
        repo.update(change)
        if (!on || notificationsAllowed) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (Build.VERSION.SDK_INT >= 26) {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        // Defaults for everything; what's playing can be switched just for itself (in the player).
        // A row that follows the one above shows that language dimmed until something else is picked.
        item { Group(stringResource(R.string.settings_group_language)) }
        item {
            Text(
                stringResource(R.string.settings_language_note), style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
            )
        }
        item { Entry(stringResource(R.string.settings_app_language), appLanguageLabel(settings.appLanguage), inherited = settings.appLanguage == null) { pick = "app" } }
        item { Entry(stringResource(R.string.settings_video_language), settings.languageName, onClick = onOpenLanguage) }
        item { Entry(stringResource(R.string.settings_subtitle_language), subtitleLabel(settings, pocket.core.languages::name), inherited = settings.subtitleLanguage == null, onClick = onOpenSubtitles) }
        item { Entry(stringResource(R.string.bible_language_title), settings.bibleLanguage?.let(pocket.core.languages::name) ?: settings.languageName, inherited = settings.bibleLanguage == null, onClick = onOpenBibleLanguage) }

        item { Group(stringResource(R.string.settings_group_playback)) }
        item { Toggle(stringResource(R.string.settings_background_title), stringResource(R.string.settings_background_detail), settings.backgroundPlayback) { on -> repo.update { it.copy(backgroundPlayback = on) } } }
        item { Toggle(stringResource(R.string.settings_pip_title), stringResource(R.string.settings_pip_detail), settings.autoPictureInPicture) { on -> repo.update { it.copy(autoPictureInPicture = on) } } }
        item { Toggle(stringResource(R.string.settings_autoplay_title), stringResource(R.string.settings_autoplay_detail), settings.autoplayNext) { on -> repo.update { it.copy(autoplayNext = on) } } }
        item { Entry(stringResource(R.string.settings_streaming_quality), settings.streamQuality ?: stringResource(R.string.quality_auto)) { pick = "stream" } }
        item { Entry(stringResource(R.string.listen_saver_title), listenSaverLabel(settings.listenSaver)) { pick = "listen" } }

        item { Group(stringResource(R.string.settings_group_subtitles)) }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.settings_subtitle_size), style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value = settings.subtitleScale,
                    onValueChange = { v -> repo.update { it.copy(subtitleScale = v) } },
                    valueRange = 0.75f..1.75f,
                    steps = 3,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Pocket.Accent),
                )
            }
        }

        item { Group(stringResource(R.string.settings_group_downloads)) }
        item { Entry(stringResource(R.string.settings_download_quality), settings.downloadQuality ?: stringResource(R.string.quality_ask_every_time)) { pick = "download" } }
        item { Toggle(stringResource(R.string.settings_wifi_only_title), stringResource(R.string.settings_wifi_only_detail), settings.wifiOnlyDownloads) { on -> repo.update { it.copy(wifiOnlyDownloads = on) } } }
        item { Entry(stringResource(R.string.settings_manage_downloads_title), stringResource(R.string.settings_manage_downloads_detail), onClick = onOpenDownloads) }

        item { Group(stringResource(R.string.settings_group_notifications)) }
        item {
            Toggle(stringResource(R.string.settings_notify_broadcasting_title), stringResource(R.string.settings_notify_broadcasting_detail), settings.notifyBroadcasting && notificationsAllowed) { on ->
                setNotify(on) { it.copy(notifyBroadcasting = on) }
            }
        }
        item {
            Toggle(stringResource(R.string.settings_notify_news_title), stringResource(R.string.settings_notify_news_detail), settings.notifyNews && notificationsAllowed) { on ->
                setNotify(on) { it.copy(notifyNews = on) }
            }
        }

        item { Group(stringResource(R.string.settings_group_app)) }
        item { Entry(stringResource(R.string.settings_check_updates), stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)) { pocket.core.updates.check(manual = true) } }
        item {
            Text(
                stringResource(R.string.settings_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = Pocket.TextMuted,
                modifier = Modifier.padding(20.dp),
            )
        }
    }

    when (pick) {
        "app" -> AppLanguageSheet(settings.appLanguage, onDismiss = { pick = null })
        "stream" -> ChoiceSheet(stringResource(R.string.settings_streaming_quality), stringResource(R.string.quality_streaming_note), { pick = null }) {
            item { ChoiceRow(stringResource(R.string.quality_auto), settings.streamQuality == null, stringResource(R.string.quality_auto_detail)) { repo.update { it.copy(streamQuality = null) }; pick = null } }
            items(QUALITIES) { q -> ChoiceRow(q, settings.streamQuality == q) { repo.update { it.copy(streamQuality = q) }; pick = null } }
        }
        "download" -> ChoiceSheet(stringResource(R.string.settings_download_quality), stringResource(R.string.quality_download_note), { pick = null }) {
            item { ChoiceRow(stringResource(R.string.quality_ask_every_time), settings.downloadQuality == null) { repo.update { it.copy(downloadQuality = null) }; pick = null } }
            items(QUALITIES) { q -> ChoiceRow(q, settings.downloadQuality == q) { repo.update { it.copy(downloadQuality = q) }; pick = null } }
        }
        "listen" -> ChoiceSheet(stringResource(R.string.listen_saver_title), stringResource(R.string.listen_saver_note), { pick = null }) {
            items(listOf(ListenSaver.MobileData, ListenSaver.Always, ListenSaver.Off)) { option ->
                ChoiceRow(listenSaverLabel(option), settings.listenSaver == option) { repo.update { it.copy(listenSaver = option) }; pick = null }
            }
        }
    }
}

@Composable
private fun listenSaverLabel(saver: ListenSaver): String = stringResource(
    when (saver) {
        ListenSaver.MobileData -> R.string.listen_saver_mobile
        ListenSaver.Always -> R.string.listen_saver_always
        ListenSaver.Off -> R.string.listen_saver_off
    }
)

@Composable
private fun appLanguageLabel(chosen: String?): String =
    chosen?.let(AppLanguage::name) ?: stringResource(R.string.settings_device_language, AppLanguage.name(AppLanguage.effective(null)))

/**
 * App language: the device's or one the app is translated into. If the
 * library has videos in the new language, asks whether videos (and their
 * subtitles) should follow; then restarts the screen in the new language.
 */
@Composable
private fun AppLanguageSheet(chosen: String?, onDismiss: () -> Unit) {
    val pocket = LocalPocket.current
    val activity = LocalContext.current as? Activity
    var asking by remember { mutableStateOf<Pair<String?, Language>?>(null) }
    fun apply(tag: String?, videos: Language?, subtitlesToo: Boolean) {
        pocket.core.settings.setAppLanguage(tag)
        if (videos != null) {
            pocket.core.settings.setLanguage(videos.code, videos.vernacular)
            if (subtitlesToo) pocket.core.settings.setSubtitleLanguage(null)
            pocket.core.library.clearMemory()
            pocket.core.languages.load(videos.code)
        }
        onDismiss()
        activity?.recreate()
    }
    fun pick(tag: String?) {
        val match = pocket.core.languages.forLocale(AppLanguage.effective(tag))
        if (match != null && match.code != pocket.core.settings.current.languageCode) asking = tag to match else apply(tag, null, false)
    }

    val pending = asking
    if (pending == null) {
        ChoiceSheet(stringResource(R.string.settings_app_language), onDismiss = onDismiss) {
            item { ChoiceRow(stringResource(R.string.settings_device_language, AppLanguage.name(AppLanguage.effective(null))), chosen == null) { pick(null) } }
            items(AppLanguage.supported) { (tag, name) -> ChoiceRow(name, chosen == tag) { pick(tag) } }
        }
    } else {
        val (tag, videos) = pending
        AlertDialog(
            onDismissRequest = { apply(tag, null, false) },
            title = { Text(stringResource(R.string.cascade_app_title, videos.vernacular)) },
            text = { Text(stringResource(R.string.cascade_app_text, videos.vernacular)) },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { apply(tag, videos, subtitlesToo = true) }) { Text(stringResource(R.string.cascade_app_yes), color = Pocket.Accent) }
                    TextButton(onClick = { apply(tag, videos, subtitlesToo = false) }) { Text(stringResource(R.string.cascade_app_videos_only)) }
                    TextButton(onClick = { apply(tag, null, false) }) { Text(stringResource(R.string.cascade_app_no)) }
                }
            },
            containerColor = Pocket.SurfaceHigh,
        )
    }
}

/** The default subtitles; following the videos shows their language. */
@Composable
private fun subtitleLabel(settings: Settings, name: (String) -> String): String = when (val s = settings.subtitleLanguage) {
    null -> settings.languageName
    Settings.SUBTITLES_OFF -> stringResource(R.string.subtitles_off)
    else -> name(s)
}

/** Whether the app may post notifications: allowed in system settings and, on Android 13+, permitted. */
private fun canNotify(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

@Composable
private fun Group(title: String) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = Pocket.Accent, modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 4.dp))
}

/** [inherited]: the value follows another setting, shown dimmed until something else is picked. */
@Composable
private fun Entry(title: String, value: String, inherited: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodySmall, color = if (inherited) Pocket.TextMuted else Pocket.TextSecondary)
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
                placeholder = { Text(stringResource(R.string.search_languages)) },
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

/** Default video (and audio) language. Subtitles and the Bible that follow it change with it; languages picked for them stay. */
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

/** Bible language: the video language unless another is picked (Bible tab, reading and its text). */
@Composable
fun BibleLanguageScreen(contentPadding: PaddingValues, onDone: () -> Unit) {
    val pocket = LocalPocket.current
    val settings by pocket.core.settings.settings.collectAsState()
    val languages by pocket.core.languages.languages.collectAsState()
    val list = remember(languages) { languages.filterNot { it.isSignLanguage } }
    LanguageList(
        contentPadding, list, settings.bibleLanguage,
        header = {
            ChoiceRow(stringResource(R.string.language_follow_videos, settings.languageName), settings.bibleLanguage == null) {
                pocket.core.settings.setBibleLanguage(null); onDone()
            }
        },
    ) { lang ->
        pocket.core.settings.setBibleLanguage(lang.code)
        onDone()
    }
}

@Composable
fun SubtitleLanguageScreen(contentPadding: PaddingValues, onDone: () -> Unit) {
    val pocket = LocalPocket.current
    val settings by pocket.core.settings.settings.collectAsState()
    val languages by pocket.core.languages.languages.collectAsState()
    val list = remember(languages, settings.pinnedLanguages) { pocket.core.subtitles.subtitleLanguages(languages, settings.pinnedLanguages) }
    LanguageList(
        contentPadding, list, settings.subtitleLanguage,
        header = {
            Column {
                ChoiceRow(stringResource(R.string.language_follow_videos, settings.languageName), settings.subtitleLanguage == null) { pocket.core.settings.setSubtitleLanguage(null); onDone() }
                ChoiceRow(stringResource(R.string.subtitles_off), settings.subtitleLanguage == Settings.SUBTITLES_OFF) { pocket.core.settings.setSubtitleLanguage(Settings.SUBTITLES_OFF); onDone() }
                Text(
                    stringResource(R.string.subtitles_any_language_note),
                    style = MaterialTheme.typography.bodySmall, color = Pocket.TextSecondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        },
    ) { lang ->
        pocket.core.settings.setSubtitleLanguage(lang.code)
        onDone()
    }
}
