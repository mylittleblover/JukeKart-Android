package com.mylittleblover.mkwplayer.ui.kit

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mylittleblover.mkwplayer.ui.AppMotion
import com.mylittleblover.mkwplayer.ui.DragHandlePill
import com.mylittleblover.mkwplayer.ui.rememberPressScale
import com.mylittleblover.mkwplayer.ui.rememberReducedMotion

internal data class MkwSelectionOption<T>(
    val value: T,
    val label: String,
    val subtitle: String? = null,
    val selected: Boolean = false,
)

/**
 * Unified MKW bottom sheet container with consistent `DragHandlePill()`, pure white ground,
 * `24.dp` top corners, built-in `navigationBarsPadding()`, optional title/subtitle header, and
 * optional pinned footer slot so primary CTAs stay reachable above system navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MkwBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    title: String? = null,
    subtitle: String? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.statusBarsPadding(),
        sheetState = sheetState,
        shape = MkwKitTokens.Shape.sheetTop,
        containerColor = MkwKitTokens.Palette.ground,
        tonalElevation = 0.dp,
        dragHandle = { DragHandlePill() },
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = MkwKitTokens.Spacing.lg),
        ) {
            if (!title.isNullOrBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MkwKitTokens.Spacing.xl, vertical = MkwKitTokens.Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MkwKitTokens.Palette.ink,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MkwKitTokens.Palette.inkSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            content()
            if (footer != null) {
                MkwDivider(modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MkwKitTokens.Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(MkwKitTokens.Spacing.xs),
                    content = footer,
                )
            }
        }
    }
}

/**
 * Signature MKW selector row (`14.dp` porcelain box + `1.dp` hairline border + soft rose value
 * pill) opening an [MkwBottomSheet] populated with unified [MkwOptionRow] + [MkwRadioMark] items.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> MkwSelectorRow(
    label: String,
    selectedLabel: String,
    sheetTitle: String,
    options: List<MkwSelectionOption<T>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    selectedValue: T? = null,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    var open by rememberSaveable(sheetTitle) { mutableStateOf(false) }
    val (pressMod, interactionSource) = rememberPressScale(pressedScale = 0.985f)

    Surface(
        onClick = { if (enabled) open = true },
        enabled = enabled,
        interactionSource = interactionSource,
        shape = MkwKitTokens.Shape.control,
        color = MkwKitTokens.Palette.card,
        tonalElevation = 0.dp,
        border = BorderStroke(MkwKitTokens.Stroke.border, MkwKitTokens.Palette.hairline),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 44.dp else 50.dp)
            .then(if (enabled) pressMod else Modifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = if (compact) 8.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MkwKitTokens.Palette.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = MkwKitTokens.Shape.pill,
                color = MkwKitTokens.Palette.rose,
                border = BorderStroke(MkwKitTokens.Stroke.hairline, MkwKitTokens.Palette.crimson.copy(alpha = 0.25f)),
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = selectedLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MkwKitTokens.Palette.crimsonDeep,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MkwKitTokens.Palette.crimsonDeep,
                    )
                }
            }
        }
    }

    if (open) {
        MkwBottomSheet(
            onDismissRequest = { open = false },
            title = sheetTitle,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .selectableGroup()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                options.forEach { option ->
                    val isSelected = option.selected || (selectedValue != null && option.value == selectedValue)
                    MkwOptionRow(
                        headline = option.label,
                        supporting = option.subtitle,
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = {
                            open = false
                            onSelect(option.value)
                        },
                        trailing = {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MkwKitTokens.Palette.crimson,
                                )
                            } else {
                                MkwRadioMark(selected = false)
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * Custom porcelain modal dialog matching [MkwCard] geometry and [MkwButton] actions, replacing raw
 * MD3 `AlertDialog` popups.
 */
@Composable
internal fun MkwDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = true),
    ) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = MkwKitTokens.Shape.dialog,
            color = MkwKitTokens.Palette.ground,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(MkwKitTokens.Stroke.border, MkwKitTokens.Palette.hairline),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(MkwKitTokens.Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(MkwKitTokens.Spacing.sm),
            ) {
                if (icon != null) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        icon()
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MkwKitTokens.Palette.ink,
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(MkwKitTokens.Spacing.xs),
                    content = content,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (dismissButton != null) dismissButton()
                    confirmButton()
                }
            }
        }
    }
}

/** Crisp white hairline-bordered overflow menu replacing raw MD3 `DropdownMenu`. */
@Composable
internal fun MkwDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = MkwKitTokens.Shape.card,
        containerColor = MkwKitTokens.Palette.ground,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(MkwKitTokens.Stroke.border, MkwKitTokens.Palette.hairline),
        content = content,
    )
}

/** Unified popup item sized cleanly (`44.dp` min) inside [MkwDropdownMenu]. */
@Composable
internal fun MkwDropdownItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    val tint = when {
        !enabled -> MkwKitTokens.Palette.inkSecondary.copy(alpha = 0.4f)
        destructive -> MkwKitTokens.Palette.danger
        else -> MkwKitTokens.Palette.ink
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(MkwKitTokens.Shape.control)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp), tint = tint)
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Spring-sliding porcelain segmented bar replacing boxed chip-tabs and stock MD3 `TabRow`.
 */
@Composable
internal fun <T> MkwSegmentedBar(
    items: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val reduceMotion = rememberReducedMotion()
    val selectedIdx = items.indexOf(selected).coerceAtLeast(0)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 42.dp)
            .clip(MkwKitTokens.Shape.control)
            .background(MkwKitTokens.Palette.inset)
            .border(MkwKitTokens.Stroke.border, MkwKitTokens.Palette.hairline, MkwKitTokens.Shape.control)
            .padding(3.dp)
            .selectableGroup(),
    ) {
        val segmentWidth = maxWidth / items.size
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIdx,
            animationSpec = if (reduceMotion) tween(0) else AppMotion.snapDpSpring,
            label = "mkw-segment-thumb",
        )
        Surface(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(segmentWidth)
                .heightIn(min = 36.dp),
            shape = MkwKitTokens.Shape.thumb,
            color = MkwKitTokens.Palette.ground,
            tonalElevation = 0.dp,
            shadowElevation = 1.dp,
            border = BorderStroke(MkwKitTokens.Stroke.hairline, MkwKitTokens.Palette.hairline),
            content = {},
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            items.forEach { item ->
                val isSelected = item == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 36.dp)
                        .clip(MkwKitTokens.Shape.thumb)
                        .clickable(role = Role.Tab) { onSelect(item) }
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = labelOf(item),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) MkwKitTokens.Palette.ink else MkwKitTokens.Palette.inkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

internal data class MkwNavDestination<T>(
    val id: T,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

/**
 * Porcelain bottom navigation bar matching [MkwKitTokens]: `#FFFFFF` ground, top hairline stroke,
 * rose active indicator pill, and spring press scale without MD3 tonal-elevation gray haze.
 */
@Composable
internal fun <T> MkwBottomNavDock(
    destinations: List<MkwNavDestination<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MkwKitTokens.Palette.ground,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            MkwDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                destinations.forEach { dest ->
                    MkwBottomNavItem(
                        destination = dest,
                        isSelected = dest.id == selected,
                        onClick = { onSelect(dest.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> RowScope.MkwBottomNavItem(
    destination: MkwNavDestination<T>,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val (pressScale, src) = rememberPressScale(pressedScale = 0.95f)
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(MkwKitTokens.Shape.control)
            .clickable(
                interactionSource = src,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .then(pressScale)
            .semantics { selected = isSelected }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Surface(
            shape = MkwKitTokens.Shape.pill,
            color = if (isSelected) MkwKitTokens.Palette.rose else Color.Transparent,
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (isSelected) MkwKitTokens.Palette.crimsonDeep else MkwKitTokens.Palette.inkSecondary,
                )
            }
        }
        Text(
            text = destination.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (isSelected) MkwKitTokens.Palette.ink else MkwKitTokens.Palette.inkSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
