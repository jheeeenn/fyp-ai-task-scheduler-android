package com.example.myapplication.ai.conversation

import com.example.myapplication.ai.schema.AgentResponseSchemas
import com.example.myapplication.voice.VoiceSettingsDecisionValidator
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VoiceSettingsContractTest {
    private val parser = ConversationDecisionParser()

    @Test
    fun strictSchemaContainsSettingsRouteAndOnlyBoundedActions() {
        val schema = AgentResponseSchemas.conversationDecisionResponseFormat()
            .getJSONObject("json_schema")
            .getJSONObject("schema")
        val properties = schema.getJSONObject("properties")
        val routes = properties.getJSONObject("route").enumValues()
        val actions = properties.getJSONObject("setting_action").enumValues()

        assertTrue("SETTINGS_ACTION" in routes)
        assertEquals(ConversationSettingAction.entries.map { it.name }.toSet(), actions)
        assertEquals(false, schema.getBoolean("additionalProperties"))
        assertTrue(schema.getJSONArray("required").toStrings().contains("setting_action"))
    }

    @Test
    fun validSettingsActionPassesAndConflictingAuthorityIsCanonicalizedAway() {
        val result = parser.parseWithReport(
            decisionJson(
                route = "SETTINGS_ACTION",
                settingAction = "HIGH_CONTRAST_ON",
                taskText = "delete a task",
                reply = "I changed it",
                contextRef = "T1",
                contextDetail = "TITLE",
                contextAction = "DELETE",
                queryReadingMove = "STOP",
                queryPresentationHint = "DETAILS"
            )
        )

        assertEquals(ConversationRoute.SETTINGS_ACTION, result.decision.route)
        assertEquals(ConversationSettingAction.HIGH_CONTRAST_ON, result.decision.settingAction)
        assertTrue(VoiceSettingsDecisionValidator.isValid(result.decision))
        assertTrue(result.canonicalizationReport.fields.containsAll(
            listOf("task_text", "reply", "context_ref", "context_detail", "context_action",
                "query_reading_move", "query_presentation_hint")
        ))
    }

    @Test
    fun settingsActionFailsClosedForNoneLowConfidenceOrStoppedListening() {
        listOf(
            decisionJson(route = "SETTINGS_ACTION", settingAction = "NONE"),
            decisionJson(
                route = "SETTINGS_ACTION",
                settingAction = "LARGE_TEXT_ON",
                confidence = 0.79
            ),
            decisionJson(
                route = "SETTINGS_ACTION",
                settingAction = "LARGE_TEXT_ON",
                listenAgain = false
            )
        ).forEach { raw ->
            assertThrows(ConversationSchemaException::class.java) { parser.parse(raw) }
        }
    }

    @Test
    fun unsupportedSettingActionFailsStructurally() {
        val error = assertThrows(ConversationSchemaException::class.java) {
            parser.parse(
                decisionJson(
                    route = "SETTINGS_ACTION",
                    settingAction = "CONVERSATION_AGENT_ENDPOINT_CHANGE"
                )
            )
        }
        assertEquals(ConversationDecisionFailureCode.UNKNOWN_ENUM_VALUE, error.decisionFailureCode)
    }

    @Test
    fun everyNonSettingsRouteCanonicalizesSettingActionToNone() {
        ConversationRoute.entries
            .filterNot { it == ConversationRoute.SETTINGS_ACTION }
            .forEach { route ->
                val decoded = ConversationDecisionStructuralDecoder().decode(
                    decisionJson(
                        route = route.name,
                        settingAction = "HIGH_CONTRAST_ON",
                        contextRef = if (route == ConversationRoute.CONTEXT_READ ||
                            route == ConversationRoute.CONTEXT_ACTION) "T1" else "",
                        contextDetail = if (route == ConversationRoute.CONTEXT_READ) "TITLE" else "NONE",
                        contextAction = if (route == ConversationRoute.CONTEXT_ACTION) "UPDATE" else "NONE",
                        queryReadingMove = if (route == ConversationRoute.QUERY_READING_CONTROL) "STOP" else "NONE"
                    )
                )
                val canonical = ConversationDecisionCanonicalizer.canonicalize(decoded).decision
                assertEquals(route.name, ConversationSettingAction.NONE, canonical.settingAction)
            }
    }

    @Test
    fun promptEncodesGuidanceExecutionAmbiguityAndEndpointBoundaries() {
        val prompt = ConversationAgentClient.ROUTING_SYSTEM_PROMPT
        assertTrue(prompt.contains("\"How do I turn on high contrast?\" is DIRECT_REPLY"))
        assertTrue(prompt.contains("\"Turn on high contrast\" is SETTINGS_ACTION"))
        assertTrue(prompt.contains("\"Turn off processing haptic\" is SETTINGS_ACTION"))
        assertTrue(prompt.contains("\"Use professional tone\" is SETTINGS_ACTION"))
        assertTrue(prompt.contains("\"Use short replies\" is SETTINGS_ACTION"))
        assertTrue(prompt.contains("\"Turn off vibration\" is ambiguous"))
        assertTrue(prompt.contains("Conversation Agent Endpoint, Task Agent Endpoint"))
        assertTrue(prompt.contains("ask the user to change them one at a time"))
        assertTrue(prompt.contains("always keep reply empty"))
    }

    @Test
    fun homeExecutesWithoutSettingsNavigationAndDefersVisualRefresh() {
        val home = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()
        val routeBranch = home.substringAfter("ConversationRoute.SETTINGS_ACTION -> {")
            .substringBefore("ConversationRoute.DIRECT_REPLY ->")
        val refresh = home.substringAfter("private fun applyPendingVoiceDisplayRefresh()")
            .substringBefore("private companion object")

        assertTrue(routeBranch.contains("VoiceSettingsDecisionValidator.isValid"))
        assertTrue(routeBranch.contains("VoiceSettingsMutationSafetyPolicy.evaluate"))
        assertTrue(routeBranch.contains("VoiceSettingsSafetyDisposition.ALLOW"))
        assertTrue(routeBranch.contains("executeAllowedVoiceSetting"))
        assertFalse(routeBranch.contains("voiceSettingsExecutor.execute"))
        assertFalse(routeBranch.contains("clearAccessibleTaskQuerySession"))
        assertTrue(home.contains("pendingVoiceDisplayRefresh = true"))
        assertFalse(routeBranch.contains("SettingsActivity"))
        assertFalse(routeBranch.contains("startActivity"))
        assertTrue(home.contains("override fun onAssistantCancelled()"))
        assertTrue(home.contains("override fun onAssistantSessionStopped()"))
        assertTrue(refresh.indexOf("pendingVoiceDisplayRefresh = false") < refresh.indexOf("recreate()"))
        assertTrue(refresh.contains("!isFinishing && !isDestroyed"))
    }

    @Test
    fun executorCallIsIsolatedBehindAllowOnlyHelper() {
        val home = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()
        val routeBranch = home.substringAfter("ConversationRoute.SETTINGS_ACTION -> {")
            .substringBefore("ConversationRoute.DIRECT_REPLY ->")
        val allowBranch = routeBranch
            .substringAfter("VoiceSettingsSafetyDisposition.ALLOW -> {")
            .substringBefore("VoiceSettingsSafetyDisposition.GUIDANCE_ONLY ->")
        val vetoBranches = routeBranch.substringAfter("VoiceSettingsSafetyDisposition.GUIDANCE_ONLY ->")
        val executorHelper = home.substringAfter(
            "private fun executeAllowedVoiceSetting(action: ConversationSettingAction)"
        ).substringBefore("private fun deliverVoiceSettingsSafetyResponse")

        assertTrue(allowBranch.contains("executeAllowedVoiceSetting"))
        assertFalse(vetoBranches.contains("executeAllowedVoiceSetting"))
        assertEquals(1, executorHelper.split("voiceSettingsExecutor.execute").size - 1)
        assertTrue(home.contains("handlePendingVoiceSettingClarification(normalized)"))
        assertTrue(home.contains("voiceSettingConversationContext.clear()"))
        val sessionClear = home.substringAfter("private fun clearConversationSessionContext()")
            .substringBefore("private fun clearAccessibleTaskQuerySession")
        assertTrue(sessionClear.contains("voiceSettingConversationContext.clear()"))
        val lifecycleStop = home.substringAfter("override fun onStop()")
            .substringBefore("override fun onDestroy()")
        assertTrue(lifecycleStop.contains("clearConversationSessionContext()"))
    }

    private fun decisionJson(
        route: String,
        settingAction: String = "NONE",
        taskText: String = "",
        reply: String = "",
        contextRef: String = "",
        contextDetail: String = "NONE",
        contextAction: String = "NONE",
        queryReadingMove: String = "NONE",
        queryPresentationHint: String = "NONE",
        confidence: Double = 0.97,
        listenAgain: Boolean = true
    ): String = JSONObject()
        .put("route", route)
        .put("task_text", taskText)
        .put("reply", reply)
        .put("context_ref", contextRef)
        .put("context_detail", contextDetail)
        .put("context_action", contextAction)
        .put("setting_action", settingAction)
        .put("query_reading_move", queryReadingMove)
        .put("query_presentation_hint", queryPresentationHint)
        .put("confidence", confidence)
        .put("listen_again", listenAgain)
        .toString()

    private fun JSONObject.enumValues(): Set<String> {
        val values = getJSONArray("enum")
        return (0 until values.length()).map(values::getString).toSet()
    }

    private fun org.json.JSONArray.toStrings(): Set<String> =
        (0 until length()).map(::getString).toSet()
}
