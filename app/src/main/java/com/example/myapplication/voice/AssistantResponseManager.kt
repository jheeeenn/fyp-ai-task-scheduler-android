package com.example.myapplication.voice

import com.example.myapplication.SettingsActivity

enum class AssistantTone {
    FRIENDLY, NEUTRAL, PROFESSIONAL
}

enum class AssistantVerbosity {
    BRIEF, BALANCED, DETAILED
}

enum class QueryDetailMode {
    SHORT,
    NORMAL,
    DETAILED
}

data class AssistantProfile(
    val tone: AssistantTone,
    val verbosity: AssistantVerbosity
)



class AssistantResponseManager(
    var profile: AssistantProfile = AssistantProfile(
        tone = AssistantTone.FRIENDLY,
        verbosity = AssistantVerbosity.BALANCED
    )
) {
    val tone get() = profile.tone
    val verbosity get() = profile.verbosity

    fun askTaskTitle(): String {
        return when (tone) {
            AssistantTone.FRIENDLY -> "Okay, what should I call this task?"
            AssistantTone.NEUTRAL -> "What should I call this task?"
            AssistantTone.PROFESSIONAL -> "Please tell me the task title."
        }
    }

    fun askTaskDate(): String {
        return when (tone) {
            AssistantTone.FRIENDLY -> "Alright. When should I remind you?"
            AssistantTone.NEUTRAL -> "What date should I use?"
            AssistantTone.PROFESSIONAL -> "Please provide the reminder date."
        }
    }

    fun askTaskTime(): String {
        return when (tone) {
            AssistantTone.FRIENDLY -> "And what time should I set? You can say 8 PM or 8:24 PM."
            AssistantTone.NEUTRAL -> "What time should I use? For example, 8 PM or 8:24 PM."
            AssistantTone.PROFESSIONAL -> "Please provide the reminder time, for example 8 PM or 8:24 PM."
        }
    }

    fun confirmTaskSummary(summary: String): String {
        return when (tone) {
            AssistantTone.FRIENDLY -> "Okay, I’ve got $summary. Should I save it?"
            AssistantTone.NEUTRAL -> "I have $summary. Should I save it?"
            AssistantTone.PROFESSIONAL -> "Please confirm. I have $summary. Should I save it?"
        }
    }
    fun openCreateTaskReply(source: String): String {
        return if (source == "local") {
            "Okay, opening task creation."
        } else {
            "Okay, I understood that. Opening task creation."
        }
    }

    fun queryNoTasksToday(): String {
        return when (tone) {
            AssistantTone.FRIENDLY -> "You don’t have any tasks today."
            AssistantTone.NEUTRAL -> "You have no tasks today."
            AssistantTone.PROFESSIONAL -> "There are no tasks scheduled for today."
        }
    }

    fun cancelCreate(): String = when (tone) {
        AssistantTone.FRIENDLY -> "Okay, cancelling task creation."
        AssistantTone.NEUTRAL -> "Cancelling task creation."
        AssistantTone.PROFESSIONAL -> "Task creation has been cancelled."
    }

    fun returnHome(): String = when (tone) {
        AssistantTone.FRIENDLY -> "Okay, returning home."
        AssistantTone.NEUTRAL -> "Returning home."
        AssistantTone.PROFESSIONAL -> "Returning to the home screen."
    }

    fun titleSet(title: String): String = when (tone) {
        AssistantTone.FRIENDLY -> "Okay, I set the title as $title."
        AssistantTone.NEUTRAL -> "Title set to $title."
        AssistantTone.PROFESSIONAL -> "The title has been set to $title."
    }

    fun dateSet(date: String): String = when (tone) {
        AssistantTone.FRIENDLY -> "Okay, I set the date to $date."
        AssistantTone.NEUTRAL -> "Date set to $date."
        AssistantTone.PROFESSIONAL -> "The date has been set to $date."
    }

    fun timeSet(time: String): String = when (tone) {
        AssistantTone.FRIENDLY -> "Okay, I set the time to $time."
        AssistantTone.NEUTRAL -> "Time set to $time."
        AssistantTone.PROFESSIONAL -> "The time has been set to $time."
    }

    fun askTitleAgain(): String = when (tone) {
        AssistantTone.FRIENDLY -> "I missed the title. What should I call this task?"
        AssistantTone.NEUTRAL -> "Please say the title again."
        AssistantTone.PROFESSIONAL -> "Please provide the task title again."
    }

    fun invalidDate(): String = when (tone) {
        AssistantTone.FRIENDLY -> "I couldn’t understand the date. You can say things like tomorrow, next Monday, or 25 March."
        AssistantTone.NEUTRAL -> "I could not understand the date. Try saying tomorrow, next Monday, or 25 March."
        AssistantTone.PROFESSIONAL -> "I could not interpret the date. Please say a date such as tomorrow, next Monday, or 25 March."
    }

    fun invalidTime(): String = when (tone) {
        AssistantTone.FRIENDLY -> "I didn’t catch the time. Please say something like 8 PM, 8:24 PM, or 12:01 AM."
        AssistantTone.NEUTRAL -> "I could not understand the time. Try saying 8 PM, 8:24 PM, or 12:01 AM."
        AssistantTone.PROFESSIONAL -> "Please provide the time in a format such as 8 PM, 8:24 PM, or 12:01 AM."
    }

    fun helpCreateTask(): String = when (tone) {
        AssistantTone.FRIENDLY -> "You can say create task, set title, set date today, set date tomorrow, set time 8 PM, or save task."
        AssistantTone.NEUTRAL -> "You can say create task, set title, set date today, set date tomorrow, set time 8 PM, or save task."
        AssistantTone.PROFESSIONAL -> "Available commands include create task, set title, set date, set time, and save task."
    }

    fun unknownCommand(): String = when (tone) {
        AssistantTone.FRIENDLY -> "Sorry, I didn’t understand that."
        AssistantTone.NEUTRAL -> "Sorry, I did not understand that command."
        AssistantTone.PROFESSIONAL -> "I could not understand that command."
    }

    fun taskTitleEmpty(): String = when (tone) {
        AssistantTone.FRIENDLY -> "The task still needs a title."
        AssistantTone.NEUTRAL -> "Task title cannot be empty."
        AssistantTone.PROFESSIONAL -> "A task title is required."
    }

    fun missingDateTime(): String = when (tone) {
        AssistantTone.FRIENDLY -> "I still need both the date and time before I can save this."
        AssistantTone.NEUTRAL -> "Please select both date and time before saving."
        AssistantTone.PROFESSIONAL -> "Both date and time are required before saving."
    }

    fun saveSuccess(): String = when (tone) {
        AssistantTone.FRIENDLY -> "Done. Your task has been saved."
        AssistantTone.NEUTRAL -> "Task saved successfully."
        AssistantTone.PROFESSIONAL -> "The task has been saved successfully."
    }

    fun savePartialFailure(): String = when (tone) {
        AssistantTone.FRIENDLY -> "Your task was saved, but I could not schedule the reminder."
        AssistantTone.NEUTRAL -> "Task saved, but reminder could not be scheduled."
        AssistantTone.PROFESSIONAL -> "The task was saved, but the reminder could not be scheduled."
    }

    fun listenFailure(): String = when (tone) {
        AssistantTone.FRIENDLY -> "I didn’t catch that. Please try again."
        AssistantTone.NEUTRAL -> "I didn't catch that. Please try again."
        AssistantTone.PROFESSIONAL -> "I could not hear your response clearly. Please try again."
    }

    fun stopListening(): String = when (tone) {
        AssistantTone.FRIENDLY -> "Okay, I’ll stop listening now. Tap Talk to Assistant when you’re ready."
        AssistantTone.NEUTRAL -> "I will stop listening now. Tap Talk to Assistant when you are ready."
        AssistantTone.PROFESSIONAL -> "Listening has been stopped. Tap Talk to Assistant when you are ready."
    }

    fun helpHome(): String = when (tone) {
        AssistantTone.FRIENDLY -> "You can ask me to create a task, check your tasks, or show all your tasks."
        AssistantTone.NEUTRAL -> "You can say create task, what are my tasks today, or show all my tasks."
        AssistantTone.PROFESSIONAL -> "Available commands include creating tasks and querying task lists."
    }

    fun parserCrash(): String = when (tone) {
        AssistantTone.FRIENDLY -> "Something went wrong while understanding that request."
        AssistantTone.NEUTRAL -> "The AI parser crashed. Please check Logcat."
        AssistantTone.PROFESSIONAL -> "The request parser encountered an error."
    }

    fun deleteNotReady(): String = when (tone) {
        AssistantTone.FRIENDLY -> "I understood that as deleting a task, but that part is not connected yet."
        AssistantTone.NEUTRAL -> "I understood this as a delete task request. Delete handling will be connected next."
        AssistantTone.PROFESSIONAL -> "Delete task handling is not connected yet."
    }

    fun updateNotReady(): String = when (tone) {
        AssistantTone.FRIENDLY -> "I understood that as updating a task, but that part is not connected yet."
        AssistantTone.NEUTRAL -> "I understood this as an update task request. Update handling will be connected next."
        AssistantTone.PROFESSIONAL -> "Update task handling is not connected yet."
    }

    fun rescheduleNotReady(): String = when (tone) {
        AssistantTone.FRIENDLY -> "I understood that as rescheduling a task, but that part is not connected yet."
        AssistantTone.NEUTRAL -> "I understood this as a reschedule request. Reschedule handling will be connected next."
        AssistantTone.PROFESSIONAL -> "Reschedule handling is not connected yet."
    }

    fun askChangeTitle(): String = when (tone) {
        AssistantTone.FRIENDLY -> "Okay, what should I change the title to?"
        AssistantTone.NEUTRAL -> "What is the new title?"
        AssistantTone.PROFESSIONAL -> "Please provide the new title."
    }

    fun askChangeDate(): String = when (tone) {
        AssistantTone.FRIENDLY -> "Okay, what date should I change it to?"
        AssistantTone.NEUTRAL -> "What date should I set?"
        AssistantTone.PROFESSIONAL -> "Please provide the new date."
    }

    fun askChangeTime(): String = when (tone) {
        AssistantTone.FRIENDLY -> "Okay, what time should I change it to?"
        AssistantTone.NEUTRAL -> "What time should I set?"
        AssistantTone.PROFESSIONAL -> "Please provide the new time."
    }

    fun askWhatToChange(): String = when (tone) {
        AssistantTone.FRIENDLY -> "Okay. What would you like to change? You can say title, date, or time."
        AssistantTone.NEUTRAL -> "What would you like to change? You can say change title, change date, or change time."
        AssistantTone.PROFESSIONAL -> "Please specify what you would like to change: title, date, or time."
    }

    fun dateSelected(date: String): String = when (tone) {
        AssistantTone.FRIENDLY -> "Okay, I selected $date."
        AssistantTone.NEUTRAL -> "Date selected: $date."
        AssistantTone.PROFESSIONAL -> "The date has been selected as $date."
    }

    fun timeSelected(time: String): String = when (tone) {
        AssistantTone.FRIENDLY -> "Okay, I selected $time."
        AssistantTone.NEUTRAL -> "Time selected: $time."
        AssistantTone.PROFESSIONAL -> "The time has been selected as $time."
    }

    fun pastDateTime(): String = when (tone) {
        AssistantTone.FRIENDLY -> "That date and time is already in the past."
        AssistantTone.NEUTRAL -> "The selected date and time is already in the past."
        AssistantTone.PROFESSIONAL -> "The selected date and time is in the past."
    }

    fun semanticTimeNeedsExact(phrase: String): String = when (tone) {
        AssistantTone.FRIENDLY -> "I understood the time as $phrase. Please tell me an exact clock time, for example 8 PM or 8:24 PM."
        AssistantTone.NEUTRAL -> "I understood the time as $phrase. Please tell me an exact clock time, for example 8 PM or 8:24 PM."
        AssistantTone.PROFESSIONAL -> "I interpreted the time as $phrase. Please provide an exact time, such as 8 PM or 8:24 PM."
    }

    fun learnedTimeSuggestion(phrase: String, learnedTime: String): String = when (tone) {
        AssistantTone.FRIENDLY -> "You usually mean $learnedTime when you say $phrase. Say yes to use it, or say a different time."
        AssistantTone.NEUTRAL -> "You usually mean $learnedTime when you say $phrase. Please say yes to use it, or say a different time."
        AssistantTone.PROFESSIONAL -> "You usually mean $learnedTime when you say $phrase. Please confirm or provide a different time."
    }

    fun inlineDateUpdated(summary: String): String = when (tone) {
        AssistantTone.FRIENDLY -> "Okay, I updated the date. I now have $summary. Should I save it?"
        AssistantTone.NEUTRAL -> "I updated the date. I now have $summary. Should I save it?"
        AssistantTone.PROFESSIONAL -> "The date has been updated. I now have $summary. Should I save it?"
    }

    fun inlineTimeUpdated(summary: String): String = when (tone) {
        AssistantTone.FRIENDLY -> "Okay, I updated the time. I now have $summary. Should I save it?"
        AssistantTone.NEUTRAL -> "I updated the time. I now have $summary. Should I save it?"
        AssistantTone.PROFESSIONAL -> "The time has been updated. I now have $summary. Should I save it?"
    }

    fun inlineTitleUpdated(summary: String): String = when (tone) {
        AssistantTone.FRIENDLY -> "Okay, I updated the title. I now have $summary. Should I save it?"
        AssistantTone.NEUTRAL -> "I updated the title. I now have $summary. Should I save it?"
        AssistantTone.PROFESSIONAL -> "The title has been updated. I now have $summary. Should I save it?"
    }

    fun correctionNotUnderstood(): String = when (tone) {
        AssistantTone.FRIENDLY -> "I couldn’t understand that change. Please try again."
        AssistantTone.NEUTRAL -> "I could not understand that change. Please try again."
        AssistantTone.PROFESSIONAL -> "I could not interpret that change. Please try again."
    }

    fun queryNoTasks(today: Boolean): String = when (tone) {
        AssistantTone.FRIENDLY -> if (today) "You don’t have any tasks today." else "You don’t have any active tasks."
        AssistantTone.NEUTRAL -> if (today) "You have no tasks today." else "You have no active tasks."
        AssistantTone.PROFESSIONAL -> if (today) "There are no tasks scheduled for today." else "There are no active tasks."
    }

    fun queryShortCount(count: Int, today: Boolean): String {
        return when (verbosity) {
            AssistantVerbosity.BRIEF -> {
                when (tone) {
                    AssistantTone.FRIENDLY ->
                        if (today) "Yes, $count today." else "Yes, $count active."
                    AssistantTone.NEUTRAL ->
                        if (today) "Yes, you have $count task${if (count > 1) "s" else ""} today."
                        else "Yes, you have $count active task${if (count > 1) "s" else ""}."
                    AssistantTone.PROFESSIONAL ->
                        if (today) "You have $count task${if (count > 1) "s" else ""} scheduled for today."
                        else "You have $count active task${if (count > 1) "s" else ""}."
                }
            }

            AssistantVerbosity.BALANCED,
            AssistantVerbosity.DETAILED -> {
                when (tone) {
                    AssistantTone.FRIENDLY ->
                        if (today) "Yes, you have $count task${if (count > 1) "s" else ""} today."
                        else "Yes, you have $count active task${if (count > 1) "s" else ""}."
                    AssistantTone.NEUTRAL ->
                        if (today) "Yes, you have $count task${if (count > 1) "s" else ""} today."
                        else "Yes, you have $count active task${if (count > 1) "s" else ""}."
                    AssistantTone.PROFESSIONAL ->
                        if (today) "You have $count task${if (count > 1) "s" else ""} scheduled for today."
                        else "You have $count active task${if (count > 1) "s" else ""}."
                }
            }
        }
    }

    fun queryIntro(count: Int, today: Boolean): String {
        if (verbosity == AssistantVerbosity.BRIEF) {
            return ""
        }

        return when (tone) {
            AssistantTone.FRIENDLY ->
                if (today) "You have $count task${if (count > 1) "s" else ""} today."
                else "You have $count active task${if (count > 1) "s" else ""}."

            AssistantTone.NEUTRAL ->
                if (today) "You have $count task${if (count > 1) "s" else ""} today."
                else "You have $count active task${if (count > 1) "s" else ""}."

            AssistantTone.PROFESSIONAL ->
                if (today) "You have $count task${if (count > 1) "s" else ""} scheduled for today."
                else "You have $count active task${if (count > 1) "s" else ""}."
        }
    }

    fun queryAndMore(remaining: Int): String {
        return when (verbosity) {
            AssistantVerbosity.BRIEF -> ""
            AssistantVerbosity.BALANCED,
            AssistantVerbosity.DETAILED -> when (tone) {
                AssistantTone.FRIENDLY -> " And $remaining more."
                AssistantTone.NEUTRAL -> " And $remaining more."
                AssistantTone.PROFESSIONAL -> " Plus $remaining more."
            }
        }
    }
    fun microphonePermissionNeeded(): String = when (tone) {
        AssistantTone.FRIENDLY -> "I need microphone permission before I can listen."
        AssistantTone.NEUTRAL -> "Microphone permission is needed for voice commands."
        AssistantTone.PROFESSIONAL -> "Microphone permission is required for voice input."
    }

    fun saveConfirmationHelp(): String = when (tone) {
        AssistantTone.FRIENDLY -> "Please say yes to save it, or tell me what you want to change."
        AssistantTone.NEUTRAL -> "Please say yes to save it, or say change title, change date, or change time."
        AssistantTone.PROFESSIONAL -> "Please confirm the save, or specify which field you would like to change."
    }

    fun getMaxTasksForMode(mode: QueryDetailMode): Int {
        return when (verbosity) {
            AssistantVerbosity.BRIEF -> 1
            AssistantVerbosity.BALANCED -> if (mode == QueryDetailMode.DETAILED) 3 else 2
            AssistantVerbosity.DETAILED -> if (mode == QueryDetailMode.DETAILED) 5 else 3
        }
    }
    fun followUpCreateAfterNoTasks(): String = when (tone) {
        AssistantTone.FRIENDLY -> "Would you like to create one?"
        AssistantTone.NEUTRAL -> "Would you like to create a task?"
        AssistantTone.PROFESSIONAL -> "Would you like to create a new task?"
    }

    fun followUpReadAllTasks(): String = when (tone) {
        AssistantTone.FRIENDLY -> "Do you want me to read all of them?"
        AssistantTone.NEUTRAL -> "Do you want me to read all of them?"
        AssistantTone.PROFESSIONAL -> "Would you like me to read the full task list?"
    }

    fun followUpAnythingElse(): String = when (tone) {
        AssistantTone.FRIENDLY -> "Anything else you’d like to do?"
        AssistantTone.NEUTRAL -> "Anything else?"
        AssistantTone.PROFESSIONAL -> "Is there anything else you would like to do?"
    }

    fun hintYesNo(): String = when (tone) {
        AssistantTone.FRIENDLY -> "You can say yes or no."
        AssistantTone.NEUTRAL -> "Say yes or no."
        AssistantTone.PROFESSIONAL -> "Please say yes or no."
    }

    fun hintChangeFields(): String = when (tone) {
        AssistantTone.FRIENDLY -> "You can say change title, change date, or change time."
        AssistantTone.NEUTRAL -> "Say change title, change date, or change time."
        AssistantTone.PROFESSIONAL -> "You may say change title, change date, or change time."
    }

    fun hintCreateOrRead(): String = when (tone) {
        AssistantTone.FRIENDLY -> "You can say create one, or read all."
        AssistantTone.NEUTRAL -> "Say create one, or read all."
        AssistantTone.PROFESSIONAL -> "You may say create one, or read all."
    }
    fun followUpCreateAccepted(): String = when (tone) {
        AssistantTone.FRIENDLY -> "Okay, let’s create one."
        AssistantTone.NEUTRAL -> "Okay, opening task creation."
        AssistantTone.PROFESSIONAL -> "Opening task creation."
    }

    fun followUpReadAllAccepted(): String = when (tone) {
        AssistantTone.FRIENDLY -> "Okay, I’ll read them all."
        AssistantTone.NEUTRAL -> "Okay, I’ll read all of them."
        AssistantTone.PROFESSIONAL -> "Reading the full task list."
    }

    fun combineReplyWithFollowUp(reply: String, followUp: String): String {
        return "$reply $followUp".trim()
    }

    fun followUpCreateOrStop(): String = when (tone) {
        AssistantTone.FRIENDLY -> "Would you like to create one?"
        AssistantTone.NEUTRAL -> "Would you like to create a task?"
        AssistantTone.PROFESSIONAL -> "Would you like to create a new task?"
    }

    fun followUpReadAllOrStop(): String = when (tone) {
        AssistantTone.FRIENDLY -> "Do you want me to read all of them?"
        AssistantTone.NEUTRAL -> "Do you want me to read all of them?"
        AssistantTone.PROFESSIONAL -> "Would you like me to read the full list?"
    }

    fun hintTitle(): String = when (tone) {
        AssistantTone.FRIENDLY -> "You can say the task title now."
        AssistantTone.NEUTRAL -> "Say the task title."
        AssistantTone.PROFESSIONAL -> "Please say the task title."
    }

    fun hintDate(): String = when (tone) {
        AssistantTone.FRIENDLY -> "You can say tomorrow, next Monday, or 25 March."
        AssistantTone.NEUTRAL -> "Say a date like tomorrow, next Monday, or 25 March."
        AssistantTone.PROFESSIONAL -> "Please provide a date such as tomorrow, next Monday, or 25 March."
    }

    fun hintTime(): String = when (tone) {
        AssistantTone.FRIENDLY -> "You can say 8 PM, 3 30 PM, or after lunch."
        AssistantTone.NEUTRAL -> "Say a time like 8 PM, 3 30 PM, or after lunch."
        AssistantTone.PROFESSIONAL -> "Please provide a time such as 8 PM, 3 30 PM, or after lunch."
    }

    fun confirmEditSummary(summary: String): String = when (tone) {
        AssistantTone.FRIENDLY -> "Okay, I updated it. I now have $summary. Should I save the changes?"
        AssistantTone.NEUTRAL -> "I updated it. I now have $summary. Should I save the changes?"
        AssistantTone.PROFESSIONAL -> "The task has been updated. I now have $summary. Would you like to save the changes?"
    }

    fun taskMatchAmbiguous(title1: String, title2: String): String = when (tone) {
        AssistantTone.FRIENDLY -> "I found two possible tasks: $title1, or $title2. Which one did you mean?"
        AssistantTone.NEUTRAL -> "I found two possible tasks: $title1, or $title2. Which one did you mean?"
        AssistantTone.PROFESSIONAL -> "I found two possible matching tasks: $title1, or $title2. Please tell me which one you meant."
    }

    fun taskMatchAmbiguityRetry(): String = when (tone) {
        AssistantTone.FRIENDLY -> "I still couldn’t tell which one you meant. Please say the first one, the second one, or say the task title."
        AssistantTone.NEUTRAL -> "I still could not tell which one you meant. Say the first one, the second one, or say the task title."
        AssistantTone.PROFESSIONAL -> "I could not determine which task you meant. Please say the first one, the second one, or say the task title."
    }

    fun taskMatchAmbiguityReset(): String = when (tone) {
        AssistantTone.FRIENDLY -> "I still couldn’t tell which task you meant. Let’s start over. Please say the full command again."
        AssistantTone.NEUTRAL -> "I still could not tell which task you meant. Please say the full command again."
        AssistantTone.PROFESSIONAL -> "I could not resolve the task selection. Please provide the full command again."
    }

    fun hintAmbiguityChoice(): String = when (tone) {
        AssistantTone.FRIENDLY -> "You can say first one, second one, or say the task title."
        AssistantTone.NEUTRAL -> "Say first one, second one, or the task title."
        AssistantTone.PROFESSIONAL -> "Please say first one, second one, or the task title."
    }

    fun deleteSuccess(title: String): String = when (tone) {
        AssistantTone.FRIENDLY -> "Okay, I deleted $title."
        AssistantTone.NEUTRAL -> "$title deleted."
        AssistantTone.PROFESSIONAL -> "$title has been deleted."
    }

    fun markDoneSuccess(title: String): String = when (tone) {
        AssistantTone.FRIENDLY -> "Okay, I marked $title as done."
        AssistantTone.NEUTRAL -> "$title marked as done."
        AssistantTone.PROFESSIONAL -> "$title has been marked as done."
    }

    fun markUndoneSuccess(title: String): String = when (tone) {
        AssistantTone.FRIENDLY -> "Okay, I marked $title as not done."
        AssistantTone.NEUTRAL -> "$title marked as not done."
        AssistantTone.PROFESSIONAL -> "$title has been marked as not done."
    }

    fun taskMatchNotFound(): String = when (tone) {
        AssistantTone.FRIENDLY -> "I couldn’t find a matching task. Please say the task title again."
        AssistantTone.NEUTRAL -> "I could not find a matching task. Please say the task title again."
        AssistantTone.PROFESSIONAL -> "I could not find a matching task. Please provide the task title again."
    }


    fun openEditTask(): String = when (tone) {
        AssistantTone.FRIENDLY -> "Okay, opening edit task."
        AssistantTone.NEUTRAL -> "Opening edit task."
        AssistantTone.PROFESSIONAL -> "Opening the edit task screen."
    }

    fun openReschedule(): String = when (tone) {
        AssistantTone.FRIENDLY -> "Okay, opening reschedule."
        AssistantTone.NEUTRAL -> "Opening reschedule."
        AssistantTone.PROFESSIONAL -> "Opening the reschedule screen."
    }

    fun editIntro(title: String): String = when (tone) {
        AssistantTone.FRIENDLY -> "You are editing $title. What would you like to change?"
        AssistantTone.NEUTRAL -> "You are editing $title. What would you like to change?"
        AssistantTone.PROFESSIONAL -> "You are editing $title. Please tell me what you would like to change."
    }

    fun rescheduleIntro(title: String, changedDate: Boolean, changedTime: Boolean): String {
        val updateText = when {
            changedDate && changedTime -> " I updated the date and time."
            changedDate -> " I updated the date."
            changedTime -> " I updated the time."
            else -> ""
        }

        return when (tone) {
            AssistantTone.FRIENDLY -> "You are rescheduling $title.$updateText Would you like me to save the changes?"
            AssistantTone.NEUTRAL -> "You are rescheduling $title.$updateText Would you like me to save the changes?"
            AssistantTone.PROFESSIONAL -> "You are rescheduling $title.$updateText Would you like to save the changes?"
        }
    }

    fun editDeleteCurrent(): String = when (tone) {
        AssistantTone.FRIENDLY -> "Okay, deleting this task."
        AssistantTone.NEUTRAL -> "Deleting this task."
        AssistantTone.PROFESSIONAL -> "Deleting the current task."
    }

    fun editHelp(): String = when (tone) {
        AssistantTone.FRIENDLY -> "You can say change title, change date, change time, save, or delete this task."
        AssistantTone.NEUTRAL -> "Say change title, change date, change time, save, or delete this task."
        AssistantTone.PROFESSIONAL -> "You may say change title, change date, change time, save, or delete this task."
    }

    fun editContextReminder(): String = when (tone) {
        AssistantTone.FRIENDLY -> "You are editing a task. Tell me what you want to change."
        AssistantTone.NEUTRAL -> "You are editing a task. Tell me what you want to change."
        AssistantTone.PROFESSIONAL -> "You are editing a task. Please tell me what you would like to change."
    }

    fun invalidEditDate(): String = when (tone) {
        AssistantTone.FRIENDLY -> "I couldn’t understand the date. Try saying tomorrow, next Monday, or 25 March."
        AssistantTone.NEUTRAL -> "I could not understand the date. Try saying tomorrow, next Monday, or 25 March."
        AssistantTone.PROFESSIONAL -> "I could not interpret the date. Please say a date such as tomorrow, next Monday, or 25 March."
    }

    fun invalidEditTime(): String = when (tone) {
        AssistantTone.FRIENDLY -> "I couldn’t understand the time. Try saying 3 PM, 3:45 PM, afternoon, or after lunch."
        AssistantTone.NEUTRAL -> "I could not understand the time. Try saying 3 PM, 3:45 PM, afternoon, or after lunch."
        AssistantTone.PROFESSIONAL -> "I could not interpret the time. Please say a time such as 3 PM, 3:45 PM, afternoon, or after lunch."
    }

    fun invalidEditTitle(): String = when (tone) {
        AssistantTone.FRIENDLY -> "I didn’t catch the new title. Please say it again."
        AssistantTone.NEUTRAL -> "I did not catch the new title. Please say it again."
        AssistantTone.PROFESSIONAL -> "Please provide the new title again."
    }

    fun cancelEdit(): String = when (tone) {
        AssistantTone.FRIENDLY -> "Okay, cancelling task editing."
        AssistantTone.NEUTRAL -> "Cancelling task editing."
        AssistantTone.PROFESSIONAL -> "Task editing has been cancelled."
    }

    fun returnHomeFromEdit(): String = when (tone) {
        AssistantTone.FRIENDLY -> "Okay, returning home."
        AssistantTone.NEUTRAL -> "Returning home."
        AssistantTone.PROFESSIONAL -> "Returning to the home screen."
    }
    fun editParseFailure(): String = when (tone) {
        AssistantTone.FRIENDLY -> "I ran into a problem understanding that. Please try again."
        AssistantTone.NEUTRAL -> "I had trouble understanding that. Please try again."
        AssistantTone.PROFESSIONAL -> "I could not process that request. Please try again."
    }
    companion object {
        fun fromPreferences(context: android.content.Context): AssistantResponseManager {
            val prefs = context.getSharedPreferences(
                SettingsActivity.PREFS_NAME,
                android.content.Context.MODE_PRIVATE
            )

            val tone = when (prefs.getString(SettingsActivity.KEY_ASSISTANT_TONE, "Friendly")) {
                "Neutral" -> AssistantTone.NEUTRAL
                "Professional" -> AssistantTone.PROFESSIONAL
                else -> AssistantTone.FRIENDLY
            }

            val verbosity = when (prefs.getString(SettingsActivity.KEY_REPLY_LENGTH, "Normal")) {
                "Short" -> AssistantVerbosity.BRIEF
                "Detailed" -> AssistantVerbosity.DETAILED
                else -> AssistantVerbosity.BALANCED
            }

            return AssistantResponseManager(
                AssistantProfile(
                    tone = tone,
                    verbosity = verbosity
                )
            )
        }
    }

}

class UserPreferenceState {
    var prefersBrief = 0
    var prefersDetailed = 0

    fun getPreferredVerbosity(): AssistantVerbosity {
        return if (prefersBrief > prefersDetailed) {
            AssistantVerbosity.BRIEF
        } else {
            AssistantVerbosity.BALANCED
        }
    }
}


