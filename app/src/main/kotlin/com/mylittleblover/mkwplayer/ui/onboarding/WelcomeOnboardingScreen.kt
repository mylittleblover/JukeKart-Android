package com.mylittleblover.mkwplayer.ui.onboarding

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mylittleblover.mkwplayer.R
import com.mylittleblover.mkwplayer.pack.PackImportPhase
import com.mylittleblover.mkwplayer.pack.PackImportUiState
import com.mylittleblover.mkwplayer.ui.AppMotion
import com.mylittleblover.mkwplayer.ui.SmoothProgressIndicator
import com.mylittleblover.mkwplayer.ui.kit.MkwBadge
import com.mylittleblover.mkwplayer.ui.kit.MkwBadgeTone
import com.mylittleblover.mkwplayer.ui.kit.MkwButtonSize
import com.mylittleblover.mkwplayer.ui.kit.MkwCard
import com.mylittleblover.mkwplayer.ui.kit.MkwGhostButton
import com.mylittleblover.mkwplayer.ui.kit.MkwKitTokens
import com.mylittleblover.mkwplayer.ui.kit.MkwPrimaryButton
import com.mylittleblover.mkwplayer.ui.kit.MkwSecondaryButton
import com.mylittleblover.mkwplayer.ui.kit.MkwSurfaceTone
import com.mylittleblover.mkwplayer.ui.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun WelcomeOnboardingScreen(
    notificationGranted: Boolean,
    busy: Boolean,
    importState: PackImportUiState,
    onRequestNotificationPermission: () -> Unit,
    onPickPack: () -> Unit,
    onResumeImport: () -> Unit,
    onRepickImport: () -> Unit,
    onCancelImport: () -> Unit,
    onSkipForLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReducedMotion()
    val density = LocalDensity.current
    val leadProgress = remember(reduceMotion) { Animatable(if (reduceMotion) 1f else 0f) }
    val supportProgress = remember(reduceMotion) { Animatable(if (reduceMotion) 1f else 0f) }

    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            leadProgress.snapTo(1f)
            supportProgress.snapTo(1f)
        } else {
            launch {
                leadProgress.animateTo(1f, tween(durationMillis = 260, easing = AppMotion.easeOut))
            }
            launch {
                delay(70L)
                supportProgress.animateTo(1f, tween(durationMillis = 260, easing = AppMotion.easeOut))
            }
        }
    }

    val leadShiftPx = with(density) { 12.dp.toPx() }
    val supportShiftPx = with(density) { 8.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MkwKitTokens.Palette.ground)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 540.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Lead: Welcome hero & core product overview
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val p = leadProgress.value
                            alpha = p
                            if (!reduceMotion) translationY = (1f - p) * leadShiftPx
                        },
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = MkwKitTokens.Shape.card,
                        color = MkwKitTokens.Palette.rose,
                        border = BorderStroke(MkwKitTokens.Stroke.hairline, MkwKitTokens.Palette.crimson.copy(alpha = 0.22f)),
                        modifier = Modifier.size(56.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.GraphicEq,
                                contentDescription = null,
                                tint = MkwKitTokens.Palette.crimson,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.onboarding_welcome_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MkwKitTokens.Palette.ink,
                    )
                    Text(
                        text = stringResource(R.string.onboarding_welcome_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MkwKitTokens.Palette.inkSecondary,
                    )
                    MkwCard(
                        tone = MkwSurfaceTone.Porcelain,
                        contentPadding = PaddingValues(14.dp),
                    ) {
                        OnboardingFeatureRow(
                            icon = Icons.Outlined.GraphicEq,
                            text = stringResource(R.string.onboarding_feature_courses),
                        )
                        OnboardingFeatureRow(
                            icon = Icons.Filled.Map,
                            text = stringResource(R.string.onboarding_feature_routes),
                        )
                        OnboardingFeatureRow(
                            icon = Icons.Outlined.Album,
                            text = stringResource(R.string.onboarding_feature_jukebox),
                        )
                    }
                }

                // Support: Step 1 (Notifications) + Step 2 (Pack import) + Skip
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val p = supportProgress.value
                            alpha = p
                            if (!reduceMotion) translationY = (1f - p) * supportShiftPx
                        },
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Step 1: Notification Permission
                    MkwCard(
                        tone = MkwSurfaceTone.Porcelain,
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            StepNumberPill(number = "1")
                            Text(
                                text = stringResource(R.string.onboarding_step_permission_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MkwKitTokens.Palette.ink,
                                modifier = Modifier.weight(1f),
                            )
                            if (notificationGranted) {
                                MkwBadge(
                                    text = stringResource(R.string.onboarding_permission_granted),
                                    tone = MkwBadgeTone.Rose,
                                    tabular = false,
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.onboarding_step_permission_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MkwKitTokens.Palette.inkSecondary,
                        )
                        if (!notificationGranted) {
                            MkwSecondaryButton(
                                text = stringResource(R.string.onboarding_permission_action),
                                onClick = onRequestNotificationPermission,
                                leadingIcon = Icons.Filled.Notifications,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    // Step 2: Import .mkwpack
                    MkwCard(
                        tone = MkwSurfaceTone.Porcelain,
                        contentPadding = PaddingValues(16.dp),
                        modifier = Modifier.animateContentSize(
                            tween(if (reduceMotion) 0 else 220, easing = AppMotion.easeOut),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            StepNumberPill(number = "2")
                            Text(
                                text = stringResource(R.string.onboarding_step_pack_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MkwKitTokens.Palette.ink,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Text(
                            text = stringResource(R.string.onboarding_step_pack_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MkwKitTokens.Palette.inkSecondary,
                        )

                        when {
                            importState.isActive -> {
                                if (importState.totalMembers > 0) {
                                    Text(
                                        text = stringResource(
                                            R.string.settings_import_progress_members,
                                            importState.processedMembers,
                                            importState.totalMembers,
                                            importState.currentMember.ifBlank { importState.displayName },
                                        ),
                                        style = MkwKitTokens.tabular(MaterialTheme.typography.bodySmall),
                                        color = MkwKitTokens.Palette.inkSecondary,
                                    )
                                }
                                val progress = if (importState.phase == PackImportPhase.PROBING) {
                                    0.15f
                                } else {
                                    importState.fraction
                                }
                                SmoothProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
                                if (importState.canCancel) {
                                    Text(
                                        text = stringResource(R.string.settings_importing),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MkwKitTokens.Palette.inkSecondary,
                                    )
                                    MkwSecondaryButton(
                                        text = stringResource(R.string.settings_import_cancel),
                                        onClick = onCancelImport,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                } else {
                                    Text(
                                        text = stringResource(R.string.settings_import_committing),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MkwKitTokens.Palette.crimson,
                                    )
                                }
                            }
                            importState.phase == PackImportPhase.RESUMABLE && importState.checkpoint != null -> {
                                val cp = importState.checkpoint
                                Text(
                                    text = stringResource(
                                        R.string.settings_import_resumable_hint,
                                        cp.displayName,
                                        cp.completedMembers.size,
                                        cp.totalMembers,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MkwKitTokens.Palette.crimsonDeep,
                                )
                                MkwPrimaryButton(
                                    text = stringResource(R.string.onboarding_import_resumable_cta),
                                    onClick = onResumeImport,
                                    size = MkwButtonSize.Hero,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                MkwSecondaryButton(
                                    text = stringResource(R.string.onboarding_import_cta),
                                    onClick = onPickPack,
                                    enabled = !busy,
                                    leadingIcon = Icons.Filled.FolderOpen,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            importState.phase == PackImportPhase.PAUSED_NEEDS_REPICK && importState.checkpoint != null -> {
                                val cp = importState.checkpoint
                                Text(
                                    text = stringResource(
                                        R.string.settings_import_repick_hint,
                                        cp.displayName,
                                        cp.completedMembers.size,
                                        cp.totalMembers,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MkwKitTokens.Palette.crimsonDeep,
                                )
                                MkwPrimaryButton(
                                    text = stringResource(R.string.settings_import_repick),
                                    onClick = onRepickImport,
                                    size = MkwButtonSize.Hero,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            else -> {
                                if (importState.phase == PackImportPhase.FAILED && !importState.errorReason.isNullOrBlank()) {
                                    Text(
                                        text = importState.errorReason,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MkwKitTokens.Palette.danger,
                                    )
                                }
                                MkwPrimaryButton(
                                    text = stringResource(R.string.onboarding_import_cta),
                                    onClick = onPickPack,
                                    enabled = !busy,
                                    leadingIcon = Icons.Filled.FolderOpen,
                                    size = MkwButtonSize.Hero,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        MkwGhostButton(
                            text = stringResource(R.string.onboarding_skip_later),
                            onClick = onSkipForLater,
                            enabled = !importState.isActive,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun OnboardingFeatureRow(
    icon: ImageVector,
    text: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MkwKitTokens.Palette.crimson,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MkwKitTokens.Palette.ink,
        )
    }
}

@Composable
private fun StepNumberPill(number: String) {
    Surface(
        shape = CircleShape,
        color = MkwKitTokens.Palette.rose,
        border = BorderStroke(MkwKitTokens.Stroke.hairline, MkwKitTokens.Palette.crimson.copy(alpha = 0.25f)),
        modifier = Modifier.size(26.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = number,
                style = MkwKitTokens.tabular(MaterialTheme.typography.labelMedium),
                fontWeight = FontWeight.Bold,
                color = MkwKitTokens.Palette.crimsonDeep,
            )
        }
    }
}
