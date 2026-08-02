package com.example.myapplication.accessibility

import android.content.Context
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

object AccessibleAssistantInputDialog {
    fun show(
        activity: AppCompatActivity,
        title: String,
        message: String,
        emptyError: String,
        onSubmit: (String) -> Unit
    ) {
        val inputId = View.generateViewId()
        val input = EditText(activity).apply {
            id = inputId
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_SEND
            minHeight = 56.dp(activity)
            minLines = 2
            maxLines = 4
            setSingleLine(false)
        }
        val label = TextView(activity).apply {
            text = activity.getString(com.example.myapplication.R.string.message_to_assistant)
            textSize = 18f
            labelFor = inputId
        }
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val horizontal = 24.dp(activity)
            setPadding(horizontal, 8.dp(activity), horizontal, 0)
            addView(label, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            addView(input, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Send", null)
            .create()

        fun submit() {
            val typedText = input.text.toString().trim()
            if (typedText.isEmpty()) {
                input.error = emptyError
                input.requestFocus()
                return
            }
            dialog.dismiss()
            onSubmit(typedText)
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { submit() }
            input.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            input.post {
                val keyboard = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                keyboard.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submit()
                true
            } else {
                false
            }
        }
        dialog.show()
    }

    private fun Int.dp(context: Context): Int =
        (this * context.resources.displayMetrics.density).toInt()
}
