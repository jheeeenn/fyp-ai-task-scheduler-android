package com.example.myapplication.voice

enum class LocalDialogAct {
    YES,
    NO,
    SAVE,
    CANCEL,
    STOP,
    END,
    DATE,
    TIME,
    TITLE,
    DELETE,
    DONE,
    UNDO
}

object LocalDialogActInterpreter {
    fun detect(normalized: String): LocalDialogAct? {
        val text = normalized.trim().lowercase()
        return when {
            text in setOf("yes", "yeah", "yep", "ok", "okay") -> LocalDialogAct.YES
            text in setOf("no", "nope") -> LocalDialogAct.NO
            text in setOf("save", "save it", "safe") -> LocalDialogAct.SAVE
            text in setOf("cancel") -> LocalDialogAct.CANCEL
            text in setOf("stop", "stop listening") -> LocalDialogAct.STOP
            text in setOf("that's all", "thats all", "nothing else", "done", "all done") -> LocalDialogAct.END
            text in setOf("date", "the date") -> LocalDialogAct.DATE
            text in setOf("time", "the time") -> LocalDialogAct.TIME
            text in setOf("title", "the title") -> LocalDialogAct.TITLE
            text in setOf("delete", "delete task", "delete this") -> LocalDialogAct.DELETE
            text in setOf("done", "mark done") -> LocalDialogAct.DONE
            text in setOf("undo", "mark undone", "not done") -> LocalDialogAct.UNDO
            else -> null
        }
    }
}
