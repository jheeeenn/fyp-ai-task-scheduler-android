package com.example.myapplication.ai.conversation

internal enum class ConversationRepairProfile {
    GENERAL,
    NO_CONTEXT_MUTATION_REPAIR
}

internal object ConversationRepairProfileSelector {
    private const val PROFILE_LABEL = "Conversation repair request mode:"

    fun select(
        failureCode: String,
        failedRoute: ConversationRoute?,
        suppliedTemporaryRefCount: Int,
        validatedFocusAvailable: Boolean
    ): ConversationRepairProfile = if (
        failureCode == ConversationDecisionFailureCode.INVALID_CONTEXT_REF.name &&
        failedRoute == ConversationRoute.CONTEXT_ACTION &&
        suppliedTemporaryRefCount == 0 &&
        !validatedFocusAvailable
    ) {
        ConversationRepairProfile.NO_CONTEXT_MUTATION_REPAIR
    } else {
        ConversationRepairProfile.GENERAL
    }

    fun marker(profile: ConversationRepairProfile): String =
        "$PROFILE_LABEL ${profile.name}"

    fun fromBoundedContext(appContextSummary: String): ConversationRepairProfile =
        ConversationRepairProfile.entries.firstOrNull { profile ->
            appContextSummary.lineSequence().any { line ->
                line.trim() == marker(profile)
            }
        } ?: ConversationRepairProfile.GENERAL
}
