package com.gugachat.app

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class GuGuChatApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        runCatching { PDFBoxResourceLoader.init(applicationContext) }
    }
}
