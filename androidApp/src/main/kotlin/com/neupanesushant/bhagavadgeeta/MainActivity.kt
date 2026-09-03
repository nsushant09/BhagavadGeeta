package com.neupanesushant.bhagavadgeeta

import android.content.pm.PackageManager
import android.os.Build
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        val notificationScheduler = AndroidNotificationScheduler(this)

        setContent {
            App(
                storage = AndroidAppStorage(this),
                actions = AndroidPlatformActions(this),
                audioPlayer = AndroidVerseAudioPlayer(this),
                notificationScheduler = notificationScheduler
            )
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(
        storage = MemoryAppStorage(),
        actions = NoOpPlatformActions(),
        audioPlayer = NoOpVerseAudioPlayer(),
        notificationScheduler = NoOpNotificationScheduler()
    )
}
