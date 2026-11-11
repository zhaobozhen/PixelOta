package life.absinthe.pixelota

import android.app.Instrumentation
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

/** Device check uses an official HTML fixture, never spoofs the device or changes production inputs. */
class UiCheck : Instrumentation() {
    override fun onCreate(args: Bundle?) {
        super.onCreate(args)
        start()
    }

    override fun onStart() {
        val report = Bundle()
        try {
            val html = context.assets.open("beta.html").use { input ->
                String(input.readBytes(), StandardCharsets.UTF_8)
            }
            val entries = OtaCatalog.parse(
                html,
                OtaUrls.versionQprDownloadOta(17, 2),
            )
            val entry = OtaCatalog.current(entries, "bluejay", "CP41.260831.019")
                ?: throw AssertionError("Fixture missing")

            val activity = startActivitySync(
                Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ) as MainActivity
            waitForIdleSync()
            SystemClock.sleep(500)

            runOnMainSync {
                activity.render(OtaRepository.Result().apply { betaComplete = false })
            }
            waitForComposeIdle()
            requireNodeText(targetContext.getString(R.string.ota_empty_current, "test").substringBefore("test"))
            requireNodeText(targetContext.getString(R.string.ota_empty_beta_partial))

            runOnMainSync {
                activity.render(
                    OtaRepository.Result().apply {
                        betaComplete = false
                        latest = entry
                    },
                )
            }
            waitForComposeIdle()
            scrollToTop()
            requireNodeText(targetContext.getString(R.string.ota_section_beta_found))

            val found = OtaRepository.Result().apply {
                current = entry
                latest = entry
            }
            runOnMainSync {
                activity.render(found)
            }
            waitForComposeIdle()
            scrollToTop()
            val copyDescription = targetContext.getString(
                R.string.ota_copy_content_desc,
                targetContext.getString(R.string.ota_section_current_title),
            )
            val copyNode = findNodeWithScroll(copyDescription, byDescription = true)
                ?: throw AssertionError("Copy action missing")
            if (!performClick(copyNode)) {
                throw AssertionError("Copy action click failed")
            }
            SystemClock.sleep(300)

            runOnMainSync {
                val clipboard = activity.getSystemService(ClipboardManager::class.java)
                val primary = clipboard?.primaryClip
                if (primary == null || !entry.url.contentEquals(primary.getItemAt(0).text)) {
                    throw AssertionError("Clipboard mismatch")
                }
                activity.render(found, targetContext.getString(R.string.ui_check_banner))
            }
            waitForComposeIdle()
            scrollToTop()
            SystemClock.sleep(2500)

            val screenshot = File(targetContext.getExternalFilesDir(null), "ui-check.png")
            FileOutputStream(screenshot).use { output ->
                val bitmap = uiAutomation.takeScreenshot()
                    ?: throw AssertionError("Screenshot unavailable")
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            report.putString("checks", "empty, partial, populated, clipboard exact URL: PASS; fixture UI only")
            finish(-1, report)
        } catch (ex: Throwable) {
            report.putString("failure", ex.toString())
            finish(0, report)
        }
    }

    private fun requireNodeText(prefix: String) {
        val deadline = SystemClock.uptimeMillis() + 2500L
        while (SystemClock.uptimeMillis() < deadline) {
            if (findNodeWithScroll(prefix, byDescription = false) != null) return
            scrollToTop()
            SystemClock.sleep(200)
        }
        throw AssertionError("Missing state: $prefix")
    }

    private fun waitForComposeIdle() {
        waitForIdleSync()
        SystemClock.sleep(350)
    }

    private fun findNodeWithScroll(text: String, byDescription: Boolean): AccessibilityNodeInfo? {
        findInActiveWindow(text, byDescription)?.let { return it }
        for (attempt in 0 until 4) {
            if (!scroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) break
            findInActiveWindow(text, byDescription)?.let { return it }
        }
        scrollToTop()
        return findInActiveWindow(text, byDescription)
    }

    private fun scrollToTop() {
        for (attempt in 0 until 4) {
            if (!scroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) break
        }
    }

    private fun scroll(action: Int): Boolean {
        val root = uiAutomation.rootInActiveWindow ?: return false
        val scrollable = findScrollable(root) ?: return false
        val moved = scrollable.performAction(action)
        if (moved) {
            waitForIdleSync()
            SystemClock.sleep(250)
        }
        return moved
    }

    private fun findInActiveWindow(text: String, byDescription: Boolean): AccessibilityNodeInfo? {
        val root = uiAutomation.rootInActiveWindow ?: return null
        return findNode(root, text, byDescription)
    }

    private fun findNode(
        node: AccessibilityNodeInfo,
        text: String,
        byDescription: Boolean,
    ): AccessibilityNodeInfo? {
        val candidate = (if (byDescription) node.contentDescription else node.text)?.toString().orEmpty()
        val matches = if (byDescription) candidate == text else candidate.startsWith(text)
        if (matches) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findNode(child, text, byDescription)?.let { return it }
        }
        return null
    }

    private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findScrollable(child)?.let { return it }
        }
        return null
    }

    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            current = current.parent
        }
        return false
    }
}
