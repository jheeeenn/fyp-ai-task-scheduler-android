package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskNavigationSpeechRendererTest {
    @Test
    fun openingDetailsNamesTheSelectedTask() {
        assertEquals(
            "Opening details for Take medicine.",
            TaskNavigationSpeechRenderer.openingDetails("Take medicine")
        )
    }
}
