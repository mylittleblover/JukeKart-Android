package com.mylittleblover.mkwplayer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.ui.art.ArtworkSlot
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Rule
import org.junit.Test

class ArtworkViewportDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun composedOffscreenArtworkStopsRequestingPixels() {
        val firstEdge = AtomicInteger()
        val lastEdge = AtomicInteger()
        compose.setContent {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                ArtworkSlot(Modifier.size(140.dp).testTag("first-cover")) { edge ->
                    SideEffect { firstEdge.set(edge) }
                }
                Spacer(Modifier.height(1400.dp))
                ArtworkSlot(Modifier.size(140.dp).testTag("last-cover")) { edge ->
                    SideEffect { lastEdge.set(edge) }
                }
            }
        }
        compose.waitUntil(5_000) { firstEdge.get() > 0 && lastEdge.get() == 0 }
        compose.onNodeWithTag("last-cover").performScrollTo()
        compose.waitUntil(5_000) { firstEdge.get() == 0 && lastEdge.get() > 0 }
        compose.onNodeWithTag("first-cover").performScrollTo()
        compose.waitUntil(5_000) { firstEdge.get() > 0 && lastEdge.get() == 0 }
    }
}
