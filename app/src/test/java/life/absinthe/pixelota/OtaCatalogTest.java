package life.absinthe.pixelota;

import org.junit.Test;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.*;

public class OtaCatalogTest {
    static String fixture(String name) throws Exception {
        try (InputStream input = OtaCatalogTest.class.getResourceAsStream("/" + name)) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test public void parsesOfficialBetaModalLinksAndChecksums() throws Exception {
        List<OtaCatalog.Entry> entries = OtaCatalog.parse(fixture("beta.html"), "https://developer.android.com/about/versions/17/qpr2/download-ota");
        assertEquals(23, entries.size());
        OtaCatalog.Entry pixel = OtaCatalog.current(entries, "bluejay", "CP41.260831.019");
        assertNotNull(pixel);
        assertTrue(pixel.beta);
        assertEquals(LocalDate.of(2026, 10, 16), pixel.date);
        assertEquals("111d8f25fae6e9ecbbb674dda785028701a9c0c1b9435ecd7e8d25d8e966730e", pixel.sha256);
        assertTrue(pixel.url.startsWith("https://dl.google.com/"));
        assertNull(OtaCatalog.current(entries, "bluejay", "CP41.260831.019.A1"));
        assertNull(OtaCatalog.current(entries, "oriole", "CP41.260831.019"));
    }

    @Test public void parsesStableFullOtaWithoutTreatingItAsBeta() throws Exception {
        List<OtaCatalog.Entry> entries = OtaCatalog.parse(fixture("stable.html"), OtaCatalog.STABLE);
        assertTrue(entries.size() > 300);
        OtaCatalog.Entry entry = OtaCatalog.current(entries, "cubs", "CD1A.261005.003.B1");
        assertNotNull(entry);
        assertFalse(entry.beta);
        assertNull(OtaCatalog.latest(entries, "cubs"));
    }

    @Test public void newerQprMissingFromOldNavigationStillMatchesInstalledBuild() throws Exception {
        List<OtaCatalog.Entry> entries = new java.util.ArrayList<>(OtaCatalog.parse(fixture("beta.html"), "https://developer.android.com/about/versions/17/qpr2/download-ota"));
        entries.addAll(OtaCatalog.parse(fixture("qpr3.html"), "https://developer.android.com/about/versions/17/qpr3/download-ota"));
        OtaCatalog.Entry current = OtaCatalog.current(entries, "bluejay", "DP11.261002.005");
        assertNotNull(current);
        assertEquals(LocalDate.of(2026, 10, 20), current.date);
        assertEquals("bluejay_beta-ota-dp11.261002.005-7940ca55.zip", current.filename());
        assertSame(current, OtaCatalog.latest(entries, "bluejay"));
    }

    @Test public void rejectsNonGoogleFactoryAndChecksumMismatch() throws Exception {
        String html = fixture("beta.html");
        assertTrue(OtaCatalog.parse(html.replace("https://dl.google.com/", "https://dl.google.com.evil.example/"), OtaCatalog.VERSIONS).isEmpty());
        assertTrue(OtaCatalog.parse(html.replace("-ota-", "-factory-"), OtaCatalog.VERSIONS).isEmpty());
        assertNull(OtaCatalog.current(OtaCatalog.parse(html.replace("111d8f25fae6", "222d8f25fae6"), OtaCatalog.VERSIONS), "bluejay", "CP41.260831.019"));
        assertTrue(OtaCatalog.parse("<html>Sign in or acknowledge terms</html>", OtaCatalog.STABLE).isEmpty());
    }

    @Test public void latestUsesPublicationDateAndNeverAnotherDevice() {
        OtaCatalog.Entry older = new OtaCatalog.Entry("bluejay", "ZZ99", "old", "", "", LocalDate.of(2026, 6, 1), true);
        OtaCatalog.Entry latest = new OtaCatalog.Entry("bluejay", "AA11", "new", "", "", LocalDate.of(2026, 8, 1), true);
        OtaCatalog.Entry unknownDate = new OtaCatalog.Entry("bluejay", "ZZ100", "unknown", "", "", null, true);
        assertSame(latest, OtaCatalog.latest(List.of(older, latest, unknownDate), "bluejay"));
        assertNull(OtaCatalog.latest(List.of(older, latest), "panther"));
    }

    @Test public void discoversChannelsAndRejectsUnrelatedLinks() throws Exception {
        assertEquals(List.of(17, 16), OtaCatalog.versions(fixture("versions.html")));
        Set<String> pages = OtaCatalog.pages(fixture("beta.html"), OtaCatalog.VERSIONS, Set.of(17));
        assertTrue(pages.contains("https://developer.android.com/about/versions/17/qpr2/download-ota"));
        assertTrue(pages.contains("https://developer.android.com/about/versions/17/download-ota"));
        assertFalse(pages.contains("https://developer.android.com/about/versions/17/download"));
        assertTrue(OtaCatalog.pages("<a href='https://evil.example/about/versions/17/download-ota'>x</a>", OtaCatalog.VERSIONS, Set.of(17)).isEmpty());
    }

    @Test public void historySurvivesRoundTripAndKeepsFullBuildSuffix() throws Exception {
        File folder = Files.createTempDirectory("pixelota-test").toFile();
        File cache = new File(folder, "index.properties");
        try {
            List<OtaCatalog.Entry> entries = OtaCatalog.parse(fixture("stable.html"), OtaCatalog.STABLE);
            OtaRepository.writeCache(cache, entries);
            List<OtaCatalog.Entry> read = OtaRepository.readCache(cache);
            assertEquals(entries.size(), read.size());
            assertNotNull(OtaCatalog.current(read, "cubs", "CD1A.261005.003.B1"));
            OtaRepository.writeCache(cache, List.of());
            assertTrue(OtaRepository.readCache(cache).isEmpty());
        } finally { Files.deleteIfExists(cache.toPath()); Files.deleteIfExists(folder.toPath()); }
    }
}
