package com.mylittleblover.mkwplayer.ui.kit

import androidx.compose.ui.graphics.Color
import com.mylittleblover.mkwplayer.ui.Contrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MkwKitTest {

    @Test
    fun palettePairingsMeetContrastMinimums() {
        assertTrue(Contrast.passesNormalText(MkwKitTokens.Palette.ink, MkwKitTokens.Palette.ground))
        assertTrue(Contrast.passesNormalText(MkwKitTokens.Palette.ink, MkwKitTokens.Palette.card))
        assertTrue(Contrast.passesNormalText(MkwKitTokens.Palette.ink, MkwKitTokens.Palette.inset))
        assertTrue(Contrast.passesNormalText(MkwKitTokens.Palette.inkSecondary, MkwKitTokens.Palette.ground))
        assertTrue(Contrast.passesNormalText(MkwKitTokens.Palette.inkSecondary, MkwKitTokens.Palette.card))
        assertTrue(Contrast.passesNormalText(MkwKitTokens.Palette.crimsonDeep, MkwKitTokens.Palette.rose))
        assertTrue(Contrast.passesNormalText(MkwKitTokens.Palette.danger, MkwKitTokens.Palette.ground))
        assertTrue(Contrast.passesNormalText(MkwKitTokens.Palette.danger, MkwKitTokens.Palette.dangerTint))
        assertTrue(Contrast.passesNormalText(Color.White, MkwKitTokens.Palette.crimson))
        assertTrue(Contrast.passesNormalText(Color.White, MkwKitTokens.Palette.ink))
    }

    @Test
    fun radiusScaleIsStrictlyMonotonicAndAccessible() {
        assertTrue(MkwKitTokens.Radius.thumb < MkwKitTokens.Radius.control)
        assertTrue(MkwKitTokens.Radius.control < MkwKitTokens.Radius.card)
        assertTrue(MkwKitTokens.Radius.card < MkwKitTokens.Radius.sheet)
        assertTrue(MkwKitTokens.Radius.sheet < MkwKitTokens.Radius.pill)
        assertTrue(MkwKitTokens.Size.touchMin.value >= 48f)
        assertTrue(MkwKitTokens.Size.optionRowMin.value >= 48f)
    }

    @Test
    fun sliderMathNormalizesAndSnapsCenterZeroAndStandardRanges() {
        assertEquals(0.5f, MkwSliderMath.fractionForValue(0f, -12f..12f), 1e-5f)
        assertEquals(0f, MkwSliderMath.fractionForValue(-20f, -12f..12f), 1e-5f)
        assertEquals(1f, MkwSliderMath.fractionForValue(20f, -12f..12f), 1e-5f)

        // 0.5f step quantization over -12..12 -> 47 intermediate steps
        val snapped = MkwSliderMath.snapValue(3.4f, -12f..12f, steps = 47)
        assertEquals(3.5f, snapped, 1e-5f)

        // Positive diverging bar fills from center 0.5 to 0.75
        val (negStart, negEnd) = MkwSliderMath.fillFractions(0.25f, -12f..12f, centerZero = true)
        assertEquals(0.25f, negStart, 1e-5f)
        assertEquals(0.5f, negEnd, 1e-5f)

        val (stdStart, stdEnd) = MkwSliderMath.fillFractions(0.65f, 0f..1f, centerZero = false)
        assertEquals(0f, stdStart, 1e-5f)
        assertEquals(0.65f, stdEnd, 1e-5f)
    }
}
