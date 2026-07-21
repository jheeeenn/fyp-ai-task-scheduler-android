package com.example.myapplication.voice

class CreateDraftMoveInterpreter {
    fun interpret(
        normalizedText: String,
        state: CreateTaskDialogState
    ): CreateDraftMove {
        val text = clean(normalizedText)

        if (isCancellation(text)) return CreateDraftMove.Cancel
        if (state == CreateTaskDialogState.READY_TO_SAVE) return CreateDraftMove.Unknown
        if (isHelpRequest(text)) return CreateDraftMove.RequestHelp

        return when (state) {
            CreateTaskDialogState.IDLE,
            CreateTaskDialogState.WAITING_FOR_TITLE -> interpretTitleInput(text)

            CreateTaskDialogState.WAITING_FOR_DATE ->
                CreateDraftMove.ProvideField(CreateDraftField.DATE, text)

            CreateTaskDialogState.WAITING_FOR_TIME ->
                CreateDraftMove.ProvideField(CreateDraftField.TIME, text)

            CreateTaskDialogState.WAITING_FOR_SAVE_CONFIRMATION ->
                interpretSaveConfirmation(text)

            CreateTaskDialogState.WAITING_FOR_CHANGE_FIELD ->
                interpretChangeFieldSelection(text)

            CreateTaskDialogState.READY_TO_SAVE -> CreateDraftMove.Unknown
        }
    }

    fun isReasonableTitleCandidate(value: String): Boolean {
        val text = clean(value)
        if (text.length < 3) return false
        if (isCancellation(text) || isHelpRequest(text) || isConfirmation(text) || isRejection(text)) {
            return false
        }
        if (parseExplicitFieldChange(text) != null || parseGenericCorrection(text) != null) {
            return false
        }
        if (parseBareField(text) != null) return false
        if (text in controlQuestions) return false
        return true
    }

    fun isConfirmationUtterance(value: String): Boolean = isConfirmation(clean(value))

    private fun interpretTitleInput(text: String): CreateDraftMove {
        parseExplicitFieldChange(text)?.let { return it }
        return if (isReasonableTitleCandidate(text)) {
            CreateDraftMove.ProvideField(CreateDraftField.TITLE, text)
        } else {
            CreateDraftMove.Unknown
        }
    }

    private fun interpretSaveConfirmation(text: String): CreateDraftMove {
        parseExplicitFieldChange(text)?.let { return it }
        if (isConfirmation(text)) return CreateDraftMove.ConfirmSave
        if (isRejection(text)) return CreateDraftMove.RejectSave
        parseGenericCorrection(text)?.let { return it }
        return CreateDraftMove.Unknown
    }

    private fun interpretChangeFieldSelection(text: String): CreateDraftMove {
        parseExplicitFieldChange(text)?.let { return it }
        val field = parseBareField(text) ?: return CreateDraftMove.Unknown
        return CreateDraftMove.ChangeField(field)
    }

    private fun parseExplicitFieldChange(text: String): CreateDraftMove.ChangeField? {
        val command = stripConversationLeadIn(text)

        renamePattern.matchEntire(command)?.let { match ->
            val value = match.groupValues[1].trim().ifBlank { null }
            return CreateDraftMove.ChangeField(CreateDraftField.TITLE, value)
        }

        fieldChangePattern.matchEntire(command)?.let { match ->
            val field = fieldFor(match.groupValues[1]) ?: return null
            val value = match.groupValues[2].trim().ifBlank { null }
            return CreateDraftMove.ChangeField(field, value)
        }

        return null
    }

    private fun parseGenericCorrection(text: String): CreateDraftMove.ApplyUnspecifiedCorrection? {
        val command = stripConversationLeadIn(text)
        genericCorrectionPattern.matchEntire(command)?.let { match ->
            val value = match.groupValues[1].trim()
            if (value.isNotBlank()) return CreateDraftMove.ApplyUnspecifiedCorrection(value)
        }
        makeCorrectionPattern.matchEntire(command)?.let { match ->
            val value = match.groupValues[1].trim()
            if (value.isNotBlank()) return CreateDraftMove.ApplyUnspecifiedCorrection(value)
        }
        return null
    }

    private fun stripConversationLeadIn(value: String): String {
        var text = value
        var previous: String
        do {
            previous = text
            text = text
                .replace(leadingDispositionPattern, "")
                .replace(leadingRequestPattern, "")
                .trim()
        } while (text != previous)
        return text
    }

    private fun parseBareField(text: String): CreateDraftField? {
        val match = bareFieldPattern.matchEntire(text) ?: return null
        return fieldFor(match.groupValues[1])
    }

    private fun fieldFor(value: String): CreateDraftField? = when (value) {
        "title", "name" -> CreateDraftField.TITLE
        "date", "day" -> CreateDraftField.DATE
        "time" -> CreateDraftField.TIME
        else -> null
    }

    private fun isConfirmation(text: String): Boolean = text in confirmations

    private fun isRejection(text: String): Boolean = text in rejections

    private fun isCancellation(text: String): Boolean = text in cancellations

    private fun isHelpRequest(text: String): Boolean = text in helpRequests

    private fun clean(value: String): String = value
        .lowercase()
        .replace(Regex("[.,!?]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private companion object {
        val confirmations = setOf(
            "yes", "yes yes", "yeah", "yep", "sure", "okay", "ok", "alright", "save",
            "confirm", "okay yes", "ok yes"
        )
        val rejections = setOf(
            "no", "no no", "nope", "no thanks", "no need", "do not save", "don't save", "not now"
        )
        val cancellations = setOf(
            "cancel", "cancel this task", "stop creating", "stop creating this task", "discard this task"
        )
        val helpRequests = setOf(
            "help", "what can i say", "what should i say"
        )
        val controlQuestions = setOf(
            "what should i change", "what can i change"
        )

        val leadingDispositionPattern = Regex("^(?:(?:no(?: no)?|nope|actually|please)\\s+)+")
        val leadingRequestPattern = Regex(
            "^(?:(?:can|could|would)\\s+you\\s+|i\\s+(?:want|would like)\\s+to\\s+)"
        )
        val fieldChangePattern = Regex(
            "^(?:change|edit|replace|update|set)\\s+(?:(?:the\\s+)?(?:task\\s+)?)?(title|name|date|day|time)(?:\\s+to\\s+(.+))?$"
        )
        val renamePattern = Regex(
            "^rename\\s+(?:(?:it|(?:(?:the\\s+)?(?:task\\s+)?)?(?:title|name)))(?:\\s+to\\s+(.+))?$"
        )
        val genericCorrectionPattern = Regex(
            "^(?:change|edit|replace|update|set)\\s+it\\s+to\\s+(.+)$"
        )
        val makeCorrectionPattern = Regex("^make\\s+it(?:\\s+to)?\\s+(.+)$")
        val bareFieldPattern = Regex("^(?:(?:the|task)\\s+)?(title|name|date|day|time)$")
    }
}
