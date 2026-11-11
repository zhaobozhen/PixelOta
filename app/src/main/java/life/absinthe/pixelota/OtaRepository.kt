package life.absinthe.pixelota

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.util.Properties
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class OtaRepository {
    sealed interface FetchError {
        data object NonOfficialRedirect : FetchError
        data object MissingLocation : FetchError
        data object Cancelled : FetchError
        data object PageTooLarge : FetchError
        data object TooManyRedirects : FetchError
        data class HttpStatus(val code: Int) : FetchError
        data class Other(val message: String) : FetchError
    }

    class FetchException(val error: FetchError) : IOException(error.toString())

    sealed interface Warning {
        data object NoBetaEntry : Warning
        data object PendingSources : Warning
        data class EmptyPage(val url: String) : Warning
        data object NoDeviceBeta : Warning
        data object CacheReadFailed : Warning
        data object CacheWriteFailed : Warning
        data class FetchFailed(val source: String, val error: FetchError) : Warning
    }

    class Result {
        @JvmField var current: OtaCatalog.Entry? = null
        @JvmField var latest: OtaCatalog.Entry? = null
        @JvmField var cachedCurrent = false
        @JvmField var betaComplete = true
        @JvmField val warnings: MutableList<Warning> = mutableListOf()
    }

    @Throws(InterruptedException::class)
    fun query(device: String, build: String, cache: File): Result {
        val result = Result()
        val live = mutableListOf<OtaCatalog.Entry>()
        val visited = linkedSetOf<String>()
        val pool = Executors.newFixedThreadPool(4)
        try {
            val initial = fetchBatch(pool, linkedSetOf(OtaUrls.VERSIONS, OtaUrls.STABLE), result).toMutableMap()
            val index = initial[OtaUrls.VERSIONS]
            if (index == null) result.betaComplete = false
            val majors = linkedSetOf<Int>().apply {
                if (index != null) addAll(OtaCatalog.versions(index))
            }
            if (majors.isEmpty()) {
                result.betaComplete = false
                result.warnings.add(Warning.NoBetaEntry)
            }
            var pending = linkedSetOf<String>()
            for (major in majors) pending.add(OtaUrls.version(major))
            val probes = linkedSetOf<String>()
            for (major in majors) {
                probes.add(OtaUrls.versionDownloadOta(major))
                probes.add(OtaUrls.versionDownloadOtaQpr(major))
                for (qpr in 1..3) {
                    probes.add(OtaUrls.versionQprDownloadOta(major, qpr))
                }
            }
            // New QPR pages can be published before Google's navigation links are updated.
            initial.putAll(fetchBatch(pool, probes, result, optional = true))
            visited.addAll(probes)
            for ((key, value) in initial) {
                pending.addAll(OtaCatalog.pages(value, key, majors))
            }
            pending.removeAll(visited)
            // shortcut: discover the two newest Android generations, add an archive service if full history is needed.
            for (wave in 0 until 4) {
                if (pending.isEmpty()) break
                visited.addAll(pending)
                val fetched = fetchBatch(pool, pending, result)
                initial.putAll(fetched)
                pending = linkedSetOf()
                for ((key, value) in fetched) {
                    pending.addAll(OtaCatalog.pages(value, key, majors))
                }
                pending.removeAll(visited)
            }
            if (pending.isNotEmpty()) {
                result.betaComplete = false
                result.warnings.add(Warning.PendingSources)
            }
            for ((key, value) in initial) {
                val parsed = OtaCatalog.parse(value, key)
                if ((key == OtaUrls.STABLE || key.contains("download-ota")) && parsed.isEmpty()) {
                    result.warnings.add(Warning.EmptyPage(key))
                    if (key != OtaUrls.STABLE) result.betaComplete = false
                }
                live.addAll(parsed)
            }
        } finally {
            pool.shutdownNow()
        }
        result.latest = OtaCatalog.latest(live, device)
        if (result.latest == null && result.betaComplete) {
            result.warnings.add(Warning.NoDeviceBeta)
        }
        result.current = OtaCatalog.current(live, device, build)
        val saved = try {
            readCache(cache)
        } catch (_: IOException) {
            result.warnings.add(Warning.CacheReadFailed)
            emptyList()
        }
        if (result.current == null) {
            result.current = OtaCatalog.current(saved, device, build)
            result.cachedCurrent = result.current != null
        }
        val merged = linkedMapOf<String, OtaCatalog.Entry>()
        for (entry in saved) merged[entry.url] = entry
        for (entry in live) merged[entry.url] = entry
        try {
            writeCache(cache, merged.values.toList())
        } catch (_: IOException) {
            result.warnings.add(Warning.CacheWriteFailed)
        }
        return result
    }

    @Throws(InterruptedException::class)
    private fun fetchBatch(
        pool: ExecutorService,
        urls: Set<String>,
        result: Result,
        optional: Boolean = false,
    ): Map<String, String> {
        val sources = urls.toList()
        val jobs = sources.map { source -> Callable { fetch(source) } }
        val futures = pool.invokeAll(jobs)
        val pages = linkedMapOf<String, String>()
        for (i in sources.indices) {
            val source = sources[i]
            try {
                pages[source] = futures[i].get()
            } catch (ex: ExecutionException) {
                val cause = ex.cause
                if (optional && cause is FileNotFoundException) continue
                val error = when (cause) {
                    is FetchException -> cause.error
                    else -> FetchError.Other(cause?.message ?: ex.message.orEmpty())
                }
                result.warnings.add(Warning.FetchFailed(source, error))
                if (source != OtaUrls.STABLE) result.betaComplete = false
            }
        }
        return pages
    }

    companion object {
        @Throws(IOException::class)
        fun fetch(source: String): String {
            var uri = URI.create(source + (if (source.contains("?")) "&" else "?") + "hl=en")
            for (redirects in 0 until 5) {
                if (uri.scheme != "https" ||
                    !OtaUrls.isOfficialPageHost(uri.host)
                ) {
                    throw FetchException(FetchError.NonOfficialRedirect)
                }
                val connection = uri.toURL().openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 12000
                connection.readTimeout = 20000
                connection.setRequestProperty("User-Agent", "PixelOTA/0.1 (Android; full OTA link finder)")
                connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                if (uri.host == OtaUrls.DEVELOPERS_GOOGLE_HOST) {
                    connection.setRequestProperty("Cookie", "devsite_wall_acks=nexus-ota-tos")
                }
                try {
                    val status = connection.responseCode
                    if (status in 300..399) {
                        val location = connection.getHeaderField("Location")
                            ?: throw FetchException(FetchError.MissingLocation)
                        uri = uri.resolve(location)
                        continue
                    }
                    if (status == 404 || status == 410) throw FileNotFoundException("HTTP $status")
                    if (status != 200) throw FetchException(FetchError.HttpStatus(status))
                    connection.inputStream.use { input ->
                        val bytes = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var count: Int
                        while (input.read(buffer).also { count = it } != -1) {
                            if (Thread.currentThread().isInterrupted) {
                                throw FetchException(FetchError.Cancelled)
                            }
                            if (bytes.size() + count > 4 * 1024 * 1024) {
                                throw FetchException(FetchError.PageTooLarge)
                            }
                            bytes.write(buffer, 0, count)
                        }
                        return bytes.toString(StandardCharsets.UTF_8.name())
                    }
                } finally {
                    connection.disconnect()
                }
            }
            throw FetchException(FetchError.TooManyRedirects)
        }

        @Throws(IOException::class)
        internal fun readCache(file: File): List<OtaCatalog.Entry> {
            val entries = mutableListOf<OtaCatalog.Entry>()
            if (!file.exists()) return entries
            val props = Properties()
            FileInputStream(file).use { props.load(it) }
            for (url in props.stringPropertyNames()) {
                val fields = props.getProperty(url).split('\t')
                try {
                    val uri = URI.create(url)
                    if (fields.size != 6 || uri.scheme != "https" || uri.host != OtaUrls.DL_GOOGLE_HOST) continue
                    entries.add(
                        OtaCatalog.Entry(
                            device = fields[0],
                            build = fields[1],
                            url = url,
                            sha256 = fields[2],
                            source = fields[3],
                            date = if (fields[4].isEmpty()) null else LocalDate.parse(fields[4]),
                            beta = fields[5].toBoolean(),
                        ),
                    )
                } catch (_: IllegalArgumentException) {
                    // Discard an invalid cache row.
                }
            }
            return entries
        }

        @Throws(IOException::class)
        internal fun writeCache(file: File, entries: List<OtaCatalog.Entry>) {
            val props = Properties()
            for (entry in entries) {
                props.setProperty(
                    entry.url,
                    listOf(
                        entry.device,
                        entry.build,
                        entry.sha256,
                        entry.source,
                        entry.date?.toString() ?: "",
                        entry.beta.toString(),
                    ).joinToString("\t"),
                )
            }
            val temp = File(file.parentFile, "${file.name}.tmp")
            FileOutputStream(temp).use { output ->
                props.store(output, "Official full OTA links previously observed on this device")
                output.fd.sync()
            }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
