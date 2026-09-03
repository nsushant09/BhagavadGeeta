package com.neupanesushant.bhagavadgeeta

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val storage = AndroidAppStorage(context)
            val enabled = storage.read(StorageKeys.NotificationEnabled) == "true"
            if (enabled) {
                val scheduler = AndroidNotificationScheduler(context)
                scheduler.scheduleMorningNotification(
                    enabled = true,
                    title = "Morning Discipline · Today's Contemplation",
                    body = "Start your day with today's verse contemplation from the Bhagavad Gita."
                )
            }
        }
    }
}
