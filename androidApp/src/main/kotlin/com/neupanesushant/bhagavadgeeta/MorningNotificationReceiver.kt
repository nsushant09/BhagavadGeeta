package com.neupanesushant.bhagavadgeeta

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class MorningNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title")
            ?.ifBlank { "Morning Discipline · 5:00 AM" }
            ?: "Morning Discipline · 5:00 AM"
        val body = intent.getStringExtra("body")
            ?.ifBlank { "Start your day with today's verse contemplation from the Bhagavad Gita." }
            ?: "Start your day with today's verse contemplation from the Bhagavad Gita."

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            pendingFlags
        )

        val notification = NotificationCompat.Builder(context, AndroidNotificationScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            notificationManager.notify(1001, notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        // Reschedule for next 5:00 AM
        val scheduler = AndroidNotificationScheduler(context)
        scheduler.scheduleMorningNotification(true, title, body)
    }
}
