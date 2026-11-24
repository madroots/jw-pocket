package app.jwpocket.core.data

import java.io.Closeable
import java.io.File
import java.util.zip.ZipFile

/**
 * The verses of the Bible's EPUB as published on jw.org, to show the words
 * while a chapter is read aloud.
 *
 * Layout of the book: biblebooknav.xhtml links each book to its chapter list
 * (biblechapternav<book>.xhtml), or straight to the text for one-chapter
 * books; each verse starts with an empty <span id="chapter<c>_verse<v>">.
 * A chapter may run over several files, so its text is read from its first
 * file up to the next chapter's.
 */
class BibleText(file: File) : Closeable {

    private val zip = ZipFile(file)
    private val root: String = zip.entries().asSequence().map { it.name }.firstOrNull { it.endsWith("biblebooknav.xhtml") }
        ?.substringBeforeLast("biblebooknav.xhtml").orEmpty()

    /** Files in reading order (the spine of the package). */
    private val spine: List<String> by lazy {
        val opfName = zip.entries().asSequence().map { it.name }.first { it.endsWith(".opf") }
        val opf = read(opfName) ?: return@lazy emptyList()
        val hrefById = HashMap<String, String>()
        ITEM.findAll(opf).forEach { m ->
            val tag = m.value
            val id = ID.find(tag)?.groupValues?.get(1) ?: return@forEach
            val href = HREF.find(tag)?.groupValues?.get(1) ?: return@forEach
            hrefById[id] = href
        }
        ITEMREF.findAll(opf).mapNotNull { hrefById[it.groupValues[1]] }.toList()
    }
    private val position: Map<String, Int> by lazy { spine.withIndex().associate { (i, f) -> f to i } }

    /** Book number (1-66) -> the file each chapter starts in. */
    private val books: List<String> by lazy {
        BOOK_LINK.findAll(read(root + "biblebooknav.xhtml").orEmpty()).map { it.groupValues[1] }.toList()
    }
    private val chapterFiles = HashMap<Int, Map<Int, String>>()
    private val cache = object : LinkedHashMap<String, Map<Int, String>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Map<Int, String>>) = size > 12
    }

    /** Verse number -> its words, for [chapter] of [book]; empty when the book has no such chapter. */
    @Synchronized
    fun verses(book: Int, chapter: Int): Map<Int, String> = cache.getOrPut("$book:$chapter") {
        val files = chapterFilesOf(book)
        val start = files[chapter] ?: return@getOrPut emptyMap()
        val from = position[start] ?: return@getOrPut emptyMap()
        val to = files[chapter + 1]?.let { position[it] } ?: (from + LAST_CHAPTER_FILES)
        val text = (from..minOf(to, spine.lastIndex)).joinToString("") { read(root + spine[it]).orEmpty() }
        parse(text, chapter)
    }

    private fun chapterFilesOf(book: Int): Map<Int, String> = chapterFiles.getOrPut(book) {
        val href = books.getOrNull(book - 1)?.substringBefore('#') ?: return@getOrPut emptyMap()
        if (!href.startsWith("biblechapternav")) return@getOrPut mapOf(1 to href) // one-chapter book
        CHAPTER_LINK.findAll(read(root + href).orEmpty())
            .associate { it.groupValues[2].toInt() to it.groupValues[1] }
    }

    private fun read(name: String): String? = zip.getEntry(name)?.let { entry -> zip.getInputStream(entry).use { it.readBytes().decodeToString() } }

    override fun close() = zip.close()

    companion object {
        /** Files read past a book's last chapter start: enough for the longest chapter. */
        private const val LAST_CHAPTER_FILES = 3

        private val ITEM = Regex("<item\\b[^>]*>")
        private val ITEMREF = Regex("<itemref\\b[^>]*idref=\"([^\"]+)\"")
        private val ID = Regex("\\bid=\"([^\"]+)\"")
        private val HREF = Regex("\\bhref=\"([^\"]+)\"")
        private val BOOK_LINK = Regex("<a href=\"([^\"]+)\">")
        private val CHAPTER_LINK = Regex("<a[^>]*href=\"([^\"#]+)(?:#[^\"]*)?\"[^>]*>\\s*(\\d+)\\s*</a>")
        private val VERSE = Regex("<span id=\"chapter(\\d+)_verse(\\d+)\"></span>")
        private val END = Regex("<(?:aside|div[^>]*footnote|section[^>]*footnote|/body)")
        private val NOTE_REF = Regex("<a[^>]*epub:type=\"noteref\"[^>]*>.*?</a>", RegexOption.DOT_MATCHES_ALL)
        private val CHAPTER_NUMBER = Regex("<span class=\"w_ch\">.*?</span>", RegexOption.DOT_MATCHES_ALL)
        private val SUP = Regex("<sup>.*?</sup>", RegexOption.DOT_MATCHES_ALL)
        private val TAG = Regex("<[^>]+>")
        private val SPACE = Regex("\\s+")
        private val ENTITY = Regex("&(#x[0-9a-fA-F]+|#\\d+|[a-zA-Z]+);")

        /** The verses of [chapter] in [text]; stops where numbering starts over (the next book). */
        internal fun parse(text: String, chapter: Int): Map<Int, String> {
            val marks = ArrayList<MatchResult>()
            for (m in VERSE.findAll(text)) {
                if (m.groupValues[1].toInt() != chapter) continue
                if (marks.isNotEmpty() && m.groupValues[2].toInt() <= marks.last().groupValues[2].toInt()) break
                marks += m
            }
            val verses = LinkedHashMap<Int, String>()
            marks.forEachIndexed { i, m ->
                var segment = text.substring(m.range.last + 1, if (i + 1 < marks.size) marks[i + 1].range.first else text.length)
                VERSE.find(segment)?.let { segment = segment.substring(0, it.range.first) } // another chapter begins
                END.find(segment)?.let { segment = segment.substring(0, it.range.first) }  // footnotes
                verses[m.groupValues[2].toInt()] = clean(segment)
            }
            return verses
        }

        private fun clean(html: String): String {
            val text = html.replace(NOTE_REF, "").replace(CHAPTER_NUMBER, "").replace(SUP, "").replace(TAG, " ")
            return decode(text).replace(SPACE, " ").trim()
        }

        private fun decode(text: String): String = ENTITY.replace(text) { m ->
            val e = m.groupValues[1]
            when {
                e.startsWith("#x") -> e.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
                e.startsWith("#") -> e.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
                e == "nbsp" -> " "
                e == "amp" -> "&"
                e == "lt" -> "<"
                e == "gt" -> ">"
                e == "quot" -> "\""
                e == "apos" -> "'"
                else -> m.value
            }
        }
    }
}
