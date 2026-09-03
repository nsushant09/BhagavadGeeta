package com.neupanesushant.bhagavadgeeta

import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

class IosNotificationScheduler : NotificationScheduler {
    override fun scheduleMorningNotification(enabled: Boolean, title: String, body: String) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        if (!enabled) {
            center.removePendingNotificationRequestsWithIdentifiers(listOf("morning_discipline_notification"))
            return
        }

        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        ) { granted, _ ->
            if (granted) {
                val content = UNMutableNotificationContent().apply {
                    setTitle(title.ifBlank { "Morning Discipline · 5:00 AM" })
                    setBody(body.ifBlank { "Start your day with today's verse contemplation from the Bhagavad Gita." })
                    setSound(UNNotificationSound.defaultSound)
                }

                val dateComponents = NSDateComponents().apply {
                    hour = 5
                    minute = 0
                }

                val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
                    dateComponents = dateComponents,
                    repeats = true
                )

                val request = UNNotificationRequest.requestWithIdentifier(
                    identifier = "morning_discipline_notification",
                    content = content,
                    trigger = trigger
                )

                center.addNotificationRequest(request, null)
            }
        }
    }
}
