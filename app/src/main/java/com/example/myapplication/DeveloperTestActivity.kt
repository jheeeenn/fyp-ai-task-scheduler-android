package com.example.myapplication

import android.os.Bundle

/** Entry activity only; all assistant/business behavior remains inherited from HomeActivity. */
class DeveloperTestActivity : HomeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!DeveloperTestSession.isActive) finish()
    }
}
