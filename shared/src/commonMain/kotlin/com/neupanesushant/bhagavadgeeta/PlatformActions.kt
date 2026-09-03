package com.neupanesushant.bhagavadgeeta

interface PlatformActions {
    fun feedback()
    fun shareApp()
    fun rateApp()
    fun openUrl(url: String)
}

class NoOpPlatformActions : PlatformActions {
    override fun feedback() = Unit
    override fun shareApp() = Unit
    override fun rateApp() = Unit
    override fun openUrl(url: String) = Unit
}
