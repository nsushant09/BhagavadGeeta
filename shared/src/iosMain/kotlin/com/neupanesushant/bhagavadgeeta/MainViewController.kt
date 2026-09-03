package com.neupanesushant.bhagavadgeeta

import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController {
    App(
        storage = IosAppStorage(),
        actions = IosPlatformActions(),
        audioPlayer = IosVerseAudioPlayer(),
        notificationScheduler = IosNotificationScheduler()
    )
}
