package com.example.myapplication.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechRatePresetTest {
    @Test
    fun presetsExposeTheBoundedProductRates() {
        assertEquals(0.80f, SpeechRatePreset.SLOW.rate, 0.0f)
        assertEquals(1.00f, SpeechRatePreset.NORMAL.rate, 0.0f)
        assertEquals(1.25f, SpeechRatePreset.FAST.rate, 0.0f)
        assertEquals(1.50f, SpeechRatePreset.VERY_FAST.rate, 0.0f)
    }

    @Test
    fun parsingUsesDisplayNamesAndFallsBackSafely() {
        assertEquals(SpeechRatePreset.SLOW, SpeechRatePreset.fromStoredValue("Slow"))
        assertEquals(SpeechRatePreset.NORMAL, SpeechRatePreset.fromStoredValue("Normal"))
        assertEquals(SpeechRatePreset.FAST, SpeechRatePreset.fromStoredValue("Fast"))
        assertEquals(SpeechRatePreset.VERY_FAST, SpeechRatePreset.fromStoredValue("Very Fast"))
        assertEquals(SpeechRatePreset.NORMAL, SpeechRatePreset.fromStoredValue(null))
        assertEquals(SpeechRatePreset.NORMAL, SpeechRatePreset.fromStoredValue("corrupted"))
    }
}
