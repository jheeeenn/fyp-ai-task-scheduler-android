package com.example.myapplication

class TaskCardGestureActions(
    private val read: () -> Unit,
    private val openDetails: () -> Unit
) {
    fun onSingleTapConfirmed(): Boolean {
        read()
        return true
    }

    fun onDoubleTap(): Boolean {
        openDetails()
        return true
    }

    fun onScroll(): Boolean = true
}
