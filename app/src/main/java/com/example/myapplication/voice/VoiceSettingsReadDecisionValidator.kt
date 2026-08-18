package com.example.myapplication.voice

import com.example.myapplication.ai.TaskQueryPresentation
import com.example.myapplication.ai.conversation.ConversationContextAction
import com.example.myapplication.ai.conversation.ConversationContextDetail
import com.example.myapplication.ai.conversation.ConversationDecision
import com.example.myapplication.ai.conversation.ConversationDecisionParser
import com.example.myapplication.ai.conversation.ConversationQueryReadingMove
import com.example.myapplication.ai.conversation.ConversationRoute
import com.example.myapplication.ai.conversation.ConversationSettingAction
import com.example.myapplication.ai.conversation.ConversationSettingTarget

object VoiceSettingsReadDecisionValidator {
    fun isValid(decision: ConversationDecision): Boolean =
        decision.route == ConversationRoute.SETTINGS_READ &&
            decision.settingTarget != ConversationSettingTarget.NONE &&
            decision.settingAction == ConversationSettingAction.NONE &&
            decision.confidence >= ConversationDecisionParser.MIN_ACCEPTED_ROUTING_CONFIDENCE &&
            decision.listenAgain &&
            decision.taskText.isEmpty() &&
            decision.reply.isEmpty() &&
            decision.contextRef.isEmpty() &&
            decision.contextDetail == ConversationContextDetail.NONE &&
            decision.contextAction == ConversationContextAction.NONE &&
            decision.queryReadingMove == ConversationQueryReadingMove.NONE &&
            decision.queryPresentationHint == TaskQueryPresentation.NONE
}
