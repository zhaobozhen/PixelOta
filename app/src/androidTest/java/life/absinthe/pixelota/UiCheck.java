package life.absinthe.pixelota;

import android.app.Instrumentation;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Device check uses an official HTML fixture, never spoofs the device or changes production inputs. */
public class UiCheck extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    @Override public void onStart() {
        Bundle report = new Bundle();
        try {
            String html;
            try (InputStream input = getContext().getAssets().open("beta.html")) {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192]; int n;
                while ((n = input.read(buffer)) != -1) bytes.write(buffer, 0, n);
                html = bytes.toString(StandardCharsets.UTF_8.name());
            }
            List<OtaCatalog.Entry> entries = OtaCatalog.parse(html, "https://developer.android.com/about/versions/17/qpr2/download-ota");
            OtaCatalog.Entry entry = OtaCatalog.current(entries, "bluejay", "CP41.260831.019");
            if (entry == null) throw new AssertionError("Fixture missing");
            MainActivity activity = (MainActivity) startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            runOnMainSync(() -> {
                OtaRepository.Result empty = new OtaRepository.Result();
                empty.betaComplete = false;
                activity.render(empty);
                requireText(activity, "未找到 ");
                requireText(activity, "暂时无法确认");
                OtaRepository.Result partial = new OtaRepository.Result();
                partial.betaComplete = false;
                partial.latest = entry;
                activity.render(partial);
                requireText(activity, "已查到的 Beta 完整 OTA");
                OtaRepository.Result found = new OtaRepository.Result();
                found.current = entry;
                found.latest = entry;
                activity.render(found);
                View copy = find(activity.getWindow().getDecorView(), "复制 当前版本对应的 OTA 下载链接", true);
                if (copy == null) throw new AssertionError("Copy action missing");
                copy.performClick();
                ClipboardManager clipboard = activity.getSystemService(ClipboardManager.class);
                if (clipboard.getPrimaryClip() == null || !entry.url.contentEquals(clipboard.getPrimaryClip().getItemAt(0).getText())) throw new AssertionError("Clipboard mismatch");
                TextView status = (TextView) find(activity.getWindow().getDecorView(), "检查时间", false);
                status.setText("界面测试 · Pixel 6a 官方数据，非本机识别结果");
            });
            waitForIdleSync();
            android.os.SystemClock.sleep(2500);
            java.io.File screenshot = new java.io.File(getTargetContext().getExternalFilesDir(null), "ui-check.png");
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(screenshot)) {
                android.graphics.Bitmap bitmap = getUiAutomation().takeScreenshot();
                if (bitmap == null) throw new AssertionError("Screenshot unavailable");
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
            }
            report.putString("checks", "empty, partial, populated, clipboard exact URL: PASS; fixture UI only");
            finish(-1, report);
        } catch (Throwable ex) {
            report.putString("failure", ex.toString());
            finish(0, report);
        }
    }

    private void requireText(MainActivity activity, String text) {
        if (find(activity.getWindow().getDecorView(), text, false) == null) throw new AssertionError("Missing state: " + text);
    }

    private View find(View view, String text, boolean description) {
        if (description ? text.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())
                : view instanceof TextView && ((TextView) view).getText().toString().startsWith(text)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(group.getChildAt(i), text, description);
                if (found != null) return found;
            }
        }
        return null;
    }
}
