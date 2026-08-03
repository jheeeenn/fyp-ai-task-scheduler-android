package com.example.myapplication

class VoiceFirstTouchInteraction(
    private val singleTap: () -> Unit,
    private val doubleTap: () -> Unit,
    private val longPress: () -> Unit
) {
    fun onSingleTapConfirmed(): Boolean {
        singleTap()
        return true
    }

    fun onDoubleTap(): Boolean {
        doubleTap()
        return true
    }

    fun onLongPress() {
        longPress()
    }

    fun onScroll(): Boolean = false
}
