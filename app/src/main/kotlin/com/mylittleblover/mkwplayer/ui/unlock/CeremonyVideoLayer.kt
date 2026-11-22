package com.mylittleblover.mkwplayer.ui.unlock

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Build
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.player.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Fullscreen landscape Special Cup unlock ceremony stage.
 *
 * Displays `R.raw.rainbow_appears` in fullscreen landscape with a 1-second black fade-in, zero HUD
 * chrome or extra assets, and a 1-second black fade-out synchronized to the end of the cutscene.
 */
@Composable
internal fun SpecialCupUnlockCeremonyStage(
    visible: Boolean,
    progressFlow: StateFlow<PlayerViewModel.ProgressUi>,
    isPreviewOnly: Boolean = false,
    onDismissPreview: (() -> Unit)? = null,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(450)),
    ) {
        val context = LocalContext.current
        val progressUi by progressFlow.collectAsStateWithLifecycle()
        val livePositionMs = progressUi.positionMs

        var videoReady by remember { mutableStateOf(false) }
        var videoCompleted by remember { mutableStateOf(false) }
        var videoDurationMs by remember { mutableIntStateOf(68_000) }
        var fadeOutStarted by remember { mutableStateOf(false) }

        val blackVeilAlpha = remember { Animatable(1f) }

        // Lock landscape orientation, hide system bars edge-to-edge, and keep screen awake while active.
        DisposableEffect(context) {
            val activity = context.findActivity()
            val window = activity?.window
            val prevOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            val prevCutoutMode = if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode
            } else {
                0
            }
            if (activity != null) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            if (window != null) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val attrs = window.attributes
                    attrs.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    window.attributes = attrs
                }
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }
            onDispose {
                if (window != null) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val attrs = window.attributes
                        attrs.layoutInDisplayCutoutMode = prevCutoutMode
                        window.attributes = attrs
                    }
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.show(WindowInsetsCompat.Type.systemBars())
                    controller.isAppearanceLightStatusBars = true
                    controller.isAppearanceLightNavigationBars = true
                }
                if (activity != null) {
                    activity.requestedOrientation = prevOrientation
                }
            }
        }

        // 1-second fade-in from pure black as soon as the video surface begins rendering.
        LaunchedEffect(videoReady) {
            if (videoReady && !fadeOutStarted) {
                blackVeilAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
                )
            }
        }

        // Trigger the 1-second fade-out to black either 1s before the video ends (real ceremony)
        // or at the final second of the developer VFX preview window.
        LaunchedEffect(isPreviewOnly, videoReady) {
            if (isPreviewOnly && videoReady) {
                delay(5500L)
                fadeOutStarted = true
            }
        }
        LaunchedEffect(videoReady, livePositionMs, videoDurationMs, videoCompleted, isPreviewOnly) {
            if (!videoReady || fadeOutStarted) return@LaunchedEffect
            val fadeOutTriggerMs = (videoDurationMs - 1_000).coerceAtLeast(1_500).toLong()
            if (!isPreviewOnly && (videoCompleted || livePositionMs >= fadeOutTriggerMs)) {
                fadeOutStarted = true
            }
        }
        LaunchedEffect(fadeOutStarted) {
            if (fadeOutStarted) {
                blackVeilAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("special-cup-ceremony-overlay")
                .then(
                    if (onDismissPreview != null) {
                        val interactionSource = remember { MutableInteractionSource() }
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onDismissPreview,
                        )
                    } else {
                        Modifier.pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent()
                                }
                            }
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            CeremonyVideoBackdrop(
                playbackPositionMs = livePositionMs,
                onVideoStatusChanged = { ready, durationMs ->
                    videoReady = ready
                    if (durationMs > 0) videoDurationMs = durationMs
                },
                onVideoCompleted = { videoCompleted = true },
                modifier = Modifier.fillMaxSize(),
            )

            // Black fade veil for crisp 1s fade-in and 1s fade-out over the hardware video texture.
            val veilAlpha = blackVeilAlpha.value.coerceIn(0f, 1f)
            if (veilAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = veilAlpha)),
                )
            }
        }
    }
}

/**
 * Plays the bundled Special Cup unlock video (`R.raw.rainbow_appears`) silently over a [TextureView]
 * so the native BWAV engine remains the sole owner of ceremony audio (`Dm_RainbowAppears`).
 */
@Composable
internal fun CeremonyVideoBackdrop(
    playbackPositionMs: Long,
    modifier: Modifier = Modifier,
    onVideoStatusChanged: (ready: Boolean, durationMs: Int) -> Unit = { _, _ -> },
    onVideoCompleted: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var aspectRatio by remember { mutableFloatStateOf(16f / 9f) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var activeSurface by remember { mutableStateOf<Surface?>(null) }
    var prepared by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }
    var videoDurationMs by remember { mutableIntStateOf(68_000) }
    var resumed by remember { mutableStateOf(true) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val mp = mediaPlayer
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    resumed = true
                    if (prepared && !completed && mp != null && !mp.isPlaying) {
                        runCatching { mp.start() }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    resumed = false
                    if (prepared && mp != null && mp.isPlaying) {
                        runCatching { mp.pause() }
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            prepared = false
            runCatching { mediaPlayer?.release() }
            mediaPlayer = null
            runCatching { activeSurface?.release() }
            activeSurface = null
            onVideoStatusChanged(false, videoDurationMs)
        }
    }

    LaunchedEffect(playbackPositionMs, prepared, completed, resumed) {
        val mp = mediaPlayer ?: return@LaunchedEffect
        if (!prepared || !resumed || completed) return@LaunchedEffect
        val maxSeek = (videoDurationMs - 400).coerceAtLeast(0)
        if (playbackPositionMs in 0L..maxSeek.toLong()) {
            val cur = runCatching { mp.currentPosition }.getOrDefault(-1)
            if (cur >= 0 && abs(cur - playbackPositionMs) > 650L) {
                runCatching { mp.seekTo(playbackPositionMs.toInt()) }
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val containerAspect = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
        val videoModifier = if (containerAspect > aspectRatio) {
            Modifier.fillMaxHeight().aspectRatio(aspectRatio)
        } else {
            Modifier.fillMaxWidth().aspectRatio(aspectRatio)
        }

        AndroidView(
            modifier = videoModifier,
            factory = { ctx ->
                TextureView(ctx).apply {
                    isOpaque = false
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                            val surface = Surface(surfaceTexture)
                            activeSurface = surface
                            val player = MediaPlayer()
                            mediaPlayer = player
                            runCatching {
                                val afd = context.resources.openRawResourceFd(R.raw.rainbow_appears)
                                afd.use {
                                    player.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                                }
                                player.setSurface(surface)
                                player.setVolume(0f, 0f)
                                player.isLooping = false
                                player.setOnVideoSizeChangedListener { _, vw, vh ->
                                    if (vw > 0 && vh > 0) {
                                        aspectRatio = vw.toFloat() / vh.toFloat()
                                    }
                                }
                                player.setOnPreparedListener { mp ->
                                    val dur = mp.duration.takeIf { d -> d > 0 } ?: 68_000
                                    videoDurationMs = dur
                                    prepared = true
                                    completed = false
                                    if (playbackPositionMs in 1L until (dur - 400).toLong()) {
                                        runCatching { mp.seekTo(playbackPositionMs.toInt()) }
                                    }
                                    if (resumed) {
                                        runCatching { mp.start() }
                                    }
                                    onVideoStatusChanged(true, dur)
                                }
                                player.setOnCompletionListener {
                                    completed = true
                                    onVideoCompleted()
                                }
                                player.setOnErrorListener { _, _, _ ->
                                    prepared = false
                                    onVideoStatusChanged(false, videoDurationMs)
                                    true
                                }
                                player.prepareAsync()
                            }.onFailure {
                                prepared = false
                                onVideoStatusChanged(false, videoDurationMs)
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit

                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            prepared = false
                            runCatching { mediaPlayer?.release() }
                            mediaPlayer = null
                            runCatching { activeSurface?.release() }
                            activeSurface = null
                            onVideoStatusChanged(false, videoDurationMs)
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                    }
                }
            },
        )
    }
}
