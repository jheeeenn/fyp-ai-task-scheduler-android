package com.example.myapplication.ai.conversation

import com.example.myapplication.ai.TaskQueryPresentation

enum class ConversationQueryReadingMove {
    NONE,
    START_OVERVIEW,
    CONTINUE,
    REPEAT_LAST,
    REPEAT_PAGE,
    STOP
}

data class ConversationDecision(
    val route: ConversationRoute,
    val taskText: String = "",
    val reply: String = "",
    val contextRef: String = "",
    val contextDetail: ConversationContextDetail = ConversationContextDetail.NONE,
    val contextAction: ConversationContextAction = ConversationContextAction.NONE,
    val settingAction: ConversationSettingAction = ConversationSettingAction.NONE,
    val settingTarget: ConversationSettingTarget = ConversationSettingTarget.NONE,
    val queryReadingMove: ConversationQueryReadingMove = ConversationQueryReadingMove.NONE,
    val queryPresentationHint: TaskQueryPresentation = TaskQueryPresentation.NONE,
    val confidence: Double = 0.0,
    val listenAgain: Boolean = true,
    val source: String = "conversation_agent"
)
