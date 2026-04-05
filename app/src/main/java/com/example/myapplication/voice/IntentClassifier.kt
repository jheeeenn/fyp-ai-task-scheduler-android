// TO BE DISCARDED.................

package com.example.myapplication.voice

object IntentClassifier {

    fun classify(normalizedText: String): IntentType {
        return when {
            matchesAny(normalizedText, listOf("change title", "edit title")) ->
                IntentType.CHANGE_TITLE

            matchesAny(normalizedText, listOf("change date", "edit date")) ->
                IntentType.CHANGE_DATE

            matchesAny(normalizedText, listOf("change time", "edit time")) ->
                IntentType.CHANGE_TIME

            matchesAny(normalizedText, listOf("change it to", "change to", "make it", "rename it to")) ->
                IntentType.CORRECTION_INLINE

            matchesAny(normalizedText, listOf("set title", "title", "change title")) ->
                IntentType.SET_TITLE

            matchesAny(normalizedText, listOf("set date", "change date", "date")) ->
                IntentType.SET_DATE

            matchesAny(normalizedText, listOf("set time", "change time", "time")) ->
                IntentType.SET_TIME

            matchesAny(normalizedText, listOf("create task", "add task", "new task", "remind me", "set a reminder", "i need to remember")) ->
                IntentType.CREATE_TASK

            matchesAny(normalizedText, listOf("save task", "save", "save it", "confirm", "done creating")) ->
                IntentType.SAVE_TASK

            matchesAny(normalizedText, listOf("yes", "yes please", "correct", "that's right", "confirm yes")) ->
                IntentType.CONFIRM_YES

            matchesAny(normalizedText, listOf("no", "nope", "not yet", "wrong", "confirm no")) ->
                IntentType.CONFIRM_NO

            matchesAny(normalizedText, listOf("cancel", "stop", "never mind")) ->
                IntentType.CANCEL

            matchesAny(normalizedText, listOf("go home", "home", "back home")) ->
                IntentType.GO_HOME

            matchesAny(normalizedText, listOf("help", "what can you do")) ->
                IntentType.HELP

            else -> IntentType.UNKNOWN
        }
    }

    private fun matchesAny(text: String, patterns: List<String>): Boolean {
        return patterns.any { pattern -> text.startsWith(pattern) || text.contains(pattern) }
    }
}