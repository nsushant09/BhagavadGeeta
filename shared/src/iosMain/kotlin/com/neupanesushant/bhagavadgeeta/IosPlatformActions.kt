package com.neupanesushant.bhagavadgeeta

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

class IosPlatformActions : PlatformActions {
    private fun open(url: String) { NSURL.URLWithString(url)?.let { UIApplication.sharedApplication.openURL(it) } }
    override fun feedback() = open("mailto:feedback@example.com?subject=Bhagavad%20Gita%20Reader%20feedback")
    override fun shareApp() = open("https://apps.apple.com/")
    override fun rateApp() = open("https://apps.apple.com/")
    override fun openUrl(url: String) = open(url)
}
