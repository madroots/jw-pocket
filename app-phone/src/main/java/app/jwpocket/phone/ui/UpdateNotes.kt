package app.jwpocket.phone.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.jwpocket.core.data.AppUpdate
import app.jwpocket.core.util.formatBytes
import app.jwpocket.phone.R
import app.jwpocket.phone.ui.theme.Pocket

/**
 * What changed since the installed version: each version's changes as
 * bullets under its number, at most half the screen tall and scrolling, so a
 * long list never pushes the buttons away.
 */
@Composable
internal fun UpdateNotes(update: AppUpdate) {
    if (update.releases.isEmpty()) {
        Text(stringResource(R.string.update_available_message, formatBytes(update.sizeBytes)))
        return
    }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
    Column(Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState())) {
        update.releases.forEachIndexed { i, release ->
            if (update.releases.size > 1) {
                Text(
                    release.version, style = MaterialTheme.typography.titleSmall, color = Pocket.Accent,
                    modifier = Modifier.padding(top = if (i == 0) 0.dp else 14.dp, bottom = 4.dp),
                )
            }
            release.changes.forEach { change ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text("•", color = Pocket.TextSecondary, modifier = Modifier.width(14.dp))
                    Text(change, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
