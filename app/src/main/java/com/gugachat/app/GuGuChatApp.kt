package com.gugachat.app

import android.app.Application
import com.gugachat.app.core.runtime.AppForegroundTracker
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class GuGuChatApp : Application() {
    val foregroundTracker = AppForegroundTracker()
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(foregroundTracker)
        runCatching { PDFBoxResourceLoader.init(applicationContext) }
    }
}
