package com.example.myapplication.ai.agent

import com.example.myapplication.ai.conversation.ConversationContextAction
import com.example.myapplication.ai.temporal.RelativeTemporalProposal

data class ContextActionChangeSet(
    val action: ConversationContextAction,
    val replacementTitle: String? = null,
    val newDateText: String? = null,
    val newTimeText: String? = null,
    val confidence: Float,
    val temporalProposal: RelativeTemporalProposal? = null
)
