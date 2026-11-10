package app.luxion.fitarchiveapp.reminders

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.luxion.fitarchiveapp.domain.PlannedReminder
import kotlinx.coroutines.CompletableDeferred
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

/**
 * Notificaciones locales con `UNUserNotificationCenter`: cada aviso es una notificación de calendario de un solo
 * disparo (iOS admite hasta 64 pendientes; el plan de 7 días queda por debajo). Solo comprobado el tipado en Windows
 * (docs/ios-pendiente.md).
 */
private class IosReminderScheduler : ReminderScheduler {
    private val center get() = UNUserNotificationCenter.currentNotificationCenter()

    override val supported: Boolean = true

    override suspend fun hasPermission(): Boolean {
        val result = CompletableDeferred<Boolean>()
        center.getNotificationSettingsWithCompletionHandler { settings ->
            val status = settings?.authorizationStatus
            result.complete(status == UNAuthorizationStatusAuthorized || status == UNAuthorizationStatusProvisional)
        }
        return result.await()
    }

    override suspend fun requestPermission(): Boolean {
        val result = CompletableDeferred<Boolean>()
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { granted, _ ->
            result.complete(granted)
        }
        return result.await()
    }

    override fun replaceAll(reminders: List<PlannedReminder>) {
        center.removeAllPendingNotificationRequests()
        for (r in reminders) {
            val parts = r.date.split("-")
            if (parts.size != 3) continue
            val components = NSDateComponents().apply {
                year = parts[0].toLong()
                month = parts[1].toLong()
                day = parts[2].toLong()
                hour = (r.minutes / 60).toLong()
                minute = (r.minutes % 60).toLong()
            }
            val content = UNMutableNotificationContent().apply {
                setTitle(r.title)
                setBody(r.body)
                setSound(UNNotificationSound.defaultSound)
            }
            val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false)
            val request = UNNotificationRequest.requestWithIdentifier("easyfood-${r.id}", content, trigger)
            center.addNotificationRequest(request, withCompletionHandler = null)
        }
    }
}

@Composable
actual fun rememberReminderScheduler(): ReminderScheduler = remember { IosReminderScheduler() }
