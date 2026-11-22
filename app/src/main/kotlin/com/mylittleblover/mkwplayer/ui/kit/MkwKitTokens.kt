package com.mylittleblover.mkwplayer.ui.kit

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * Single source of truth for the MKW Player custom UI component library (`ui.kit`).
 *
 * Visual language ("Porcelain & Hairline Craft"):
 * - Crisp high-key `#FFFFFF` / `#F8F8FA` / `#F5F5F8` surfaces with zero murky elevation tint.
 * - Consistent `1.dp` hairline borders (`#E2E4EA`), reserving elevation shadows strictly for
 *   floating docks, overlays, and menus.
 * - MKW Crimson (`#C52D49`) + Soft Rose (`#FCE8ED` / `#851C32`) paired with Charcoal Ink
 *   (`#20232B`) for primary transport controls and high-contrast chrome.
 * - Five strict corner radii (`thumb`, `control`, `card`, `sheet`, `pill`) eliminating ad-hoc
 *   `4.dp`/`8.dp`/`20.dp`/`32.dp` mismatches.
 */
internal object MkwKitTokens {

    object Palette {
        val ground = Color(0xFFFFFFFF)
        val card = Color(0xFFF8F8FA)
        val inset = Color(0xFFF5F5F8)
        val track = Color(0xFFF0F1F5)
        val trackStrong = Color(0xFFE8E9EF)
        val hairline = Color(0xFFE2E4EA)

        val ink = Color(0xFF20232B)
        val inkSecondary = Color(0xFF565A66)
        val outlineInk = Color(0xFF747884)

        val crimson = Color(0xFFC52D49)
        val crimsonDeep = Color(0xFF851C32)
        val rose = Color(0xFFFCE8ED)
        val roseHover = Color(0xFFF9DCE4)

        val danger = Color(0xFFB3261E)
        val dangerTint = Color(0xFFFDECEC)
        val photoScrim = Color(0x66000000)
    }

    object Radius {
        val thumb = 10.dp
        val control = 14.dp
        val card = 18.dp
        val sheet = 24.dp
        val pill = 999.dp
    }

    object Shape {
        val thumb = RoundedCornerShape(Radius.thumb)
        val control = RoundedCornerShape(Radius.control)
        val card = RoundedCornerShape(Radius.card)
        val dialog = RoundedCornerShape(Radius.card + 4.dp)
        val sheetTop = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet)
        val pill = CircleShape
    }

    object Spacing {
        val xxs = 4.dp
        val xs = 8.dp
        val sm = 12.dp
        val md = 16.dp
        val lg = 20.dp
        val xl = 24.dp
    }

    object Stroke {
        val hairline = 0.75.dp
        val border = 1.dp
        val focused = 1.5.dp
    }

    object Size {
        val touchMin = 48.dp
        val buttonCompact = 36.dp
        val buttonRegular = 44.dp
        val buttonHero = 48.dp
        val pillHeight = 36.dp
        val switchWidth = 48.dp
        val switchHeight = 28.dp
        val switchThumb = 22.dp
        val indicator = 20.dp
        val optionRowMin = 52.dp
        val iconCircleVisual = 38.dp
    }

    /** Enforces fixed-width tabular numerals for timers, play counts, frequencies, and ratios. */
    fun tabular(style: TextStyle): TextStyle = style.copy(fontFeatureSettings = "tnum")
}
