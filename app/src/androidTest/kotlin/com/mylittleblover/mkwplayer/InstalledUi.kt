package com.mylittleblover.mkwplayer

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue

/** Actual installed UI interaction without Compose's global-idle requirement. */
internal class InstalledUi {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun find(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        for (index in 0 until node.childCount) find(node.getChild(index), predicate)?.let { return it }
        return null
    }
    fun awaitText(text: String, timeout: Long = 180_000, contains: Boolean = false): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < deadline) {
            find(instrumentation.uiAutomation.rootInActiveWindow) {
                it.isVisibleToUser && (if (contains) it.text?.toString()?.contains(text) == true else it.text?.toString() == text)
            }?.let { return it }
            SystemClock.sleep(500)
        }
        throw AssertionError("Installed UI did not display: $text")
    }
    fun tapText(text: String, contains: Boolean = false) = tapMatching {
        if (contains) it.text?.toString()?.contains(text) == true else it.text?.toString() == text
    }
    fun tapDescription(description: String) = tapMatching { it.contentDescription?.toString() == description }
    private fun tapMatching(predicate: (AccessibilityNodeInfo) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 180_000
        var bounds: Rect? = null
        while (bounds == null && SystemClock.uptimeMillis() < deadline) {
            fun visit(node: AccessibilityNodeInfo?) {
                if (node == null) return
                if (node.isVisibleToUser && node.isEnabled && predicate(node)) {
                    val candidate = Rect().also(node::getBoundsInScreen)
                    if (!candidate.isEmpty && (bounds == null || candidate.centerY() > bounds!!.centerY())) bounds = candidate
                }
                for (index in 0 until node.childCount) visit(node.getChild(index))
            }
            visit(instrumentation.uiAutomation.rootInActiveWindow)
            if (bounds == null) SystemClock.sleep(500)
        }
        val target = bounds ?: throw AssertionError("Visible touch target missing")
        val time = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, target.exactCenterX(), target.exactCenterY(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            assertTrue("Touch rejected", instrumentation.uiAutomation.injectInputEvent(event, true))
            event.recycle()
        }
    }
    fun awaitOrientation(landscape: Boolean) {
        val deadline = SystemClock.uptimeMillis() + 180_000
        var stable = 0
        while (SystemClock.uptimeMillis() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val bounds = Rect()
            root?.getBoundsInScreen(bounds)
            val correct = root?.packageName?.toString() == instrumentation.targetContext.packageName &&
                !bounds.isEmpty && (bounds.width() > bounds.height()) == landscape
            stable = if (correct) stable + 1 else 0
            if (stable >= 3) return
            SystemClock.sleep(500)
        }
        throw AssertionError("Installed app did not finish its orientation change")
    }
    fun capture(name: String) {
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(instrumentation.targetContext.filesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
