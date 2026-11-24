package app.jwpocket.core.util

/** One subtitle line on screen from [startMs] to [endMs]. */
data class Cue(val startMs: Long, val endMs: Long, val text: String)

/** Reads the WebVTT subtitle files jw.org publishes (cue timings, text, simple tags). */
object WebVtt {

    fun parse(vtt: String): List<Cue> {
        val cues = ArrayList<Cue>()
        val lines = vtt.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        var i = 0
        while (i < lines.size) {
            val timing = TIMING.find(lines[i])
            if (timing == null) { i++; continue }
            val start = time(timing.groupValues[1])
            val end = time(timing.groupValues[2])
            i++
            val text = StringBuilder()
            while (i < lines.size && lines[i].isNotBlank()) {
                if (text.isNotEmpty()) text.append('\n')
                text.append(clean(lines[i]))
                i++
            }
            if (start != null && end != null && text.isNotBlank()) cues += Cue(start, end, text.toString().trim())
        }
        return cues.sortedBy { it.startMs }
    }

    /** "01:02:03.456" or "02:03.456" to milliseconds. */
    private fun time(value: String): Long? {
        val parts = value.split(':')
        val seconds = parts.last().replace(',', '.').toDoubleOrNull() ?: return null
        val minutes = parts.getOrNull(parts.size - 2)?.toLongOrNull() ?: 0
        val hours = if (parts.size == 3) parts[0].toLongOrNull() ?: return null else 0
        return ((hours * 3600 + minutes * 60) * 1000 + seconds * 1000).toLong()
    }

    /** Drops tags (<i>, <c.color>, <00:01.000>) and decodes the entities WebVTT allows. */
    private fun clean(line: String): String =
        line.replace(TAG, "")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").replace("&lrm;", "").replace("&rlm;", "")
            .replace("&amp;", "&")
            .trim()

    private val TIMING = Regex("((?:\\d+:)?\\d{1,2}:\\d{2}[.,]\\d{1,3})\\s*-->\\s*((?:\\d+:)?\\d{1,2}:\\d{2}[.,]\\d{1,3})")
    private val TAG = Regex("<[^>]*>")
}
