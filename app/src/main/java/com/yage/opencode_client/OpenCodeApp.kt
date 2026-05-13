package com.yage.opencode_client

import android.app.Application
import com.yage.opencode_client.util.wrapWithLanguage
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class OpenCodeApp : Application() {
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base.wrapWithLanguage())
    }
}
