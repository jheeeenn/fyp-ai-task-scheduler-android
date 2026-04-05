package com.example.myapplication.voice

data class PendingTaskState(
    var title: String? = null,
    var dateText: String? = null,
    var timeText: String? = null
) {
    fun isReadyToSave(): Boolean {
        return !title.isNullOrBlank() &&
                !dateText.isNullOrBlank() &&
                !timeText.isNullOrBlank()
    }

    fun clear() {
        title = null
        dateText = null
        timeText = null
    }

}