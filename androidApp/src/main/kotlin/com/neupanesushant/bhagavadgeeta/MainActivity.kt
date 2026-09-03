package com.neupanesushant.bhagavadgeeta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(AndroidAppStorage(this), AndroidPlatformActions(this), AndroidVerseAudioPlayer(this))
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(MemoryAppStorage(), NoOpPlatformActions(), NoOpVerseAudioPlayer())
}
