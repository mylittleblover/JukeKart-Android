package com.mylittleblover.mkwplayer

import android.content.Intent
import android.graphics.Rect
import android.view.MotionEvent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Drives the installed app through Android accessibility, including actual tab clicks and scrolling. */
@RunWith(AndroidJUnit4::class)
class InteractiveNavigationDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun find(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        for (index in 0 until node.childCount) find(node.getChild(index), predicate)?.let { return it }
        return null
    }
    private fun awaitText(text: String, timeout: Long = 180_000): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < deadline) {
            find(instrumentation.uiAutomation.rootInActiveWindow) { it.isVisibleToUser && it.text?.toString() == text }?.let { return it }
            SystemClock.sleep(500)
        }
        throw AssertionError("Installed UI did not display: $text")
    }
    private fun click(text: String) {
        val deadline = SystemClock.uptimeMillis() + 180_000
        var bounds: Rect? = null
        while (bounds == null && SystemClock.uptimeMillis() < deadline) {
            // Compose exposes virtual nodes; traverse them rather than relying on provider text search.
            fun visit(node: AccessibilityNodeInfo?) {
                if (node == null) return
                if (node.isVisibleToUser && node.text?.toString() == text) {
                    val candidate = Rect().also(node::getBoundsInScreen)
                    if (!candidate.isEmpty && (bounds == null || candidate.centerY() > bounds!!.centerY())) bounds = candidate
                }
                for (index in 0 until node.childCount) visit(node.getChild(index))
            }
            visit(instrumentation.uiAutomation.rootInActiveWindow)
            if (bounds == null) SystemClock.sleep(500)
        }
        val target = bounds ?: throw AssertionError("Visible navigation label missing: $text")
        val time = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action,
                target.exactCenterX(), target.exactCenterY(), 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            assertTrue("Touch rejected: $text", instrumentation.uiAutomation.injectInputEvent(event, true))
            event.recycle()
        }
    }
    @Test fun courseInteractiveEntryReturnsThroughCup() {
        val context = instrumentation.targetContext
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        InstalledUi().awaitOrientation(landscape = false)
        click("Media")
        click("Grand Prix")
        click("Mushroom Cup")
        click("Mario Bros. Circuit")
        click("Open interactive player")
        awaitText("Interactive playback")
        assertTrue(instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
        awaitText("Complete suite")
        assertTrue(instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
        awaitText("Mushroom Cup")
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(context.filesDir, "interactive-navigation-pass.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}
