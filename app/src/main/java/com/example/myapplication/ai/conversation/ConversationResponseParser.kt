package com.example.myapplication.ai.conversation

import org.json.JSONException
import org.json.JSONObject

class ConversationResponseParser {
    fun parse(content: String): ConversationResponse {
        if (content.isBlank()) throw ConversationSchemaException("Conversation response is blank")
        return try {
            val json = JSONObject(extractFirstJsonObject(content))
            val expected = setOf("speech", "hint", "response_type")
            val keys = json.keys().asSequence().toSet()
            if (keys != expected) throw ConversationSchemaException("Conversation response fields must be exactly $expected but were $keys")
            val speech = json.getString("speech")
            if (speech.isBlank()) throw ConversationSchemaException("Conversation response speech is blank")
            val hint = json.getString("hint")
            val type = try {
                ConversationResponseType.valueOf(json.getString("response_type"))
            } catch (e: IllegalArgumentException) {
                throw ConversationSchemaException("Unsupported response_type", e)
            }
            ConversationResponse(speech = speech, hint = hint, responseType = type)
        } catch (e: ConversationSchemaException) {
            throw e
        } catch (e: JSONException) {
            throw ConversationSchemaException("Malformed ConversationResponse JSON", e)
        }
    }

    private fun extractFirstJsonObject(text: String): String {
        val start = text.indexOf('{')
        if (start < 0) throw ConversationSchemaException("No JSON object found")
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (escaped) { escaped = false; continue }
            if (c == '\\') { escaped = inString; continue }
            if (c == '"') inString = !inString
            if (!inString) {
                if (c == '{') depth++
                if (c == '}') {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        throw ConversationSchemaException("Unclosed JSON object")
    }
}
