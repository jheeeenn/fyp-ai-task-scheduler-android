package com.example.myapplication.ai.conversation

import com.example.myapplication.ai.schema.AgentResponseSchemas
import com.example.myapplication.voice.VoiceSettingsDecisionValidator
import com.example.myapplication.voice.VoiceSettingsReadDecisionValidator
import com.example.myapplication.voice.VoiceSettingsReadRecoveryPolicy
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
        val targets = properties.getJSONObject("setting_target").enumValues()

        assertTrue("SETTINGS_ACTION" in routes)
        assertTrue("SETTINGS_READ" in routes)
        assertEquals(ConversationSettingAction.entries.map { it.name }.toSet(), actions)
        assertEquals(ConversationSettingTarget.entries.map { it.name }.toSet(), targets)
        assertEquals(false, schema.getBoolean("additionalProperties"))
        assertTrue(schema.getJSONArray("required").toStrings().contains("setting_action"))
        assertTrue(schema.getJSONArray("required").toStrings().contains("setting_target"))
    }

    @Test
    fun settingsReadRequiresOneTargetAndCanonicalizesMutationAuthorityAway() {
        val parsed = parser.parseWithReport(
            decisionJson(
                route = "SETTINGS_READ",
                settingAction = "HIGH_CONTRAST_ON",
                settingTarget = "HIGH_CONTRAST"
            )
        )

        assertEquals(ConversationRoute.SETTINGS_READ, parsed.decision.route)
        assertEquals(ConversationSettingTarget.HIGH_CONTRAST, parsed.decision.settingTarget)
        assertEquals(ConversationSettingAction.NONE, parsed.decision.settingAction)
        assertTrue(parsed.canonicalizationReport.fields.contains("setting_action"))
        assertTrue(VoiceSettingsReadDecisionValidator.isValid(parsed.decision))

        assertThrows(ConversationSchemaException::class.java) {
            parser.parse(decisionJson(route = "SETTINGS_READ", settingTarget = "NONE"))
        }
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
    fun onlySettingsReadRetainsSettingTarget() {
        ConversationRoute.entries
            .filterNot { it == ConversationRoute.SETTINGS_READ }
            .forEach { route ->
                val decoded = ConversationDecisionStructuralDecoder().decode(
                    decisionJson(
                        route = route.name,
                        settingAction = if (route == ConversationRoute.SETTINGS_ACTION) {
                            "HIGH_CONTRAST_ON"
                        } else {
                            "NONE"
                        },
                        settingTarget = "HIGH_CONTRAST",
                        contextRef = if (route in setOf(
                                ConversationRoute.CONTEXT_READ,
                                ConversationRoute.CONTEXT_ACTION
                            )) "T1" else "",
                        contextDetail = if (route == ConversationRoute.CONTEXT_READ) "TITLE" else "NONE",
                        contextAction = if (route == ConversationRoute.CONTEXT_ACTION) "UPDATE" else "NONE",
                        queryReadingMove = if (route == ConversationRoute.QUERY_READING_CONTROL) "STOP" else "NONE"
                    )
                )
                val canonical = ConversationDecisionCanonicalizer.canonicalize(decoded).decision
                assertEquals(route.name, ConversationSettingTarget.NONE, canonical.settingTarget)
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
        assertTrue(prompt.contains("labelled Voice-setting context"))
        assertTrue(prompt.contains("separate from Read-only task context"))
        assertTrue(prompt.contains("Target: HIGH_CONTRAST, User: \"can you turn it off\""))
        assertTrue(prompt.contains("SETTINGS_ACTION with HIGH_CONTRAST_OFF"))
        assertTrue(prompt.contains("Target: HIGH_CONTRAST, User: \"turn off large text\""))
        assertTrue(prompt.contains("SETTINGS_ACTION with LARGE_TEXT_OFF"))
        assertTrue(prompt.contains("\"Is high contrast on?\" is SETTINGS_READ"))
        assertTrue(prompt.contains("\"What tone are you using?\" is SETTINGS_READ"))
        assertTrue(prompt.contains("\"What settings can you change?\" remains DIRECT_REPLY"))
    }

    @Test
    fun androidRecoversOnlyGroundedCurrentValueQuestions() {
        assertEquals(
            ConversationSettingTarget.HIGH_CONTRAST,
            VoiceSettingsReadRecoveryPolicy.recoverTarget("is high contrast on")
        )
        assertEquals(
            ConversationSettingTarget.LARGE_TEXT,
            VoiceSettingsReadRecoveryPolicy.recoverTarget("is large text enabled")
        )
        assertEquals(
            ConversationSettingTarget.PROCESSING_HAPTIC,
            VoiceSettingsReadRecoveryPolicy.recoverTarget("is processing haptic off")
        )
        assertEquals(
            ConversationSettingTarget.ASSISTANT_TONE,
            VoiceSettingsReadRecoveryPolicy.recoverTarget("what tone are you using")
        )
        assertEquals(
            ConversationSettingTarget.REPLY_LENGTH,
            VoiceSettingsReadRecoveryPolicy.recoverTarget("what reply length are you using")
        )
        assertEquals(null, VoiceSettingsReadRecoveryPolicy.recoverTarget("what settings can you change"))
        assertEquals(null, VoiceSettingsReadRecoveryPolicy.recoverTarget("what does high contrast do"))
        assertEquals(null, VoiceSettingsReadRecoveryPolicy.recoverTarget("turn on high contrast"))
        assertEquals(null, VoiceSettingsReadRecoveryPolicy.recoverTarget("is vibration on"))

        val recovered = requireNotNull(
            VoiceSettingsReadRecoveryPolicy.recoverDecision("is high contrast on")
        )
        assertEquals(ConversationRoute.SETTINGS_READ, recovered.route)
        assertEquals(ConversationSettingTarget.HIGH_CONTRAST, recovered.settingTarget)
        assertEquals(ConversationSettingAction.NONE, recovered.settingAction)
        assertTrue(VoiceSettingsReadDecisionValidator.isValid(recovered))
    }

    @Test
    fun homeReadsBeforeMutationAndFocusesWithoutNavigationOrRefresh() {
        val home = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()
        val recovery = home.indexOf("VoiceSettingsReadRecoveryPolicy.recoverDecision(normalized)")
        val mutationRecovery = home.indexOf("VoiceSettingContextualActionResolver.resolve(")
        val readBranch = home.substringAfter("ConversationRoute.SETTINGS_READ -> {")
            .substringBefore("ConversationRoute.SETTINGS_ACTION ->")

        assertTrue(recovery >= 0)
        assertTrue(mutationRecovery > recovery)
        assertTrue(readBranch.contains("VoiceSettingsReadDecisionValidator.isValid"))
        assertTrue(readBranch.contains("voiceSettingsStatusReader.read"))
        assertTrue(readBranch.contains("voiceSettingConversationContext::focus"))
        assertFalse(readBranch.contains("voiceSettingsExecutor"))
        assertFalse(readBranch.contains("executeAllowedVoiceSetting"))
        assertFalse(readBranch.contains("SettingsActivity"))
        assertFalse(readBranch.contains("startActivity"))
        assertFalse(readBranch.contains("recreate"))
        assertFalse(readBranch.contains("clearAccessibleTaskQuerySession"))
    }

    @Test
    fun homeAndOrchestratorTransportDedicatedVoiceSettingFocus() {
        val home = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()
        val orchestrator = File(
            "src/main/java/com/example/myapplication/ai/conversation/ConversationOrchestrator.kt"
        ).readText()
        val routingCall = home.substringAfter("conversationOrchestrator.process(")
            .substringBefore(")\n                } catch")

        assertTrue(home.contains("VoiceSettingRoutingContext.from("))
        assertTrue(routingCall.contains("voiceSettingRoutingContext = voiceSettingRoutingContext"))
        assertTrue(orchestrator.contains("voiceSettingRoutingContext: VoiceSettingRoutingContext"))
        assertTrue(orchestrator.contains("voiceSettingRoutingContext.toPromptText()"))
        assertTrue(orchestrator.contains("appendTaskContext("))
        assertTrue(orchestrator.contains("memorySnapshot = routingMemory"))
    }

    @Test
    fun homePerformsBoundedRecoveryBeforeExistingMutationSafetyGate() {
        val home = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()
        val recovery = home.indexOf("VoiceSettingContextualActionResolver.resolve(")
        val routeSwitch = home.indexOf("when (conversationDecision.route)", recovery)
        val safety = home.indexOf("VoiceSettingsMutationSafetyPolicy.evaluate(", routeSwitch)

        assertTrue(recovery >= 0)
        assertTrue(routeSwitch > recovery)
        assertTrue(safety > routeSwitch)
        assertTrue(home.contains("VOICE_SETTINGS_CONTEXT_RECOVERY"))
        assertTrue(home.contains("modelAction="))
        assertTrue(home.contains("recoveredAction="))
    }

    @Test
    fun pendingHapticClarificationOwnsCandidateAnswerBeforeGeneralRouting() {
        val home = File("src/main/java/com/example/myapplication/HomeActivity.kt").readText()
        val voiceFlow = home.substringAfter("private fun handleVoiceCommand(command: String)")
            .substringBefore("private fun beginAssistantRequest")
        val pendingHandler = home.substringAfter(
            "private fun handlePendingVoiceSettingClarification(normalized: String)"
        ).substringBefore("private fun executeAllowedVoiceSetting")
        val pendingCheck = voiceFlow.indexOf("handlePendingVoiceSettingClarification(normalized)")
        val generalRouting = voiceFlow.indexOf("conversationOrchestrator.process(")

        assertTrue(pendingCheck >= 0)
        assertTrue(generalRouting > pendingCheck)
        assertTrue(pendingHandler.contains("shouldRetainClarification("))
        assertTrue(pendingHandler.contains("retryClarification(pending)"))
        assertTrue(pendingHandler.contains("voiceSettingConversationContext.retain("))
        assertTrue(pendingHandler.contains("voiceSettingConversationContext.clearPending()"))
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
        settingTarget: String = "NONE",
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
        .put("setting_target", settingTarget)
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
