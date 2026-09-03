package com.neupanesushant.bhagavadgeeta

import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController { App(IosAppStorage(), IosPlatformActions(), IosVerseAudioPlayer()) }
