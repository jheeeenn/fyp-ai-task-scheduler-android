package com.example.myapplication.ai.conversation

/**
 * Detects only bounded, high-confidence contradictions in the selected top-level route.
 * It does not execute navigation, inspect stored data, or replace semantic routing.
 */
internal object ConversationTopLevelRouteConsistencyGuard {
    fun validate(normalizedText: String, decision: ConversationDecision) {
        val navigationTarget = explicitNavigationTarget(normalizedText)
        if (navigationTarget != null && decision.route != ConversationRoute.APP_NAVIGATION) {
            throw ConversationSchemaException(
                message = "Explicit supported app navigation was routed elsewhere",
                decisionFailureCode =
                    ConversationDecisionFailureCode.APP_NAVIGATION_MISROUTED,
                failedRoute = decision.route
            )
        }

        if (hasSavedRoutineListEvidence(normalizedText) &&
            decision.route != ConversationRoute.SAVED_ROUTINE_ACTION
        ) {
            throw ConversationSchemaException(
                message = "Explicit saved-routine list request was routed elsewhere",
                decisionFailureCode =
                    ConversationDecisionFailureCode.SAVED_ROUTINE_LIST_MISROUTED,
                failedRoute = decision.route
            )
        }
    }

    fun explicitNavigationTarget(text: String): ConversationNavigationTarget? {
        val withoutPolitePrefix = normalized(text).removePrefixMatching(POLITE_PREFIX)
            ?: return null
        val command = withoutPolitePrefix.removePrefixMatching(NAVIGATION_VERB) ?: return null

        return when {
            SETTINGS_TARGET.matches(command) -> ConversationNavigationTarget.SETTINGS
            CREATE_TASK_TARGET.matches(command) -> ConversationNavigationTarget.CREATE_TASK
            TODAY_TASKS_TARGET.matches(command) -> ConversationNavigationTarget.TODAY_TASKS
            SCHEDULED_TASKS_TARGET.matches(command) ->
                ConversationNavigationTarget.SCHEDULED_TASKS
            else -> null
        }
    }

    fun hasSavedRoutineListEvidence(text: String): Boolean {
        val utterance = normalized(text)
        return SAVED_ROUTINE_LIST_PATTERNS.any { it.matches(utterance) }
    }

    private fun String.removePrefixMatching(regex: Regex): String? {
        val match = regex.find(this) ?: return null
        if (match.range.first != 0) return null
        return substring(match.range.last + 1).trim()
    }

    private fun normalized(text: String): String = text
        .lowercase()
        .replace('’', '\'')
        .replace(Regex("[-_]"), " ")
        .replace(Regex("[^a-z0-9'\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private val POLITE_PREFIX = Regex(
        "^(?:(?:please|kindly)\\s+|(?:(?:can|could|would)\\s+you\\s+))?"
    )
    private val NAVIGATION_VERB = Regex("^(?:open|go\\s+to)\\s+")
    private val SETTINGS_TARGET = Regex("^(?:the\\s+)?settings?(?:\\s+(?:page|screen))?$")
    private val CREATE_TASK_TARGET = Regex(
        "^(?:the\\s+)?(?:create\\s+task|task\\s+creation)(?:\\s+(?:page|screen))?$"
    )
    private val TODAY_TASKS_TARGET = Regex(
        "^(?:the\\s+)?(?:today(?:'s)?\\s+tasks?|today(?:'s)?\\s+task\\s+list|" +
            "today\\s+list)(?:\\s+(?:page|screen))?$"
    )
    private val SCHEDULED_TASKS_TARGET = Regex(
        "^(?:the\\s+)?scheduled\\s+tasks?(?:\\s+(?:page|screen))?$"
    )
    private val SAVED_ROUTINE_LIST_PATTERNS = listOf(
        Regex("^do\\s+i\\s+have\\s+(?:any\\s+)?(?:saved\\s+)?routines?$"),
        Regex("^what\\s+(?:saved\\s+)?routines?\\s+do\\s+i\\s+have$"),
        Regex("^what\\s+routines?\\s+have\\s+i\\s+saved$"),
        Regex("^list\\s+(?:me\\s+)?(?:my\\s+)?(?:saved\\s+)?routines?$"),
        Regex("^show\\s+(?:me\\s+)?my\\s+(?:saved\\s+)?routines?$")
    )
}
