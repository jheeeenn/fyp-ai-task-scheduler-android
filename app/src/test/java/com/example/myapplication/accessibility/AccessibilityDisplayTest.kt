package com.example.myapplication.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityDisplayTest {
    @Test
    fun largeTextOffRetainsSystemFontScale() {
        assertEquals(1.3f, AccessibilityTextScale.resolveFontScale(1.3f, false), 0.0001f)
    }

    @Test
    fun largeTextOnAddsNamedMultiplierToSystemFontScale() {
        assertEquals(
            1.3f * AccessibilityTextScale.LARGE_TEXT_MULTIPLIER,
            AccessibilityTextScale.resolveFontScale(1.3f, true),
            0.0001f
        )
        assertEquals(1.22f, AccessibilityTextScale.LARGE_TEXT_MULTIPLIER, 0.0001f)
    }

    @Test
    fun repeatedLifecycleApplicationUsesStableSystemBaseline() {
        val systemBaseline = 1.15f
        val firstApplication = AccessibilityTextScale.resolveFontScale(systemBaseline, true)
        val secondApplication = AccessibilityTextScale.resolveFontScale(systemBaseline, true)

        assertEquals(firstApplication, secondApplication, 0.0001f)
        assertTrue(secondApplication < firstApplication * AccessibilityTextScale.LARGE_TEXT_MULTIPLIER)
    }

    @Test
    fun largeTextAndHighContrastRemainIndependent() {
        val combined = AccessibilityDisplayMode(
            largeTextEnabled = true,
            highContrastEnabled = true
        )

        assertEquals(true, combined.largeTextEnabled)
        assertEquals(true, combined.highContrastEnabled)
    }
}
