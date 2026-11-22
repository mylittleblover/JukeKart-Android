package com.mylittleblover.mkwplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mylittleblover.mkwplayer.player.PlayerViewModel

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_ENTER_CEREMONY = "com.mylittleblover.mkwplayer.EXTRA_ENTER_CEREMONY"
    }

    private val pendingEnterCeremony = androidx.compose.runtime.mutableStateOf(false)

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.mylittleblover.mkwplayer.i18n.AppLanguageSettings.wrapLegacy(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra(EXTRA_ENTER_CEREMONY, false) == true) {
            pendingEnterCeremony.value = true
        }
        com.mylittleblover.mkwplayer.i18n.AppLanguageSettings.migratePlatformChoice(this)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true
        setContent {
            com.mylittleblover.mkwplayer.ui.MkwTheme {
                val model: PlayerViewModel = viewModel()
                val ui by model.ui.collectAsStateWithLifecycle()
                MkwApp(
                    model = model,
                    ui = ui,
                    pendingEnterCeremony = pendingEnterCeremony.value,
                    onConsumedEnterCeremony = { pendingEnterCeremony.value = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_ENTER_CEREMONY, false)) {
            pendingEnterCeremony.value = true
        }
    }
}
