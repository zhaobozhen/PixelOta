package life.absinthe.pixelota;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class OtaRepository {
    public static final class Result {
        public OtaCatalog.Entry current, latest;
        public boolean cachedCurrent, betaComplete = true;
        public final List<String> warnings = new ArrayList<>();
    }

    public Result query(String device, String build, File cache) throws InterruptedException {
        Result result = new Result();
        List<OtaCatalog.Entry> live = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            Map<String, String> initial = fetchBatch(pool, Set.of(OtaCatalog.VERSIONS, OtaCatalog.STABLE), result);
            String index = initial.get(OtaCatalog.VERSIONS);
            if (index == null) result.betaComplete = false;
            Set<Integer> majors = new LinkedHashSet<>(index == null ? List.of() : OtaCatalog.versions(index));
            if (majors.isEmpty()) {
                result.betaComplete = false;
                result.warnings.add("未能发现 Android Beta 版本入口");
            }
            Set<String> pending = new LinkedHashSet<>();
            for (int major : majors) pending.add(OtaCatalog.VERSIONS + "/" + major);
            Set<String> probes = new LinkedHashSet<>();
            for (int major : majors) {
                probes.add(OtaCatalog.VERSIONS + "/" + major + "/download-ota");
                probes.add(OtaCatalog.VERSIONS + "/" + major + "/download-ota-qpr");
                for (int qpr = 1; qpr <= 3; qpr++) probes.add(OtaCatalog.VERSIONS + "/" + major + "/qpr" + qpr + "/download-ota");
            }
            // New QPR pages can be published before Google's navigation links are updated.
            initial.putAll(fetchBatch(pool, probes, result, true));
            visited.addAll(probes);
            for (Map.Entry<String, String> page : initial.entrySet()) pending.addAll(OtaCatalog.pages(page.getValue(), page.getKey(), majors));
            pending.removeAll(visited);
            // shortcut: discover the two newest Android generations, add an archive service if full history is needed.
            for (int wave = 0; wave < 4 && !pending.isEmpty(); wave++) {
                visited.addAll(pending);
                Map<String, String> fetched = fetchBatch(pool, pending, result);
                initial.putAll(fetched);
                pending = new LinkedHashSet<>();
                for (Map.Entry<String, String> page : fetched.entrySet()) {
                    pending.addAll(OtaCatalog.pages(page.getValue(), page.getKey(), majors));
                }
                pending.removeAll(visited);
            }
            if (!pending.isEmpty()) {
                result.betaComplete = false;
                result.warnings.add("仍有未检查的 Beta 来源，请稍后重试");
            }
            for (Map.Entry<String, String> page : initial.entrySet()) {
                List<OtaCatalog.Entry> parsed = OtaCatalog.parse(page.getValue(), page.getKey());
                if ((page.getKey().equals(OtaCatalog.STABLE) || page.getKey().contains("download-ota")) && parsed.isEmpty()) {
                    result.warnings.add("官方页面未解析出完整 OTA：" + page.getKey());
                    if (!page.getKey().equals(OtaCatalog.STABLE)) result.betaComplete = false;
                }
                live.addAll(parsed);
            }
        } finally { pool.shutdownNow(); }
        result.latest = OtaCatalog.latest(live, device);
        if (result.latest == null && result.betaComplete) result.warnings.add("已检查来源中没有此设备的 Beta 完整 OTA");
        result.current = OtaCatalog.current(live, device, build);
        List<OtaCatalog.Entry> saved;
        try { saved = readCache(cache); }
        catch (IOException ex) { saved = new ArrayList<>(); result.warnings.add("本机历史索引读取失败"); }
        if (result.current == null) {
            result.current = OtaCatalog.current(saved, device, build);
            result.cachedCurrent = result.current != null;
        }
        Map<String, OtaCatalog.Entry> merged = new LinkedHashMap<>();
        for (OtaCatalog.Entry e : saved) merged.put(e.url, e);
        for (OtaCatalog.Entry e : live) merged.put(e.url, e);
        try { writeCache(cache, new ArrayList<>(merged.values())); }
        catch (IOException ex) { result.warnings.add("本机历史索引保存失败"); }
        return result;
    }

    private Map<String, String> fetchBatch(ExecutorService pool, Set<String> urls, Result result) throws InterruptedException {
        return fetchBatch(pool, urls, result, false);
    }

    private Map<String, String> fetchBatch(ExecutorService pool, Set<String> urls, Result result, boolean optional) throws InterruptedException {
        List<String> sources = new ArrayList<>(urls);
        List<Callable<String>> jobs = new ArrayList<>();
        for (String source : sources) jobs.add(() -> fetch(source));
        List<Future<String>> futures = pool.invokeAll(jobs);
        Map<String, String> pages = new LinkedHashMap<>();
        for (int i = 0; i < sources.size(); i++) {
            String source = sources.get(i);
            try { pages.put(source, futures.get(i).get()); }
            catch (ExecutionException ex) {
                if (optional && ex.getCause() instanceof java.io.FileNotFoundException) continue;
                result.warnings.add("无法读取 " + source + "（" + ex.getCause().getMessage() + "）");
                if (!source.equals(OtaCatalog.STABLE)) result.betaComplete = false;
            }
        }
        return pages;
    }

    public static String fetch(String source) throws IOException {
        URI uri = URI.create(source + (source.contains("?") ? "&" : "?") + "hl=en");
        for (int redirects = 0; redirects < 5; redirects++) {
            if (!"https".equals(uri.getScheme()) || !("developer.android.com".equals(uri.getHost())
                    || "developers.google.com".equals(uri.getHost()))) throw new IOException("非官方页面跳转");
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("User-Agent", "PixelOTA/0.1 (Android; full OTA link finder)");
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            if (uri.getHost().equals("developers.google.com")) {
                connection.setRequestProperty("Cookie", "devsite_wall_acks=nexus-ota-tos");
            }
            try {
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("缺少跳转地址");
                    uri = uri.resolve(location);
                    continue;
                }
                if (status == 404 || status == 410) throw new java.io.FileNotFoundException("HTTP " + status);
                if (status != 200) throw new IOException("HTTP " + status);
                try (InputStream input = connection.getInputStream()) {
                    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) throw new IOException("查询已取消");
                        if (bytes.size() + count > 4 * 1024 * 1024) throw new IOException("页面超出大小限制");
                        bytes.write(buffer, 0, count);
                    }
                    return bytes.toString(StandardCharsets.UTF_8.name());
                }
            } finally { connection.disconnect(); }
        }
        throw new IOException("跳转次数过多");
    }

    static List<OtaCatalog.Entry> readCache(File file) throws IOException {
        List<OtaCatalog.Entry> entries = new ArrayList<>();
        if (!file.exists()) return entries;
        Properties props = new Properties();
        try (FileInputStream input = new FileInputStream(file)) { props.load(input); }
        for (String url : props.stringPropertyNames()) {
            String[] fields = props.getProperty(url).split("\\t", -1);
            try {
                URI uri = URI.create(url);
                if (fields.length != 6 || !"https".equals(uri.getScheme()) || !"dl.google.com".equals(uri.getHost())) continue;
                entries.add(new OtaCatalog.Entry(fields[0], fields[1], url, fields[2], fields[3],
                        fields[4].isEmpty() ? null : LocalDate.parse(fields[4]), Boolean.parseBoolean(fields[5])));
            } catch (IllegalArgumentException ignored) { /* Discard an invalid cache row. */ }
        }
        return entries;
    }

    static void writeCache(File file, List<OtaCatalog.Entry> entries) throws IOException {
        Properties props = new Properties();
        for (OtaCatalog.Entry e : entries) props.setProperty(e.url, String.join("\t", e.device, e.build,
                e.sha256, e.source, e.date == null ? "" : e.date.toString(), Boolean.toString(e.beta)));
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temp)) {
            props.store(output, "Official full OTA links previously observed on this device");
            output.getFD().sync();
        }
        java.nio.file.Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
