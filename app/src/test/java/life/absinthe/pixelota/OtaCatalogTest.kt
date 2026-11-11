package life.absinthe.pixelota

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.LocalDate

class OtaCatalogTest {
    private fun fixture(name: String): String {
        val stream = OtaCatalogTest::class.java.getResourceAsStream("/$name")
        assertNotNull(stream)
        return stream!!.use { String(it.readAllBytes(), StandardCharsets.UTF_8) }
    }

    @Test
    fun parsesOfficialBetaModalLinksAndChecksums() {
        val entries = OtaCatalog.parse(
            fixture("beta.html"),
            OtaUrls.versionQprDownloadOta(17, 2),
        )
        assertEquals(23, entries.size)
        val pixel = OtaCatalog.current(entries, "bluejay", "CP41.260831.019")
        assertNotNull(pixel)
        assertTrue(pixel!!.beta)
        assertEquals(LocalDate.of(2026, 10, 16), pixel.date)
        assertEquals("111d8f25fae6e9ecbbb674dda785028701a9c0c1b9435ecd7e8d25d8e966730e", pixel.sha256)
        assertTrue(pixel.url.startsWith("${OtaUrls.DL_GOOGLE_ORIGIN}/"))
        assertNull(OtaCatalog.current(entries, "bluejay", "CP41.260831.019.A1"))
        assertNull(OtaCatalog.current(entries, "oriole", "CP41.260831.019"))
    }

    @Test
    fun parsesStableFullOtaWithoutTreatingItAsBeta() {
        val entries = OtaCatalog.parse(fixture("stable.html"), OtaCatalog.STABLE)
        assertTrue(entries.size > 300)
        val entry = OtaCatalog.current(entries, "cubs", "CD1A.261005.003.B1")
        assertNotNull(entry)
        assertFalse(entry!!.beta)
        assertNull(OtaCatalog.latest(entries, "cubs"))
    }

    @Test
    fun newerQprMissingFromOldNavigationStillMatchesInstalledBuild() {
        val entries = OtaCatalog.parse(
            fixture("beta.html"),
            OtaUrls.versionQprDownloadOta(17, 2),
        ).toMutableList()
        entries.addAll(
            OtaCatalog.parse(
                fixture("qpr3.html"),
                OtaUrls.versionQprDownloadOta(17, 3),
            ),
        )
        val current = OtaCatalog.current(entries, "bluejay", "DP11.261002.005")
        assertNotNull(current)
        assertEquals(LocalDate.of(2026, 10, 20), current!!.date)
        assertEquals("bluejay_beta-ota-dp11.261002.005-7940ca55.zip", current.filename())
        assertSame(current, OtaCatalog.latest(entries, "bluejay"))
    }

    @Test
    fun rejectsNonGoogleFactoryAndChecksumMismatch() {
        val html = fixture("beta.html")
        assertTrue(
            OtaCatalog.parse(
                html.replace("${OtaUrls.DL_GOOGLE_ORIGIN}/", "https://dl.google.com.evil.example/"),
                OtaCatalog.VERSIONS,
            ).isEmpty(),
        )
        assertTrue(OtaCatalog.parse(html.replace("-ota-", "-factory-"), OtaCatalog.VERSIONS).isEmpty())
        assertNull(
            OtaCatalog.current(
                OtaCatalog.parse(html.replace("111d8f25fae6", "222d8f25fae6"), OtaCatalog.VERSIONS),
                "bluejay",
                "CP41.260831.019",
            ),
        )
        assertTrue(OtaCatalog.parse("<html>Sign in or acknowledge terms</html>", OtaCatalog.STABLE).isEmpty())
    }

    @Test
    fun latestUsesPublicationDateAndNeverAnotherDevice() {
        val older = OtaCatalog.Entry("bluejay", "ZZ99", "old", "", "", LocalDate.of(2026, 6, 1), true)
        val latest = OtaCatalog.Entry("bluejay", "AA11", "new", "", "", LocalDate.of(2026, 8, 1), true)
        val unknownDate = OtaCatalog.Entry("bluejay", "ZZ100", "unknown", "", "", null, true)
        assertSame(latest, OtaCatalog.latest(listOf(older, latest, unknownDate), "bluejay"))
        assertNull(OtaCatalog.latest(listOf(older, latest), "panther"))
    }

    @Test
    fun discoversChannelsAndRejectsUnrelatedLinks() {
        assertEquals(listOf(17, 16), OtaCatalog.versions(fixture("versions.html")))
        val pages = OtaCatalog.pages(fixture("beta.html"), OtaCatalog.VERSIONS, setOf(17))
        assertTrue(pages.contains(OtaUrls.versionQprDownloadOta(17, 2)))
        assertTrue(pages.contains(OtaUrls.versionDownloadOta(17)))
        assertFalse(pages.contains("${OtaUrls.version(17)}/download"))
        assertTrue(
            OtaCatalog.pages(
                "<a href='https://evil.example/about/versions/17/download-ota'>x</a>",
                OtaCatalog.VERSIONS,
                setOf(17),
            ).isEmpty(),
        )
    }

    @Test
    fun historySurvivesRoundTripAndKeepsFullBuildSuffix() {
        val folder = Files.createTempDirectory("pixelota-test").toFile()
        val cache = folder.resolve("index.properties")
        try {
            val entries = OtaCatalog.parse(fixture("stable.html"), OtaCatalog.STABLE)
            OtaRepository.writeCache(cache, entries)
            val read = OtaRepository.readCache(cache)
            assertEquals(entries.size, read.size)
            assertNotNull(OtaCatalog.current(read, "cubs", "CD1A.261005.003.B1"))
            OtaRepository.writeCache(cache, emptyList())
            assertTrue(OtaRepository.readCache(cache).isEmpty())
        } finally {
            Files.deleteIfExists(cache.toPath())
            Files.deleteIfExists(folder.toPath())
        }
    }

    @Test
    fun uiStateMappingPreservesDeviceCheckAndResultProvenance() {
        assertTrue(isSupportedPixelDevice("Google", "Pixel 9a", "tegu", "google/tegu/tegu:17/BP1A"))
        assertFalse(isSupportedPixelDevice("Google", "Pixel 9a", "emu64xa", "google/sdk_gphone64_arm64"))
        assertFalse(isSupportedPixelDevice("Xiaomi", "Pixel", "bluejay", "release-keys"))

        val entry = OtaCatalog.Entry(
            "bluejay",
            "CP41.260831.019",
            "${OtaUrls.DL_GOOGLE_ORIGIN}/dl/android/aosp/bluejay_beta-ota-cp41.260831.019-111d8f25.zip",
            "111d8f25fae6e9ecbbb674dda785028701a9c0c1b9435ecd7e8d25d8e966730e",
            OtaUrls.versionQprDownloadOta(17, 2),
            LocalDate.of(2026, 10, 16),
            true,
        )
        val partial = OtaRepository.Result().apply {
            current = entry
            cachedCurrent = true
            betaComplete = false
            warnings.add(OtaRepository.Warning.PendingSources)
        }
        val sections = buildOtaSections(partial, "CP41.260831.019")
        assertEquals(R.string.ota_section_current_title, sections[0].titleRes)
        assertEquals(R.string.ota_note_cached, sections[0].noteRes)
        assertEquals(R.string.ota_empty_current, sections[0].emptyMessageRes)
        assertEquals(listOf("CP41.260831.019"), sections[0].emptyArgs)
        assertTrue(sections[0].isWarningNote)
        assertEquals(R.string.ota_section_beta_found, sections[1].titleRes)
        assertEquals(R.string.ota_empty_beta_partial, sections[1].emptyMessageRes)
        assertEquals(UiStatus.Checked("11-11 10:30", complete = false), buildUiStatus(partial, "11-11 10:30"))
    }
}
