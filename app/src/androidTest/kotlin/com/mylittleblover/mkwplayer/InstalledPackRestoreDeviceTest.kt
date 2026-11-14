package com.mylittleblover.mkwplayer

import android.app.Application
import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mylittleblover.mkwplayer.pack.PackRegistry
import com.mylittleblover.mkwplayer.player.PlayerViewModel
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstalledPackRestoreDeviceTest {
    @Test fun installedCoursesAreAvailableAfterColdLaunchWithoutStartingAudio() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(PackRegistry(File(context.filesDir, "packs")).load().isNotEmpty())
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)).use { scenario ->
            scenario.onActivity { activity ->
                val model = ViewModelProvider(activity, ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application))[PlayerViewModel::class.java]
                assertNotNull(model.ui.value.pack)
                for (code in listOf("DonkeyKong", "SnowMountain", "FirstCircuit", "Rainbow")) {
                    assertTrue("Installed course unavailable: $code", model.canStartStage(code))
                }
                assertFalse(model.ui.value.outputActive)
                assertNull(model.ui.value.flow)
            }
        }
    }
}
