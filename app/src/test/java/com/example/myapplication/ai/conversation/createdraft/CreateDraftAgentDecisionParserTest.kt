package com.example.myapplication.ai.conversation.createdraft

import com.example.myapplication.ai.conversation.ConversationSchemaException
import com.example.myapplication.voice.CreateDraftField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CreateDraftAgentDecisionParserTest {
    private val parser = CreateDraftAgentDecisionParser()

    @Test
    fun exactValidResponseIsAccepted() {
        val decision = parser.parse(
            """{"move":"CHANGE_FIELD","field":"TIME","value":"10 AM","confidence":0.96}"""
        )
        assertEquals(CreateDraftAgentMoveType.CHANGE_FIELD, decision.move)
        assertEquals(CreateDraftField.TIME, decision.field)
        assertEquals("10 AM", decision.value)
        assertEquals(0.96, decision.confidence, 0.0)
    }

    @Test
    fun missingFieldIsRejected() {
        assertRejected("""{"move":"CANCEL","field":"","value":""}""")
    }

    @Test
    fun additionalFieldIsRejected() {
        assertRejected(validUnknown().dropLast(1) + ",\"extra\":true}")
        assertRejected("Explanation: ${validUnknown()}")
    }

    @Test
    fun invalidMoveIsRejected() {
        assertRejected("""{"move":"SAVE_TASK","field":"","value":"","confidence":0.9}""")
    }

    @Test
    fun invalidFieldIsRejected() {
        assertRejected("""{"move":"CHANGE_FIELD","field":"PRIORITY","value":"high","confidence":0.9}""")
    }

    @Test
    fun confidenceOutsideRangeIsRejected() {
        assertRejected("""{"move":"UNKNOWN","field":"","value":"","confidence":1.1}""")
        assertRejected("""{"move":"UNKNOWN","field":"","value":"","confidence":-0.1}""")
    }

    @Test
    fun taskAgentFieldsAreRejected() {
        assertRejected(validUnknown().dropLast(1) + ",\"task_title\":\"revision\"}")
    }

    @Test
    fun moveSpecificFieldAndValueRequirementsAreEnforced() {
        assertRejected("""{"move":"CHANGE_FIELD","field":"","value":"revision","confidence":0.9}""")
        assertRejected("""{"move":"PROVIDE_FIELD","field":"TITLE","value":"","confidence":0.9}""")
        assertRejected("""{"move":"APPLY_UNSPECIFIED_CORRECTION","field":"","value":"","confidence":0.9}""")
        assertRejected("""{"move":"CONFIRM_SAVE","field":"TIME","value":"","confidence":0.95}""")
    }

    private fun validUnknown() =
        """{"move":"UNKNOWN","field":"","value":"","confidence":0.9}"""

    private fun assertRejected(json: String) {
        assertThrows(ConversationSchemaException::class.java) { parser.parse(json) }
    }
}
