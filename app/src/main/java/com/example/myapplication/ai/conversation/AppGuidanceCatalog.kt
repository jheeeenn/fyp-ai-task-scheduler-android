package com.example.myapplication.ai.conversation

/**
 * Stable, Android-authoritative facts that the Conversation Agent may use for app guidance.
 * Live interaction state stays with the screen that owns the interaction.
 */
object AppGuidanceCatalog {
    fun homeContext(
        interactionState: String,
        currentInteraction: String,
        currentInteractionGuidance: List<String>
    ): AppGuidanceContext = AppGuidanceContext(
        currentScreen = "Home",
        assistantPurpose =
            "Help a visually impaired user manage scheduled tasks through voice or typed assistant input.",
        supportedCapabilities = listOf(
            "Create a task with a title, date, and time from a natural request; the recognised details open in task creation for review.",
            "Query and read tasks by date, time, or date range, including natural questions such as what is scheduled today, tomorrow, or next week.",
            "Use a clear follow-up to ask about a recently presented task's title, date, time, completion status, or subtasks; if the task is ambiguous, the app asks which task the user means instead of guessing.",
            "Edit a matched task's title, date, or time by opening it for review; reschedule, delete after confirmation, mark complete, or mark a completed task incomplete where requested.",
            "Provide an on-demand spoken daily briefing covering overdue tasks, today's tasks, upcoming tasks within seven days, and one suggested focus.",
            "Answer an explicit request such as 'What should I focus on?' with one read-only suggestion based on current task structure and device-local schedules.",
            "Suggest continuing the first unfinished subtask, suggest task breakdown for a suitable active task, or identify two active tasks scheduled no more than thirty minutes apart.",
            "Build a reusable routine from 2 to 5 ordered, timed steps, collect missing schedule information, and present the complete proposal for confirmation or revision before saving.",
            "List saved routines, read a routine, run or use a routine to create independent occurrence tasks, or delete a saved routine.",
            "Break a larger task into smaller actionable subtasks, present the proposed breakdown for confirmation or revision, collect missing schedule details, and save only after the required approval flow.",
            "Deliver a due reminder, a later follow-up if the task is still incomplete, and a final reminder where applicable when Android notification and alarm permissions allow delivery.",
            "Accept spoken requests from the Talk Assistant button or typed requests by long-pressing the same button; both use the same assistant pipeline.",
            "Let the user end naturally with a clear closing statement or use the Stop Assistant control.",
            "Change Assistant Tone by voice or in Settings using Friendly, Neutral, or Professional; tone changes how subsequent assistant responses are phrased.",
            "Change Reply Length by voice or in Settings using Short, Normal, or Detailed; reply length changes how detailed subsequent assistant responses are.",
            "Change Speech Speed by voice or in Settings using Slow, Normal, Fast, or Very Fast; speech speed controls how quickly app-generated spoken feedback is delivered.",
            "Turn Large Text on or off by voice or in Settings under Accessibility; it increases text size throughout the app in addition to Android's system font scaling.",
            "Turn High Contrast on or off by voice or in Settings under Accessibility; it increases foreground and background contrast across supported app screens.",
            "Turn Processing Haptic Feedback on or off by voice or in Settings; while enabled, a repeating heartbeat or lub-dub vibration means the assistant is genuinely processing a submitted request and is still working.",
            "Turn Session End Haptic Feedback on or off by voice or in Settings; while enabled, a distinct terminal vibration means the assistant conversation has completely finished and is separate from the processing heartbeat.",
            "The seven voice-configurable user settings are Large Text, High Contrast, Processing Haptic Feedback, Session End Haptic Feedback, Assistant Tone, Reply Length, and Speech Speed; users may ask to change them or ask what they are currently set to."
        ),
        screenActions = listOf(
            "Open today's tasks.",
            "Open the create-task screen.",
            "Open scheduled tasks.",
            "Open Settings.",
            "Start the voice assistant with the Talk Assistant button.",
            "Stop the current conversation with the Stop Assistant control."
        ),
        inputMethods = listOf(
            "Activate the Talk Assistant button to speak.",
            "Long-press the Talk Assistant button to type an assistant request.",
            "Voice and typed inputs use the same assistant pipeline."
        ),
        interactionState = interactionState,
        currentInteraction = currentInteraction,
        currentInteractionGuidance = currentInteractionGuidance,
        usageExamples = listOf(
            "Say, 'Create a task called take medicine tomorrow at 9 PM.'",
            "Say, 'What do I have today?' or 'Read my tasks.'",
            "Say, 'Give me my daily briefing.'",
            "Say, 'What should I focus on?'",
            "Say, 'Use my morning routine tomorrow.'",
            "Say, 'How do I reschedule a task?' for guidance without changing a task.",
            "Say, 'Turn on large text' or 'Turn on high contrast.'",
            "Say, 'Turn off processing haptic feedback' or 'Turn off session end haptic feedback.'",
            "Say, 'Use a professional tone' or 'Use short replies.'",
            "Say, 'Set speech speed to fast.'"
        ),
        limitations = listOf(
            "App guidance explains features but never performs an operation merely because the user asks how the operation works.",
            "Voice can change or read the current value of only these seven user-facing settings: Large Text, High Contrast, Processing Haptic Feedback, Session End Haptic Feedback, Assistant Tone, Reply Length, and Speech Speed.",
            "The app has no wake word or background always-listening activation; use the Talk Assistant control.",
            "The app does not provide calendar or email integration, weather, news, traffic, cloud or cross-device synchronisation, or arbitrary third-party integrations.",
            "A voice create request opens task creation with recognised fields prefilled for review; update and reschedule requests open the matched task for review.",
            "Delete requires confirmation before deletion. Task breakdown requires proposal approval and any missing scheduling information.",
            "Daily briefings are available only on demand and contain scheduled task information; they are not delivered automatically on a schedule.",
            "The daily briefing focus uses deterministic due-date and time ordering, not behavioural learning, habit learning, priority fields, autonomous prioritisation, or calendar data.",
            "Context-aware suggestions run only after an explicit request, never change a task, and do not use proactive monitoring, behavioural profiles, learned priorities, inferred duration, or calendar data.",
            "A close-schedule suggestion means tasks are no more than thirty minutes apart; it does not claim a definite conflict because task duration is unknown.",
            "The user must give a separate explicit task command before any breakdown, reschedule, completion, deletion, or other task change.",
            "A saved routine is a reusable template of ordered titles and default times. Running it creates independent occurrence tasks and does not mutate the stored template.",
            "Saved routines do not recur automatically, generate future occurrences automatically, learn habits, or integrate with calendars.",
            "Processing haptic feedback stops when processing ends or speaking begins. Processing and session-end haptic feedback can each be changed by voice or in Settings.",
            "Large Text and High Contrast can be changed by voice or in Settings under Accessibility; High Contrast does not imply formal accessibility-standard compliance.",
            "Assistant Tone, Reply Length, and Speech Speed can be changed by voice or in Settings; Speech Speed supports only Slow, Normal, Fast, and Very Fast.",
            "Conversation Agent Endpoint and Task Agent Endpoint are developer AI connection settings and cannot be changed by voice; never expose their configured URLs in app guidance.",
            "App guidance must not claim that an operation occurred unless the app successfully completed it."
        )
    )
}
