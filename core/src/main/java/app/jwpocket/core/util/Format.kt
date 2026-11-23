package app.jwpocket.core.util

import java.util.Locale

/** "1:02:03" or "4:05". */
fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.ROOT, "%d:%02d", m, s)
}

/** Duration chip text from seconds; empty when unknown. */
fun formatDuration(seconds: Double?): String =
    if (seconds == null || seconds <= 0) "" else formatClock((seconds * 1000).toLong())

/** "12 min left", "1 h 5 min left". */
fun formatTimeLeft(remainingMs: Long): String {
    val minutes = remainingMs / 60_000
    return when {
        minutes < 1 -> "Less than a minute left"
        minutes < 60 -> "$minutes min left"
        else -> "${minutes / 60} h ${minutes % 60} min left"
    }
}

/** "512 KB", "34.2 MB", "1.5 GB". */
fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "0 MB"
    bytes >= 1L shl 30 -> String.format(Locale.ROOT, "%.1f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(Locale.ROOT, "%.1f MB", bytes / (1L shl 20).toDouble())
    else -> "${(bytes + 1023) / 1024} KB"
}

/** "HD", "4K" or "" from the highest frame height. */
fun qualityBadge(maxHeight: Int): String = when {
    maxHeight >= 2160 -> "4K"
    maxHeight >= 720 -> "HD"
    else -> ""
}
