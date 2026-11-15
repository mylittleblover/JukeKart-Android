package com.mylittleblover.mkwplayer

import android.app.LocaleManager
import android.content.Intent
import android.os.Build
import android.os.LocaleList
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.mylittleblover.mkwplayer.player.PlayerViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLanguageDeviceTest {
    private fun currentModel(): PlayerViewModel {
        var value: PlayerViewModel? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
            value = ViewModelProvider(activity)[PlayerViewModel::class.java]
        }
        return requireNotNull(value)
    }
    @Test fun languageChangesPreserveNavigationMapSelectionAndTheViewModel() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val oldTags = if (Build.VERSION.SDK_INT >= 33) context.getSystemService(LocaleManager::class.java).applicationLocales else LocaleList.getEmptyLocaleList()
        val ui = InstalledUi()
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        ui.tapText("Map")
        ui.tapText("Official routes")
        ui.tapText("Mushroom Cup", contains = true)
        ui.awaitText("Start: Mario Bros. Circuit")
        ui.tapDescription("Settings")
        ui.awaitText("App language")
        val model = currentModel()
        try {
            ui.tapText("简体中文")
            ui.awaitText("应用语言")
            assertSame(model, currentModel())
            ui.tapText("繁體中文")
            ui.awaitText("應用程式語言")
            assertSame(model, currentModel())
            ui.tapText("日本語")
            ui.awaitText("アプリの言語")
            assertSame(model, currentModel())
            ui.tapText("English")
            ui.awaitText("App language")
            assertSame(model, currentModel())
            ui.tapDescription("Back")
            ui.awaitText("Start: Mario Bros. Circuit")
            ui.awaitText("Finish: DK Spaceport")
            ui.capture("language-switch-map-preserved.png")
        } finally {
            if (Build.VERSION.SDK_INT >= 33) context.getSystemService(LocaleManager::class.java).applicationLocales = oldTags
        }
    }
}
