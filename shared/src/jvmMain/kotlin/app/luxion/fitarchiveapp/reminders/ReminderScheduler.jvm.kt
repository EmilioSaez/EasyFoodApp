package app.luxion.fitarchiveapp.reminders

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.luxion.fitarchiveapp.domain.PlannedReminder
import java.awt.GraphicsEnvironment
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.time.LocalDate
import java.time.ZoneId
import java.util.Timer
import java.util.TimerTask

/**
 * Escritorio: notificación del sistema (bandeja) mientras la app está abierta. No hay servicio en segundo plano, así
 * que con la app cerrada no avisa. Sin bandeja del sistema (p. ej. sin entorno gráfico) no hace nada.
 */
private class DesktopReminderScheduler : ReminderScheduler {
    private val trayAvailable: Boolean =
        !GraphicsEnvironment.isHeadless() && try { SystemTray.isSupported() } catch (e: Throwable) { false }

    private var timer: Timer? = null
    private var icon: TrayIcon? = null

    override val supported: Boolean get() = trayAvailable

    override val limitation: String? =
        if (trayAvailable) "En escritorio los avisos solo salen mientras la app está abierta." else null

    override suspend fun hasPermission(): Boolean = trayAvailable
    override suspend fun requestPermission(): Boolean = trayAvailable

    @Synchronized
    override fun replaceAll(reminders: List<PlannedReminder>) {
        timer?.cancel()
        timer = null
        if (!trayAvailable || reminders.isEmpty()) return
        val now = System.currentTimeMillis()
        val next = Timer("easyfood-reminders", true)
        for (r in reminders) {
            val at = LocalDate.parse(r.date).atTime(r.minutes / 60, r.minutes % 60)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (at <= now) continue
            next.schedule(object : TimerTask() {
                override fun run() = show(r.title, r.body)
            }, at - now)
        }
        timer = next
    }

    @Synchronized
    private fun show(title: String, body: String) {
        try {
            val tray = SystemTray.getSystemTray()
            val existing = icon ?: TrayIcon(BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), "EasyFood").also {
                it.isImageAutoSize = true
                tray.add(it)
                icon = it
            }
            existing.displayMessage(title, body, TrayIcon.MessageType.INFO)
        } catch (e: Exception) {
            // Sin bandeja disponible en este momento: el aviso se pierde sin molestar.
        }
    }
}

@Composable
actual fun rememberReminderScheduler(): ReminderScheduler = remember { DesktopReminderScheduler() }
