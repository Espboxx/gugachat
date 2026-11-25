package com.gugachat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gugachat.app.ui.navigation.GuGuChatRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as GuGuChatApp).container
        setContent {
            GuGuChatRoot(container = container)
        }
    }
}
