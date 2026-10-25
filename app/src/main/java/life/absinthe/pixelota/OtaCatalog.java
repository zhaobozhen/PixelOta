package life.absinthe.pixelota;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Parses Google's published full OTA links; never constructs a download URL. */
public final class OtaCatalog {
    public static final String VERSIONS = "https://developer.android.com/about/versions";
    public static final String STABLE = "https://developers.google.com/android/ota";
    private static final Pattern FILE = Pattern.compile("^([a-z0-9]+)(_beta)?-ota-([a-z0-9.]+)-([a-f0-9]{8})\\.zip$");
    private static final Pattern HASH = Pattern.compile("(?i)\\b[a-f0-9]{64}\\b");
    private static final Pattern VERSION = Pattern.compile("^/about/versions/(\\d+)$");
    private static final Pattern PAGE = Pattern.compile("^/about/versions/\\d+(?:/qpr\\d+)?(?:/download-ota(?:-qpr)?|/get)?$");

    public static final class Entry {
        public final String device, build, url, sha256, source;
        public final LocalDate date;
        public final boolean beta;

        public Entry(String device, String build, String url, String sha256, String source,
                     LocalDate date, boolean beta) {
            this.device = device;
            this.build = build;
            this.url = url;
            this.sha256 = sha256;
            this.source = source;
            this.date = date;
            this.beta = beta;
        }

        public String filename() { return URI.create(url).getPath().replaceFirst(".*/", ""); }
    }

    public static List<Entry> parse(String html, String source) {
        Document doc = Jsoup.parse(html, source);
        LocalDate date = null;
        for (Element row : doc.select("tr")) {
            if (row.select("td").size() > 1 && row.select("td").first().text().equalsIgnoreCase("Release date")) {
                try { date = LocalDate.parse(row.select("td").get(1).text(), DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.US)); }
                catch (DateTimeParseException ignored) { /* Unknown date must not win latest selection. */ }
                break;
            }
        }
        List<Entry> entries = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Element link : doc.select("a[href]")) {
            String url = link.absUrl("href");
            URI uri;
            try { uri = URI.create(url); } catch (IllegalArgumentException ex) { continue; }
            if (!"https".equals(uri.getScheme()) || !"dl.google.com".equals(uri.getHost())
                    || uri.getUserInfo() != null || uri.getPort() != -1 || uri.getQuery() != null) continue;
            Matcher file = FILE.matcher(uri.getPath().replaceFirst(".*/", ""));
            if (!file.matches() || !seen.add(url)) continue;
            String device = file.group(1);
            Element row = link.closest("tr");
            if (row == null) {
                for (Element candidate : doc.select("tr[id]")) {
                    if (candidate.id().equals(device) && candidate.text().contains(file.group())) { row = candidate; break; }
                }
            }
            if (row == null) continue;
            Matcher hash = HASH.matcher(row.text());
            if (!hash.find()) continue;
            String checksum = hash.group().toLowerCase(Locale.ROOT);
            if (!checksum.startsWith(file.group(4))) continue;
            entries.add(new Entry(device, file.group(3).toUpperCase(Locale.ROOT), url,
                    checksum, source, date, file.group(2) != null));
        }
        return entries;
    }

    public static List<Integer> versions(String html) {
        Set<Integer> numbers = new LinkedHashSet<>();
        for (Element link : Jsoup.parse(html, VERSIONS).select("a[href]")) {
            URI uri;
            try { uri = URI.create(link.absUrl("href")); } catch (IllegalArgumentException ex) { continue; }
            Matcher version = VERSION.matcher(uri.getPath() == null ? "" : uri.getPath());
            if ("developer.android.com".equals(uri.getHost()) && version.matches()) numbers.add(Integer.parseInt(version.group(1)));
        }
        return numbers.stream().sorted(Comparator.reverseOrder()).limit(2).collect(Collectors.toList());
    }

    public static Set<String> pages(String html, String source, Set<Integer> majors) {
        Set<String> pages = new LinkedHashSet<>();
        for (Element link : Jsoup.parse(html, source).select("a[href]")) {
            URI uri;
            try { uri = URI.create(link.absUrl("href")); } catch (IllegalArgumentException ex) { continue; }
            String path = uri.getPath();
            if (!"https".equals(uri.getScheme()) || !"developer.android.com".equals(uri.getHost())
                    || path == null || !PAGE.matcher(path).matches()) continue;
            int major = Integer.parseInt(path.split("/")[3]);
            if (majors.contains(major)) pages.add("https://developer.android.com" + path);
        }
        return pages;
    }

    public static Entry current(List<Entry> entries, String device, String build) {
        return entries.stream().filter(e -> e.device.equals(device) && e.build.equalsIgnoreCase(build)).findFirst().orElse(null);
    }

    public static Entry latest(List<Entry> entries, String device) {
        // A published release date, rather than lexicographic Build ID order, decides the latest beta.
        return entries.stream().filter(e -> e.beta && e.device.equals(device) && e.date != null)
                .max(Comparator.comparing((Entry e) -> e.date).thenComparing(e -> e.build)).orElse(null);
    }
}
