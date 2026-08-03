package com.example.myapplication

class VoiceFirstNavigationCoordinator(
    private val speak: (String, (Boolean) -> Unit) -> Unit
) {
    private var navigationPending = false
    private var requestGeneration = 0L

    fun request(announcement: String, navigate: () -> Unit): Boolean {
        if (navigationPending) return false
        navigationPending = true
        requestGeneration += 1
        val capturedGeneration = requestGeneration
        try {
            speak(announcement) { speechSucceeded ->
                if (!navigationPending || capturedGeneration != requestGeneration) return@speak
                navigationPending = false
                if (speechSucceeded) navigate()
            }
        } catch (_: RuntimeException) {
            navigationPending = false
            return false
        }
        return true
    }

    fun cancelPending() {
        requestGeneration += 1
        navigationPending = false
    }

    fun isPending(): Boolean = navigationPending
}
