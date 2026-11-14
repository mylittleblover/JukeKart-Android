package com.mylittleblover.mkwplayer

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.platform.app.InstrumentationRegistry
import com.mylittleblover.mkwplayer.library.BrowseCatalog
import com.mylittleblover.mkwplayer.library.MapAnchors
import com.mylittleblover.mkwplayer.routes.LocalizedNames
import com.mylittleblover.mkwplayer.routes.OfficialRouteCatalog
import com.mylittleblover.mkwplayer.ui.MkwTheme
import com.mylittleblover.mkwplayer.ui.home.DiscoveryHome
import com.mylittleblover.mkwplayer.ui.home.DiscoveryViewModel
import com.mylittleblover.mkwplayer.player.PlayerViewModel
import org.junit.Rule
import org.junit.Test

/** Layout fixture uses shipped identities; it does not claim any playback/session acceptance. */
class HomeDesignDeviceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun homeHeroFavoritesAndRecentRemainReachable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun asset(name: String) = context.assets.open(name).bufferedReader().use { it.readText() }
        val anchors = requireNotNull(MapAnchors.parse(asset("map_anchors.json")))
        val routes = requireNotNull(OfficialRouteCatalog.parse(asset("official_routes.json"), anchors.stages.map { it.code }.toSet()))
        val catalog = requireNotNull(BrowseCatalog.create(routes, requireNotNull(LocalizedNames.parse(asset("localized_names.json"))), anchors))
        val ids = catalog.courses(false).take(3)
        compose.setContent {
            MkwTheme {
                DiscoveryHome(catalog, DiscoveryViewModel.State(dailyIds = ids, favorites = ids.toSet(), recentIds = ids),
                    "en", false, {}, {}, {}, {}, {}, {}, PlayerViewModel.Ui(), null)
            }
        }
        compose.onNodeWithText(context.getString(R.string.discovery_home_headline)).assertIsDisplayed()
        InstalledUi().capture("alpha17-home-top.png")
        compose.onNodeWithText(context.getString(R.string.discovery_favorites)).performScrollTo().assertIsDisplayed()
        InstalledUi().capture("alpha17-home-favorites.png")
        compose.onNodeWithText(context.getString(R.string.discovery_recent)).performScrollTo().assertIsDisplayed()
        InstalledUi().capture("alpha17-home-recent.png")
    }
}
