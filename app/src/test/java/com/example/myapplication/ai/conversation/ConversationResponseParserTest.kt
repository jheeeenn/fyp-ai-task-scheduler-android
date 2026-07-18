package com.example.myapplication.ai.conversation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ConversationResponseParserTest {
    private val parser = ConversationResponseParser()

    @Test fun acceptsExactlyValidJson() {
        val parsed = parser.parse("{\"speech\":\"Done.\",\"hint\":\"\",\"response_type\":\"SUCCESS\"}")
        assertEquals("Done.", parsed.speech)
        assertEquals(ConversationResponseType.SUCCESS, parsed.responseType)
    }
    @Test fun rejectsBlankSpeech() { assertThrows(ConversationSchemaException::class.java) { parser.parse("{\"speech\":\" \" ,\"hint\":\"\",\"response_type\":\"SUCCESS\"}") } }
    @Test fun rejectsMissingFields() { assertThrows(ConversationSchemaException::class.java) { parser.parse("{\"speech\":\"Hi\",\"response_type\":\"SUCCESS\"}") } }
    @Test fun rejectsAdditionalFields() { assertThrows(ConversationSchemaException::class.java) { parser.parse("{\"speech\":\"Hi\",\"hint\":\"\",\"response_type\":\"SUCCESS\",\"listen_again\":false}") } }
    @Test fun rejectsInvalidResponseType() { assertThrows(ConversationSchemaException::class.java) { parser.parse("{\"speech\":\"Hi\",\"hint\":\"\",\"response_type\":\"MADE_UP\"}") } }
}
