package com.neupanesushant.bhagavadgeeta

interface NotificationScheduler {
    fun scheduleMorningNotification(enabled: Boolean, title: String, body: String)
}

class NoOpNotificationScheduler : NotificationScheduler {
    override fun scheduleMorningNotification(enabled: Boolean, title: String, body: String) = Unit
}
