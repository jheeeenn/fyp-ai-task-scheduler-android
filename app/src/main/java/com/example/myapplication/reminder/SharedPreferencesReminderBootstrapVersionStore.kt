package com.example.myapplication.reminder

import android.content.Context

class SharedPreferencesReminderBootstrapVersionStore(
    context: Context
) : ReminderBootstrapVersionStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override fun currentVersion(): Int =
        preferences.getInt(KEY_SCHEMA_VERSION, 0)

    override fun recordVersion(version: Int): Boolean =
        preferences.edit()
            .putInt(KEY_SCHEMA_VERSION, version)
            .commit()

    companion object {
        private const val PREFERENCES_NAME = "reminder_escalation_bootstrap"
        private const val KEY_SCHEMA_VERSION = "schema_version"
    }
}
