package life.absinthe.pixelota;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int INK = Color.rgb(27, 44, 37), MUTED = Color.rgb(82, 99, 89);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout content, results;
    private Button refresh;
    private TextView status;
    private ProgressBar progress;
    private boolean busy;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = column();
        content.setPadding(dp(24), dp(20), dp(24), dp(32));
        scroll.addView(content);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            scroll.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsets.CONSUMED;
        });
        setContentView(scroll);
        addText(content, "PIXEL / 完整 OTA", 12, MUTED, false);
        addText(content, "Pixel OTA", 32, INK, true);
        addText(content, "找到版本，复制官方链接。", 15, MUTED, false);
        LinearLayout device = column();
        device.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable surface = new GradientDrawable();
        surface.setColor(Color.rgb(225, 237, 226));
        surface.setCornerRadius(dp(18));
        device.setBackground(surface);
        LinearLayout.LayoutParams deviceParams = new LinearLayout.LayoutParams(-1, -2);
        deviceParams.topMargin = dp(24);
        content.addView(device, deviceParams);
        addText(device, Build.MODEL, 22, INK, true);
        addText(device, "Android " + Build.VERSION.RELEASE + " · " + Build.DEVICE, 14, MUTED, false);
        TextView build = addText(device, Build.ID, 15, INK, false);
        build.setTypeface(Typeface.MONOSPACE);
        build.setTextIsSelectable(true);
        addText(device, "安全补丁 " + Build.VERSION.SECURITY_PATCH, 13, MUTED, false);
        refresh = button(content, "查询 OTA", this::query);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        content.addView(progress, new LinearLayout.LayoutParams(-1, dp(4)));
        status = addText(content, "无需 Root · 仅查询 Google 官方完整 OTA", 13, MUTED, false);
        results = column();
        content.addView(results);
        addText(content, "本工具仅查找下载链接，不判断能否直接升级或降级，也不会安装系统。", 13, MUTED, false);
        button(content, "下载条款与官方说明", this::showTerms);
        if (!isPixel()) {
            status.setText("未识别为原厂 Pixel：" + Build.MANUFACTURER + " / " + Build.DEVICE
                    + "。请在 Pixel 原厂系统上使用。模拟器不代表对应的 Pixel 硬件。");
            refresh.setEnabled(false);
        } else if (getPreferences(MODE_PRIVATE).getBoolean("terms", false)) {
            query();
        } else {
            showTerms();
        }
    }

    private boolean isPixel() {
        return "Google".equalsIgnoreCase(Build.MANUFACTURER) && Build.MODEL.startsWith("Pixel")
                && !Build.DEVICE.startsWith("generic") && !Build.DEVICE.startsWith("emu")
                && !Build.FINGERPRINT.contains("generic");
    }

    private void showTerms() {
        new AlertDialog.Builder(this).setTitle("Google 下载条款")
                .setMessage("系统镜像受 Google 的下载条款约束。请先查看官方说明，再确认同意。应用仅保存查到的链接索引，不上传机型或版本，也不会下载镜像。")
                .setNeutralButton("查看官方条款", (d, w) -> open(OtaCatalog.STABLE + "#legal"))
                .setNegativeButton("暂不查询", null)
                .setPositiveButton("同意并查询", (d, w) -> {
                    getPreferences(MODE_PRIVATE).edit().putBoolean("terms", true).apply();
                    if (isPixel()) query();
                }).show();
    }

    private void query() {
        if (busy) return;
        if (!getPreferences(MODE_PRIVATE).getBoolean("terms", false)) { showTerms(); return; }
        busy = true;
        refresh.setEnabled(false);
        results.removeAllViews();
        progress.setVisibility(View.VISIBLE);
        status.setText("正在检查 Google 官方 OTA 来源…");
        worker.execute(() -> {
            try {
                OtaRepository.Result result = new OtaRepository().query(Build.DEVICE, Build.ID,
                        new File(getFilesDir(), "ota-index.properties"));
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    finishLoading();
                    render(result);
                });
            } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            catch (RuntimeException ex) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    finishLoading();
                    status.setText("查询失败，请检查网络后重试。" + ex.getClass().getSimpleName());
                });
            }
        });
    }

    private void finishLoading() {
        busy = false;
        refresh.setEnabled(true);
        refresh.setText("重新查询");
        progress.setVisibility(View.GONE);
    }

    // Package-visible so the device UI check can exercise real result states without changing device identity.
    void render(OtaRepository.Result result) {
        results.removeAllViews();
        status.setText("检查时间 " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                + (result.warnings.isEmpty() ? " · 查询完成" : " · 部分来源未确认"));
        section("当前版本对应的 OTA", result.current, result.cachedCurrent ? "来自本机历史索引，链接未重新确认" : "与本机完整 Build ID 一致",
                "未找到 " + Build.ID + " 对应的完整 OTA。官方可能未公开或已撤下此版本；请检查网络或稍后重试。");
        section(result.betaComplete ? "最新 Beta 完整 OTA" : "已查到的 Beta 完整 OTA", result.latest,
                result.betaComplete ? "按已检查官方来源的发布日期选择" : "部分来源读取失败，无法确认是否最新",
                result.betaComplete ? "已检查来源中没有此设备的 Beta 完整 OTA。" : "暂时无法确认，请检查网络后重试。");
        if (!result.warnings.isEmpty()) {
            TextView warnings = addText(results, String.join("\n", result.warnings), 13, MUTED, false);
            warnings.setTextIsSelectable(true);
        }
    }

    private void section(String title, OtaCatalog.Entry entry, String note, String empty) {
        View line = new View(this);
        line.setBackgroundColor(Color.rgb(211, 220, 212));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.topMargin = dp(24); lp.bottomMargin = dp(16);
        results.addView(line, lp);
        addText(results, title, 20, INK, true);
        if (entry == null) { addText(results, empty, 14, MUTED, false); return; }
        TextView build = addText(results, entry.build, 17, INK, true);
        build.setTypeface(Typeface.MONOSPACE);
        build.setTextIsSelectable(true);
        addText(results, note + (entry.date == null ? "" : "\n发布于 " + entry.date), 13, MUTED, false);
        TextView filename = addText(results, entry.filename(), 13, MUTED, false);
        filename.setTextIsSelectable(true);
        Button copy = button(results, "复制下载链接", () -> {
            ClipboardManager clipboard = getSystemService(ClipboardManager.class);
            clipboard.setPrimaryClip(ClipData.newPlainText("Pixel 完整 OTA · " + entry.build, entry.url));
            Toast.makeText(this, "已复制 Google 官方 ZIP 链接", Toast.LENGTH_SHORT).show();
        });
        copy.setContentDescription("复制 " + title + " 下载链接");
        button(results, "查看官方来源", () -> open(entry.source));
        TextView sha = addText(results, "SHA-256\n" + entry.sha256, 12, MUTED, false);
        sha.setTypeface(Typeface.MONOSPACE);
        sha.setTextIsSelectable(true);
    }

    private void open(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (android.content.ActivityNotFoundException ex) { Toast.makeText(this, "没有可用的浏览器", Toast.LENGTH_SHORT).show(); }
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView addText(LinearLayout parent, String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_SIMPLE);
        if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setPadding(0, dp(5), 0, dp(5));
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    private Button button(LinearLayout parent, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setMinHeight(dp(48));
        button.setTextColor(Color.rgb(27, 75, 56));
        button.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(221, 235, 222)));
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(8);
        parent.addView(button, params);
        return button;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
}
