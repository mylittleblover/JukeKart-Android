package com.mylittleblover.mkwplayer.ui.interactive

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mylittleblover.mkwplayer.ui.kit.MkwSelectionOption
import com.mylittleblover.mkwplayer.ui.kit.MkwSelectorRow

internal data class DrawerOption<T>(
    val value: T,
    val label: String,
    val subtitle: String? = null,
    val selected: Boolean = false,
)

/**
 * Compatibility facade preserving existing interactive callers while delegating to [MkwSelectorRow]
 * in the unified `ui.kit` component library.
 */
@Composable
internal fun <T> SelectionDrawerRow(
    label: String,
    selectedLabel: String,
    sheetTitle: String,
    options: List<DrawerOption<T>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    selectedValue: T? = null,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    MkwSelectorRow(
        label = label,
        selectedLabel = selectedLabel,
        sheetTitle = sheetTitle,
        options = options.map { MkwSelectionOption(it.value, it.label, it.subtitle, it.selected) },
        onSelect = onSelect,
        modifier = modifier,
        selectedValue = selectedValue,
        enabled = enabled,
        compact = compact,
    )
}
