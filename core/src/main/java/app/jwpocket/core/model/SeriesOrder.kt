package app.jwpocket.core.model

/**
 * Parts of a series, so Up next after "Part 1" is "Part 2" whatever order the
 * row was in. Works in every language without word lists:
 *
 * - Titles that differ only in one number are parts of one series:
 *   "…—Part 1" / "…—Part 2", "… (1. časť)", "…—Part I" / "…—Part II",
 *   "Governing Body Update #5" / "#6".
 * - Numbered episodes of one publication whose titles carry that number:
 *   "pub-gnj_1_VIDEO" "Episode 1: …", "pub-gnj_2_VIDEO" "Episode 2: …".
 */
object SeriesOrder {

    /** The parts of [video]'s series found in [pool] that come after it, in order. */
    fun nextParts(video: Video, pool: List<Video>): List<Video> {
        val own = marks(video)
        if (own.isEmpty()) return emptyList()
        val candidates = pool.asSequence().filter { it.id != video.id }.distinctBy { it.id }.map { it to marks(it) }.toList()
        // The series this video belongs to with the most members.
        return own.map { (series, number) ->
            candidates.mapNotNull { (other, otherMarks) ->
                otherMarks.firstOrNull { it.series == series }?.number?.takeIf { it > number }?.let { other to it }
            }
                .groupBy { it.second }.map { (_, same) -> same.first() } // one video per part number
                .sortedBy { it.second }
                .map { it.first }
        }.maxByOrNull { it.size }.orEmpty()
    }

    /** Whether [video] looks like part of a series at all (no need to look further otherwise). */
    fun mayBePart(video: Video): Boolean = marks(video).isNotEmpty()

    private data class Mark(val series: String, val number: Int)

    private fun marks(video: Video): List<Mark> {
        val title = video.title.replace("\u200B", "")
        val numbers = NUMBER.findAll(title).mapNotNull { match ->
            val token = match.value
            val n = token.toIntOrNull() ?: ROMAN[token] ?: return@mapNotNull null
            Mark("t:" + title.substring(0, match.range.first) + "#" + title.substring(match.range.last + 1), n)
        }.toList()
        val key = video.languageAgnosticNaturalKey?.let(EPISODE_KEY::matchEntire)
        val episode = key?.let { Mark("k:" + it.groupValues[1], it.groupValues[2].toInt()) }
            ?.takeIf { e -> numbers.any { it.number == e.number } }
        return numbers + listOfNotNull(episode)
    }

    /** A standalone number of up to three digits, or a Roman numeral up to X. */
    private val NUMBER = Regex("(?<![\\p{L}\\p{N}])(\\d{1,3}|[IVX]{1,4})(?![\\p{L}\\p{N}])")
    private val EPISODE_KEY = Regex("pub-(.+)_(\\d+)_VIDEO")
    private val ROMAN = mapOf("I" to 1, "II" to 2, "III" to 3, "IV" to 4, "V" to 5, "VI" to 6, "VII" to 7, "VIII" to 8, "IX" to 9, "X" to 10)
}
