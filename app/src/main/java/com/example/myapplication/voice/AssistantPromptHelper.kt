package com.example.myapplication.voice

class AssistantPromptHelper(
    private val session: AssistantVoiceSession,
    private val responses: AssistantResponseManager
) {
    fun askTitle() {
        session.getBottomSheet()?.showAssistantHint(responses.hintTitle())
        session.speak(responses.askTaskTitle(), listenAgain = true)
    }

    fun askDate() {
        session.getBottomSheet()?.showAssistantHint(responses.hintDate())
        session.speak(responses.askTaskDate(), listenAgain = true)
    }

    fun askTime() {
        session.getBottomSheet()?.showAssistantHint(responses.hintTime())
        session.speak(responses.askTaskTime(), listenAgain = true)
    }

    fun askWhatToChange() {
        session.getBottomSheet()?.showAssistantHint(responses.hintChangeFields())
        session.speak(responses.askWhatToChange(), listenAgain = true)
    }

    fun askSaveTask(summary: String) {
        session.getBottomSheet()?.showAssistantHint(responses.hintYesNo())
        session.speak(responses.confirmTaskSummary(summary), listenAgain = true)
    }

    fun askSaveChanges(summary: String) {
        session.getBottomSheet()?.showAssistantHint(responses.hintYesNo())
        session.speak(responses.confirmEditSummary(summary), listenAgain = true)
    }

    fun ambiguity(title1: String, title2: String) {
        session.getBottomSheet()?.showAssistantHint(responses.hintAmbiguityChoice())
        session.speak(responses.taskMatchAmbiguous(title1, title2), listenAgain = true)
    }

    fun retryAmbiguity() {
        session.getBottomSheet()?.showAssistantHint(responses.hintAmbiguityChoice())
        session.speak(responses.taskMatchAmbiguityRetry(), listenAgain = true)
    }

    fun resetAmbiguity() {
        session.getBottomSheet()?.showAssistantHint("")
        session.speak(responses.taskMatchAmbiguityReset(), listenAgain = true)
    }

    fun speakInfo(text: String, listenAgain: Boolean = true, hint: String = "") {
        session.getBottomSheet()?.showAssistantHint(hint)
        session.speak(text, listenAgain = listenAgain)
    }
}