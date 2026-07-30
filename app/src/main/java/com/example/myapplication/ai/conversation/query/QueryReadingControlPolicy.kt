package com.example.myapplication.ai.conversation.query

import com.example.myapplication.ai.conversation.ConversationQueryReadingMove

internal enum class QueryReadingInteractionState {
    NONE,
    QUERY_COUNT,
    QUERY_PAGE,
    DAILY_BRIEFING,
    CONTEXT_SUGGESTION
}

internal data class QueryReadingControlValidation(
    val isValid: Boolean,
    val clarification: String = ""
)

internal object QueryReadingControlPolicy {
    fun validate(
        move: ConversationQueryReadingMove,
        interactionState: QueryReadingInteractionState,
        hasActiveSession: Boolean,
        hasAuthoritativeRepeat: Boolean
    ): QueryReadingControlValidation {
        val valid = when (move) {
            ConversationQueryReadingMove.START_OVERVIEW ->
                interactionState == QueryReadingInteractionState.QUERY_COUNT &&
                    hasActiveSession

            ConversationQueryReadingMove.CONTINUE ->
                interactionState == QueryReadingInteractionState.QUERY_PAGE &&
                    hasActiveSession

            ConversationQueryReadingMove.REPEAT_LAST ->
                interactionState != QueryReadingInteractionState.NONE &&
                    hasAuthoritativeRepeat

            ConversationQueryReadingMove.REPEAT_PAGE ->
                interactionState == QueryReadingInteractionState.QUERY_PAGE &&
                    hasActiveSession

            ConversationQueryReadingMove.STOP ->
                interactionState == QueryReadingInteractionState.DAILY_BRIEFING ||
                    interactionState == QueryReadingInteractionState.CONTEXT_SUGGESTION ||
                    (interactionState != QueryReadingInteractionState.NONE &&
                        hasActiveSession)

            ConversationQueryReadingMove.NONE -> false
        }
        return if (valid) {
            QueryReadingControlValidation(isValid = true)
        } else {
            QueryReadingControlValidation(
                isValid = false,
                clarification = "That reading control is not available right now."
            )
        }
    }
}
