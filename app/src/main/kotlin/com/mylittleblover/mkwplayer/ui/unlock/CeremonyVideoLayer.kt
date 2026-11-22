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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.mylittleblover.mkwplayer.player.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Fullscreen landscape Special Cup unlock ceremony stage.
 *
 * Displays an optional user-provided external ceremony video (e.g. `.mkwpack/artwork/rainbow_appears.webm`)
 * or renders the built-in original Prism Horizon Canvas celebration in fullscreen landscape with a
 * 1-second black fade-in, zero HUD chrome, and a 1-second black fade-out synchronized to the end.
 */
@Composable
internal fun SpecialCupUnlockCeremonyStage(
    visible: Boolean,
    progressFlow: StateFlow<PlayerViewModel.ProgressUi>,
    packDirectory: File? = null,
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
                audioDurationMs = progressUi.durationMs.takeIf { it > 0 }?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 68_000,
                packDirectory = packDirectory,
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

private fun resolveExternalCeremonyVideo(context: Context, packDirectory: File?): File? {
    val dirs = listOfNotNull(
        packDirectory?.let { File(it, "artwork") },
        packDirectory?.let { File(it, "video") },
        packDirectory,
        File(context.applicationContext.filesDir, "ceremony"),
        File(context.applicationContext.filesDir, "artwork"),
    )
    val names = listOf("rainbow_appears.webm", "rainbow_appears.mp4")
    for (dir in dirs) {
        for (name in names) {
            val f = File(dir, name)
            if (f.isFile) return f
        }
    }
    return null
}

/**
 * Plays an optional external ceremony video silently over a [TextureView] if provided in the
 * user's `.mkwpack`, or falls back cleanly to the built-in original [CeremonyPrismHorizonCanvas]
 * so the native BWAV engine remains the sole owner of ceremony audio (`Dm_RainbowAppears`).
 */
@Composable
internal fun CeremonyVideoBackdrop(
    playbackPositionMs: Long,
    modifier: Modifier = Modifier,
    audioDurationMs: Int = 68_000,
    packDirectory: File? = null,
    onVideoStatusChanged: (ready: Boolean, durationMs: Int) -> Unit = { _, _ -> },
    onVideoCompleted: () -> Unit = {},
) {
    val context = LocalContext.current
    val externalVideo = remember(context, packDirectory) { resolveExternalCeremonyVideo(context, packDirectory) }
    var useFallbackCanvas by remember(externalVideo) { mutableStateOf(externalVideo == null) }

    if (useFallbackCanvas || externalVideo == null) {
        LaunchedEffect(audioDurationMs) {
            onVideoStatusChanged(true, audioDurationMs.coerceAtLeast(6_500))
        }
        CeremonyPrismHorizonCanvas(
            playbackPositionMs = playbackPositionMs,
            durationMs = audioDurationMs.coerceAtLeast(6_500),
            modifier = modifier,
        )
        return
    }

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
                                player.setDataSource(externalVideo.path)
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
                                    useFallbackCanvas = true
                                    true
                                }
                                player.prepareAsync()
                            }.onFailure {
                                prepared = false
                                useFallbackCanvas = true
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

/**
 * Original procedural cosmic aurora & prismatic rainbow road Canvas celebration when no external
 * ceremony video is packaged inside the user's pack.
 */
@Composable
private fun CeremonyPrismHorizonCanvas(
    playbackPositionMs: Long,
    durationMs: Int,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "ceremony-prism")
    val continuousPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "ceremony-orbit",
    )
    val trackProgress = (playbackPositionMs.toFloat() / durationMs.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val phase = ((continuousPhase + trackProgress * 2.5f) * 2.0 * PI).toFloat()

    val bands = remember {
        listOf(
            Color(0xFFFF3B5C),
            Color(0xFFFF8A1F),
            Color(0xFFFFD83B),
            Color(0xFF39D98A),
            Color(0xFF28B5F4),
            Color(0xFF6366F1),
            Color(0xFFD946EF),
        )
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF030511),
                    Color(0xFF0A102E),
                    Color(0xFF161347),
                    Color(0xFF060818),
                ),
            ),
        )

        val horizonX = size.width * 0.5f + cos(phase * 0.5).toFloat() * size.width * 0.04f
        val horizonY = size.height * 0.44f
        val glowRadius = size.maxDimension * 0.65f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x55A78BFA),
                    Color(0x2838BDF8),
                    Color.Transparent,
                ),
                center = Offset(horizonX, horizonY),
                radius = glowRadius,
            ),
            radius = glowRadius,
            center = Offset(horizonX, horizonY),
        )

        // Subtle drifting starlight particles across upper vault.
        for (i in 0 until 42) {
            val seed = (i * 7919) % 997
            val sx = ((seed / 997f) + continuousPhase * (0.04f + (i % 5) * 0.015f)) % 1f * size.width
            val sy = ((seed * 31 % 617) / 617f) * size.height * 0.85f
            val twinkle = 0.25f + 0.65f * abs(sin((phase * 1.5f + i * 0.7f).toDouble())).toFloat()
            val radius = (1.5f + (i % 3) * 1.1f) * density
            drawCircle(
                color = Color.White.copy(alpha = twinkle),
                radius = radius,
                center = Offset(sx, sy),
            )
        }

        // Perspective seven-band spectral ribbon sweeping from the horizon toward foreground.
        val bandHalf = size.width * 0.14f
        bands.forEachIndexed { idx, color ->
            val offsetNorm = (idx - 3) / 3.5f
            val sway = sin((phase + idx * 0.22f).toDouble()).toFloat() * size.width * 0.06f
            val startX = horizonX + offsetNorm * size.width * 0.025f
            val endX = size.width * 0.5f + offsetNorm * bandHalf * 2.2f + sway
            val ctrlX = (startX + endX) * 0.5f - sway * 0.7f
            val ctrlY = size.height * 0.68f
            val path = Path().apply {
                moveTo(startX, horizonY)
                quadraticTo(ctrlX, ctrlY, endX, size.height * 1.05f)
            }
            drawPath(
                path = path,
                color = color.copy(alpha = 0.72f),
                style = Stroke(width = (10f + idx * 0.6f) * density, cap = StrokeCap.Round),
            )
        }
    }
}
