package com.twinspace.app

import android.app.Application
import android.os.Build
import android.webkit.WebView

class TwinSpaceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val name = getProcessName()
            if (name != packageName) {
                val suffix = name.substringAfterLast(':').ifBlank { "clone" }
                try {
                    WebView.setDataDirectorySuffix(suffix)
                } catch (_: IllegalStateException) {
                    // Already initialized in this process.
                }
            }
        }
    }
}
