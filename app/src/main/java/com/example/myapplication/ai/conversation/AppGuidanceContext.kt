package com.example.myapplication.ai.conversation

data class AppGuidanceContext(
    val currentScreen: String,
    val assistantPurpose: String,
    val supportedCapabilities: List<String>,
    val screenActions: List<String>,
    val inputMethods: List<String>,
    val interactionState: String,
    val currentInteraction: String,
    val currentInteractionGuidance: List<String>,
    val usageExamples: List<String>,
    val limitations: List<String>
) {
    fun toPromptText(): String = buildString {
        appendValue("Current screen", currentScreen)
        val hasActiveInteraction = interactionState.trim().let { state ->
            state.isNotEmpty() && !state.equals("NONE", ignoreCase = true)
        }
        appendValue(
            "Interaction priority",
            if (hasActiveInteraction) "ACTIVE" else "NONE"
        )
        if (hasActiveInteraction) {
            appendValue(
                "Interaction priority rule",
                "For questions about what to say, what to do next, what is expected now, " +
                    "or what the current interaction means, use Current interaction and " +
                    "What the user may say now before general application guidance."
            )
        }
        appendValue("Interaction state", interactionState)
        appendValue("Current interaction", currentInteraction)
        appendItems("What the user may say now", currentInteractionGuidance)
        appendValue("Assistant purpose", assistantPurpose)
        appendItems("Supported capabilities", supportedCapabilities)
        appendItems("Available screen actions", screenActions)
        appendItems("Input methods", inputMethods)
        appendItems("Example commands", usageExamples)
        appendItems("Limitations", limitations)
    }.trim()

    private fun StringBuilder.appendValue(label: String, value: String) {
        val normalizedValue = value.trim()
        if (normalizedValue.isEmpty()) return
        appendLine("$label:")
        appendLine(normalizedValue)
    }

    private fun StringBuilder.appendItems(label: String, items: List<String>) {
        val normalizedItems = items.map(String::trim).filter(String::isNotEmpty)
        if (normalizedItems.isEmpty()) return
        appendLine("$label:")
        normalizedItems.forEach { appendLine("- $it") }
    }
}
