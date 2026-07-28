package com.ajimsjames.wearappupdater

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ajimsjames.wearappupdater.ui.UpdaterScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UpdaterScreen()
        }
    }
}
