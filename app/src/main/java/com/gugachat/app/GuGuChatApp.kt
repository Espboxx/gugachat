package com.gugachat.app

import android.app.Application

class GuGuChatApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
