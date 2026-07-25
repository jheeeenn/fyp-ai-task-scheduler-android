package com.example.myapplication.ai.conversation.query

import com.example.myapplication.ai.conversation.ConversationQueryReadingMove
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryReadingControlPolicyTest {
    @Test
    fun startOverviewIsBoundedToCountWithAnActivePrivateSession() {
        assertValid(
            ConversationQueryReadingMove.START_OVERVIEW,
            QueryReadingInteractionState.QUERY_COUNT,
            hasSession = true
        )
        assertInvalid(
            ConversationQueryReadingMove.START_OVERVIEW,
            QueryReadingInteractionState.QUERY_PAGE,
            hasSession = true
        )
        assertInvalid(
            ConversationQueryReadingMove.START_OVERVIEW,
            QueryReadingInteractionState.QUERY_COUNT,
            hasSession = false
        )
    }

    @Test
    fun continueAndRepeatPageAreBoundedToAnActivePageSession() {
        listOf(
            ConversationQueryReadingMove.CONTINUE,
            ConversationQueryReadingMove.REPEAT_PAGE
        ).forEach { move ->
            assertValid(move, QueryReadingInteractionState.QUERY_PAGE, hasSession = true)
            assertInvalid(move, QueryReadingInteractionState.QUERY_COUNT, hasSession = true)
            assertInvalid(move, QueryReadingInteractionState.QUERY_PAGE, hasSession = false)
        }
    }

    @Test
    fun repeatLastRequiresAuthoritativeSpeechAndAQueryInteraction() {
        assertValid(
            ConversationQueryReadingMove.REPEAT_LAST,
            QueryReadingInteractionState.QUERY_PAGE,
            hasRepeat = true
        )
        assertValid(
            ConversationQueryReadingMove.REPEAT_LAST,
            QueryReadingInteractionState.QUERY_COUNT,
            hasRepeat = true
        )
        assertInvalid(
            ConversationQueryReadingMove.REPEAT_LAST,
            QueryReadingInteractionState.QUERY_PAGE,
            hasRepeat = false
        )
        assertInvalid(
            ConversationQueryReadingMove.REPEAT_LAST,
            QueryReadingInteractionState.NONE,
            hasRepeat = true
        )
    }

    @Test
    fun stopIsBoundedAndNoneAlwaysFailsClosed() {
        assertValid(
            ConversationQueryReadingMove.STOP,
            QueryReadingInteractionState.QUERY_PAGE,
            hasSession = true
        )
        assertInvalid(
            ConversationQueryReadingMove.STOP,
            QueryReadingInteractionState.NONE,
            hasSession = true
        )
        assertInvalid(
            ConversationQueryReadingMove.NONE,
            QueryReadingInteractionState.QUERY_PAGE,
            hasSession = true,
            hasRepeat = true
        )
    }

    private fun assertValid(
        move: ConversationQueryReadingMove,
        state: QueryReadingInteractionState,
        hasSession: Boolean = false,
        hasRepeat: Boolean = false
    ) {
        assertTrue(
            QueryReadingControlPolicy.validate(
                move,
                state,
                hasSession,
                hasRepeat
            ).isValid
        )
    }

    private fun assertInvalid(
        move: ConversationQueryReadingMove,
        state: QueryReadingInteractionState,
        hasSession: Boolean = false,
        hasRepeat: Boolean = false
    ) {
        val result = QueryReadingControlPolicy.validate(
            move,
            state,
            hasSession,
            hasRepeat
        )
        assertFalse(result.isValid)
        assertTrue(result.clarification.isNotBlank())
    }
}
