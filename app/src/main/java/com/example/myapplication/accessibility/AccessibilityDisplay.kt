package com.example.myapplication.accessibility

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R
import com.example.myapplication.preferences.AppPreferences

object AccessibilityTextScale {
    const val LARGE_TEXT_MULTIPLIER = 1.22f

    fun resolveFontScale(systemFontScale: Float, largeTextEnabled: Boolean): Float =
        if (largeTextEnabled) systemFontScale * LARGE_TEXT_MULTIPLIER else systemFontScale
}

data class AccessibilityDisplayMode(
    val largeTextEnabled: Boolean,
    val highContrastEnabled: Boolean
)

abstract class AccessibilityActivity : AppCompatActivity() {
    private lateinit var appliedDisplayMode: AccessibilityDisplayMode

    override fun attachBaseContext(newBase: Context) {
        val preferences = AppPreferences(newBase)
        val systemConfiguration = newBase.applicationContext.resources.configuration
        val activityConfiguration = Configuration(newBase.resources.configuration).apply {
            fontScale = AccessibilityTextScale.resolveFontScale(
                systemFontScale = systemConfiguration.fontScale,
                largeTextEnabled = preferences.largeTextEnabled
            )
        }
        super.attachBaseContext(newBase.createConfigurationContext(activityConfiguration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        appliedDisplayMode = currentDisplayMode()
        setTheme(
            if (appliedDisplayMode.highContrastEnabled) {
                R.style.Theme_MyApplication_HighContrast
            } else {
                R.style.Theme_MyApplication
            }
        )
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        if (currentDisplayMode() != appliedDisplayMode) recreate()
    }

    private fun currentDisplayMode(): AccessibilityDisplayMode {
        val preferences = AppPreferences(this)
        return AccessibilityDisplayMode(
            largeTextEnabled = preferences.largeTextEnabled,
            highContrastEnabled = preferences.highContrastEnabled
        )
    }
}

@ColorInt
fun Context.resolveThemeColor(@AttrRes attribute: Int): Int {
    val value = TypedValue()
    check(theme.resolveAttribute(attribute, value, true)) {
        "Theme attribute $attribute is not defined"
    }
    return value.data
}
