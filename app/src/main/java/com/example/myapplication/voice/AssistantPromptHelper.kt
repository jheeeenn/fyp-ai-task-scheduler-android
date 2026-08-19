package com.example.myapplication.voice

class AssistantPromptHelper(
    private val session: AssistantVoiceSession,
    private val responses: AssistantResponseManager
) {
    fun askTitle() {
        session.speak(responses.askTaskTitle(), listenAgain = true)
    }

    fun askDate() {
        session.speak(responses.askTaskDate(), listenAgain = true)
    }

    fun askTime() {
        session.speak(responses.askTaskTime(), listenAgain = true)
    }

    fun askWhatToChange() {
        session.speak(responses.askWhatToChange(), listenAgain = true)
    }

    fun askSaveTask(summary: String) {
        session.speak(responses.confirmTaskSummary(summary), listenAgain = true)
    }

    fun askSaveChanges(summary: String) {
        session.speak(responses.confirmEditSummary(summary), listenAgain = true)
    }

    fun ambiguity(title1: String, title2: String) {
        session.speak(responses.taskMatchAmbiguous(title1, title2), listenAgain = true)
    }

    fun retryAmbiguity() {
        session.speak(responses.taskMatchAmbiguityRetry(), listenAgain = true)
    }

    fun resetAmbiguity() {
        session.speak(responses.taskMatchAmbiguityReset(), listenAgain = true)
    }

    fun speakInfo(text: String, listenAgain: Boolean = true) {
        session.speak(text, listenAgain = listenAgain)
    }
}
