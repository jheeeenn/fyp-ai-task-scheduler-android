package com.example.myapplication.ai.conversation

data class AppGuidanceContext(
    val currentScreen: String,
    val assistantPurpose: String,
    val supportedCapabilities: List<String>,
    val screenActions: List<String>,
    val inputMethods: List<String>,
    val currentInteraction: String,
    val currentInteractionGuidance: List<String>,
    val usageExamples: List<String>,
    val limitations: List<String>
) {
    fun toPromptText(): String = buildString {
        appendValue("Current screen", currentScreen)
        appendValue("Assistant purpose", assistantPurpose)
        appendItems("Supported capabilities", supportedCapabilities)
        appendItems("Available screen actions", screenActions)
        appendItems("Input methods", inputMethods)
        appendValue("Current interaction", currentInteraction)
        appendItems("What the user may say now", currentInteractionGuidance)
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
