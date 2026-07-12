package com.yage.opencode_client

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import com.yage.opencode_client.util.wrapWithLanguage
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class OpenCodeApp : Application() {
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base.wrapWithLanguage())
    }

    override fun onCreate() {
        super.onCreate()
        warmUpWebViewAfterLaunch()
    }

    /**
     * Pre-create an off-screen WebView right after launch so Chromium's first-time initialization
     * happens here instead of when the user first opens a Markdown Web Preview. Eliminates the
     * one-time black flash on the first Markdown preview open.
     */
    private fun warmUpWebViewAfterLaunch() {
        Handler(Looper.getMainLooper()).post {
            runCatching {
                warmedWebView = WebView(applicationContext).apply {
                    loadUrl("about:blank")
                }
            }
        }
    }

    companion object {
        private var warmedWebView: WebView? = null
    }
}
