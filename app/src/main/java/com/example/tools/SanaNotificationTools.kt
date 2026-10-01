package com.example.tools

import android.content.Context
import com.example.data.model.ToolCallResult
import com.example.service.SanaNotificationListenerService

class SanaNotificationTools(private val context: Context) {

    /**
     * Reads and summarizes active notifications captured by SanaNotificationListenerService.
     */
    fun readSupportedNotifications(): ToolCallResult {
        if (!SanaNotificationListenerService.isNotificationAccessGranted(context)) {
            return ToolCallResult(
                action = "readSupportedNotifications",
                target = "Notification Access",
                success = false,
                message = "Boss, to read your notifications, Android requires Notification Access. You can enable it in the Permissions Center or Settings."
            )
        }

        val notifications = SanaNotificationListenerService.activeNotificationsList.value

        if (notifications.isEmpty()) {
            return ToolCallResult(
                action = "readSupportedNotifications",
                target = "Notifications",
                success = true,
                message = "You have no unread notifications right now, Boss! Everything is quiet and peaceful. ❤️"
            )
        }

        val messagingCount = notifications.count { it.isMessaging }
        val otherCount = notifications.size - messagingCount

        val summaryBuilder = StringBuilder()
        summaryBuilder.append("You have ${notifications.size} notification${if (notifications.size > 1) "s" else ""}, Boss. ")

        val topItem = notifications.firstOrNull()
        if (topItem != null) {
            summaryBuilder.append("Latest from ${topItem.appName}: \"${topItem.title} - ${topItem.text.take(60)}\". ")
        }

        if (messagingCount > 1) {
            summaryBuilder.append("You have $messagingCount messaging notifications. ")
        } else if (otherCount > 0) {
            summaryBuilder.append("And $otherCount other system notification${if (otherCount > 1) "s" else ""}.")
        }

        return ToolCallResult(
            action = "readSupportedNotifications",
            target = "${notifications.size} items",
            success = true,
            message = summaryBuilder.toString().trim()
        )
    }

    /**
     * Opens system notification access settings page.
     */
    fun openNotificationAccessSettings(): ToolCallResult {
        return try {
            SanaNotificationListenerService.openNotificationAccessSettings(context)
            ToolCallResult(
                action = "openNotificationAccessSettings",
                target = "Settings",
                success = true,
                message = "Opening Notification Access settings, Boss. Please turn ON SANA AI."
            )
        } catch (e: Exception) {
            ToolCallResult("openNotificationAccessSettings", "Settings", false, "Could not open notification settings: ${e.message}")
        }
    }
}
