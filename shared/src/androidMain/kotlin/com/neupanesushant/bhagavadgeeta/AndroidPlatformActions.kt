package com.neupanesushant.bhagavadgeeta

import android.content.Context
import android.content.Intent
import android.net.Uri

class AndroidPlatformActions(private val context: Context) : PlatformActions {
    private fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    override fun feedback() = launch(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:feedback@example.com?subject=Bhagavad%20Gita%20Reader%20feedback")))
    override fun shareApp() = launch(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Read the Bhagavad Gita with me: https://play.google.com/store/apps/details?id=${context.packageName}")
    }, "Share Bhagavad Gita Reader"))
    override fun rateApp() = launch(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}")))
    override fun openUrl(url: String) = launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
