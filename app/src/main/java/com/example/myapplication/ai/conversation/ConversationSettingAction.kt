package com.example.myapplication.ai.conversation

/** The complete Android-owned allowlist of settings mutations available to conversation routing. */
enum class ConversationSettingAction {
    NONE,
    LARGE_TEXT_ON,
    LARGE_TEXT_OFF,
    HIGH_CONTRAST_ON,
    HIGH_CONTRAST_OFF,
    PROCESSING_HAPTIC_ON,
    PROCESSING_HAPTIC_OFF,
    SESSION_END_HAPTIC_ON,
    SESSION_END_HAPTIC_OFF,
    ASSISTANT_TONE_FRIENDLY,
    ASSISTANT_TONE_NEUTRAL,
    ASSISTANT_TONE_PROFESSIONAL,
    REPLY_LENGTH_SHORT,
    REPLY_LENGTH_NORMAL,
    REPLY_LENGTH_DETAILED,
    SPEECH_SPEED_SLOW,
    SPEECH_SPEED_NORMAL,
    SPEECH_SPEED_FAST,
    SPEECH_SPEED_VERY_FAST,
    SPEECH_SPEED_FASTER,
    SPEECH_SPEED_SLOWER
}
