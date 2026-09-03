package com.neupanesushant.bhagavadgeeta

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import android.view.View

@Composable
actual fun PlatformSystemBars(darkTheme: Boolean) {
    val view = LocalView.current
    SideEffect {
        val decorView = (view.context as? Activity)?.window?.decorView ?: return@SideEffect
        var flags = decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        flags = if (darkTheme) flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        else flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        decorView.systemUiVisibility = flags
    }
}
