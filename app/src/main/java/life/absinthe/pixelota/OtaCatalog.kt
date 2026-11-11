package life.absinthe.pixelota

import org.jsoup.Jsoup
import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Parses Google's published full OTA links; never constructs a download URL. */
object OtaCatalog {
    const val VERSIONS = OtaUrls.VERSIONS
    const val STABLE = OtaUrls.STABLE
    private val FILE = Regex("^([a-z0-9]+)(_beta)?-ota-([a-z0-9.]+)-([a-f0-9]{8})\\.zip$")
    private val HASH = Regex("(?i)\\b[a-f0-9]{64}\\b")
    private val VERSION = Regex("^/about/versions/(\\d+)$")
    private val PAGE = Regex("^/about/versions/\\d+(?:/qpr\\d+)?(?:/download-ota(?:-qpr)?|/get)?$")
    private val RELEASE_DATE_FORMAT = DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.US)

    class Entry(
        @JvmField val device: String,
        @JvmField val build: String,
        @JvmField val url: String,
        @JvmField val sha256: String,
        @JvmField val source: String,
        @JvmField val date: LocalDate?,
        @JvmField val beta: Boolean,
    ) {
        fun filename(): String = URI.create(url).path.replaceFirst(Regex(".*/"), "")
    }

    fun parse(html: String, source: String): List<Entry> {
        val doc = Jsoup.parse(html, source)
        var date: LocalDate? = null
        for (row in doc.select("tr")) {
            val cells = row.select("td")
            if (cells.size > 1 && cells.first()?.text().equals("Release date", ignoreCase = true)) {
                try {
                    date = LocalDate.parse(cells[1].text(), RELEASE_DATE_FORMAT)
                } catch (_: DateTimeParseException) {
                    // Unknown date must not win latest selection.
                }
                break
            }
        }
        val entries = mutableListOf<Entry>()
        val seen = linkedSetOf<String>()
        for (link in doc.select("a[href]")) {
            val url = link.absUrl("href")
            val uri = try {
                URI.create(url)
            } catch (_: IllegalArgumentException) {
                continue
            }
            if (uri.scheme != "https" ||
                uri.host != OtaUrls.DL_GOOGLE_HOST ||
                uri.userInfo != null ||
                uri.port != -1 ||
                uri.query != null
            ) {
                continue
            }
            val filename = uri.path.replaceFirst(Regex(".*/"), "")
            val fileMatch = FILE.matchEntire(filename) ?: continue
            if (!seen.add(url)) continue
            val device = fileMatch.groupValues[1]
            val row = link.closest("tr") ?: doc.select("tr[id]").firstOrNull { candidate ->
                candidate.id() == device && candidate.text().contains(fileMatch.value)
            } ?: continue
            val hashMatch = HASH.find(row.text()) ?: continue
            val checksum = hashMatch.value.lowercase(Locale.ROOT)
            if (!checksum.startsWith(fileMatch.groupValues[4])) continue
            entries.add(
                Entry(
                    device = device,
                    build = fileMatch.groupValues[3].uppercase(Locale.ROOT),
                    url = url,
                    sha256 = checksum,
                    source = source,
                    date = date,
                    beta = fileMatch.groups[2] != null,
                ),
            )
        }
        return entries
    }

    fun versions(html: String): List<Int> {
        val numbers = linkedSetOf<Int>()
        for (link in Jsoup.parse(html, VERSIONS).select("a[href]")) {
            val uri = try {
                URI.create(link.absUrl("href"))
            } catch (_: IllegalArgumentException) {
                continue
            }
            val versionMatch = VERSION.matchEntire(uri.path ?: "")
            if (uri.host == OtaUrls.DEVELOPER_ANDROID_HOST && versionMatch != null) {
                numbers.add(versionMatch.groupValues[1].toInt())
            }
        }
        return numbers.sortedDescending().take(2)
    }

    fun pages(html: String, source: String, majors: Set<Int>): Set<String> {
        val pages = linkedSetOf<String>()
        for (link in Jsoup.parse(html, source).select("a[href]")) {
            val uri = try {
                URI.create(link.absUrl("href"))
            } catch (_: IllegalArgumentException) {
                continue
            }
            val path = uri.path
            if (uri.scheme != "https" ||
                uri.host != OtaUrls.DEVELOPER_ANDROID_HOST ||
                path == null ||
                !PAGE.matches(path)
            ) {
                continue
            }
            val major = path.split('/')[3].toInt()
            if (majors.contains(major)) {
                pages.add(OtaUrls.developerPage(path))
            }
        }
        return pages
    }

    fun current(entries: List<Entry>, device: String, build: String): Entry? =
        entries.firstOrNull { it.device == device && it.build.equals(build, ignoreCase = true) }

    fun latest(entries: List<Entry>, device: String): Entry? =
        // A published release date, rather than lexicographic Build ID order, decides the latest beta.
        entries.asSequence()
            .filter { it.beta && it.device == device && it.date != null }
            .maxWithOrNull(compareBy<Entry> { it.date }.thenBy { it.build })
}
