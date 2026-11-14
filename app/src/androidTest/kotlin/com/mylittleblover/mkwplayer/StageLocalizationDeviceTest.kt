package com.mylittleblover.mkwplayer

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.mylittleblover.mkwplayer.ui.interactive.stageText
import java.util.Locale
import org.junit.Rule
import org.junit.Test

class StageLocalizationDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun simplifiedChineseUsesTheConfirmedStageNames() {
        compose.setContent {
            val context = LocalContext.current
            val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale.SIMPLIFIED_CHINESE) }
            val chinese = context.createConfigurationContext(configuration)
            CompositionLocalProvider(LocalContext provides chinese) {
                Column {
                    listOf("Pre", "PreShort", "Main", "Fast", "Outro00", "Bridge", "Finished").forEach { key ->
                        Text(stageText(key))
                    }
                }
            }
        }
        listOf("序曲", "短序曲", "主赛道", "最后一圈", "尾奏", "桥段", "已结束").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
    }
}
