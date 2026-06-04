package com.example.myapplication.ai.agent

class ConversationStateManager(
    private val maxTurns: Int = 6
) {
    private var state = ConversationState()

    fun currentState(): ConversationState = state

    fun recordAgentResponse(userText: String, response: AgentResponse) {
        val turn = ConversationTurn(
            userText = userText,
            action = response.structuredAction.action,
            assistantResponse = response.naturalResponse
        )
        state = state.copy(
            pendingAction = response.structuredAction.takeIf { action ->
                action.needClarification ||
                        (action.action == AgentActionType.DELETE_TASK && action.requiresConfirmation)
            },
            lastAssistantQuestion = response.naturalResponse.takeIf {
                response.structuredAction.needClarification ||
                        (response.structuredAction.action == AgentActionType.DELETE_TASK &&
                                response.structuredAction.requiresConfirmation)
            },
            recentTurns = (state.recentTurns + turn).takeLast(maxTurns)
        )
    }

    fun recordFallback(userText: String, reason: String?) {
        val turn = ConversationTurn(
            userText = userText,
            action = AgentActionType.UNKNOWN,
            assistantResponse = "Fallback to legacy router${reason?.let { ": $it" }.orEmpty()}"
        )
        state = state.copy(recentTurns = (state.recentTurns + turn).takeLast(maxTurns))
    }

    fun clearPending() {
        state = state.copy(pendingAction = null, lastAssistantQuestion = null)
    }

    fun reset() {
        state = ConversationState()
    }
}
