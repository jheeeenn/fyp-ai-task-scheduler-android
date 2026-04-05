package com.example.myapplication

import android.app.Dialog
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "assistant_settings"
        const val KEY_ASSISTANT_TONE = "assistant_tone"
        const val KEY_REPLY_LENGTH = "reply_length"
        const val KEY_LARGE_TEXT = "large_text"
        const val KEY_HIGH_CONTRAST = "high_contrast"
    }

    private lateinit var prefs: SharedPreferences

    private lateinit var tvToneValue: TextView
    private lateinit var tvReplyLengthValue: TextView
    private lateinit var tvLargeTextValue: TextView
    private lateinit var tvHighContrastValue: TextView

    private var assistantTone: String = "Friendly"
    private var replyLength: String = "Normal"
    private var largeText: Boolean = false
    private var highContrast: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val cardTone = findViewById<LinearLayout>(R.id.cardTone)
        val cardReplyLength = findViewById<LinearLayout>(R.id.cardReplyLength)
        val cardLargeText = findViewById<LinearLayout>(R.id.cardLargeText)
        val cardHighContrast = findViewById<LinearLayout>(R.id.cardHighContrast)

        val btnGoHome = findViewById<Button>(R.id.btnGoHome)
        val btnTalkAssistant = findViewById<Button>(R.id.btnTalkAssistant)

        tvToneValue = findViewById(R.id.tvToneValue)
        tvReplyLengthValue = findViewById(R.id.tvReplyLengthValue)
        tvLargeTextValue = findViewById(R.id.tvLargeTextValue)
        tvHighContrastValue = findViewById(R.id.tvHighContrastValue)

        loadSettings()
        updateUiValues()

        cardTone.setOnClickListener {
            showOptionDialog(
                title = "Assistant Tone",
                options = listOf("Friendly", "Neutral", "Professional"),
                currentValue = assistantTone
            ) { selected ->
                assistantTone = selected
                saveSettings()
                updateUiValues()
            }
        }

        cardReplyLength.setOnClickListener {
            showOptionDialog(
                title = "Reply Length",
                options = listOf("Short", "Normal", "Detailed"),
                currentValue = replyLength
            ) { selected ->
                replyLength = selected
                saveSettings()
                updateUiValues()
            }
        }

        cardLargeText.setOnClickListener {
            showOptionDialog(
                title = "Large Text",
                options = listOf("Off", "On"),
                currentValue = if (largeText) "On" else "Off"
            ) { selected ->
                largeText = selected == "On"
                saveSettings()
                updateUiValues()
            }
        }

        cardHighContrast.setOnClickListener {
            showOptionDialog(
                title = "High Contrast",
                options = listOf("Off", "On"),
                currentValue = if (highContrast) "On" else "Off"
            ) { selected ->
                highContrast = selected == "On"
                saveSettings()
                updateUiValues()
            }
        }

        btnGoHome.setOnClickListener {
            finish()
        }

        btnTalkAssistant.setOnClickListener {
            Toast.makeText(this, "Voice settings assistant can be added later.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSettings() {
        assistantTone = prefs.getString(KEY_ASSISTANT_TONE, "Friendly") ?: "Friendly"
        replyLength = prefs.getString(KEY_REPLY_LENGTH, "Normal") ?: "Normal"
        largeText = prefs.getBoolean(KEY_LARGE_TEXT, false)
        highContrast = prefs.getBoolean(KEY_HIGH_CONTRAST, false)
    }

    private fun saveSettings() {
        prefs.edit()
            .putString(KEY_ASSISTANT_TONE, assistantTone)
            .putString(KEY_REPLY_LENGTH, replyLength)
            .putBoolean(KEY_LARGE_TEXT, largeText)
            .putBoolean(KEY_HIGH_CONTRAST, highContrast)
            .apply()
    }

    private fun updateUiValues() {
        tvToneValue.text = assistantTone
        tvReplyLengthValue.text = replyLength
        tvLargeTextValue.text = if (largeText) "On" else "Off"
        tvHighContrastValue.text = if (highContrast) "On" else "Off"
    }

    private fun showOptionDialog(
        title: String,
        options: List<String>,
        currentValue: String,
        onSelected: (String) -> Unit
    ) {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_setting_options, null)
        dialog.setContentView(view)

        val tvDialogTitle = view.findViewById<TextView>(R.id.tvDialogTitle)
        val radioGroupOptions = view.findViewById<RadioGroup>(R.id.radioGroupOptions)
        val btnDialogSave = view.findViewById<Button>(R.id.btnDialogSave)

        tvDialogTitle.text = title

        options.forEach { option ->
            val radioButton = RadioButton(this).apply {
                text = option
                textSize = 20f
                setTextColor(android.graphics.Color.parseColor("#F5F1FF"))
                isChecked = option == currentValue
            }
            radioGroupOptions.addView(radioButton)
        }

        btnDialogSave.setOnClickListener {
            val checkedId = radioGroupOptions.checkedRadioButtonId
            if (checkedId != -1) {
                val selectedButton = radioGroupOptions.findViewById<RadioButton>(checkedId)
                onSelected(selectedButton.text.toString())
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}