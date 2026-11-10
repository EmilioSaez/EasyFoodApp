package app.luxion.fitarchiveapp.reminders

import androidx.compose.runtime.Composable
import app.luxion.fitarchiveapp.domain.PlannedReminder

/**
 * Notificaciones locales del sistema. Quien decide qué avisar es [app.luxion.fitarchiveapp.domain.ReminderPlanner];
 * esto solo las entrega a cada plataforma (Android: AlarmManager, iOS: UNUserNotificationCenter, escritorio: bandeja
 * del sistema mientras la app está abierta).
 */
interface ReminderScheduler {
    /** `false` si la plataforma no puede mostrar notificaciones (p. ej. escritorio sin bandeja del sistema). */
    val supported: Boolean

    /** Nota para el usuario sobre limitaciones de la plataforma (se enseña en la pantalla de recordatorios), o `null`. */
    val limitation: String? get() = null

    /** `true` si el usuario ya ha permitido las notificaciones (o la plataforma no pide permiso). */
    suspend fun hasPermission(): Boolean

    /** Pide el permiso de notificaciones si hace falta; devuelve si quedó concedido. */
    suspend fun requestPermission(): Boolean

    /** Sustituye todo lo programado antes por [reminders]. Lista vacía = cancelar todo. */
    fun replaceAll(reminders: List<PlannedReminder>)
}

/** [ReminderScheduler] de la plataforma actual (necesita el contexto de Compose, p. ej. para pedir permisos en Android). */
@Composable
expect fun rememberReminderScheduler(): ReminderScheduler

/** Scheduler que no hace nada: para tests y plataformas sin notificaciones. */
object NoReminderScheduler : ReminderScheduler {
    override val supported: Boolean = false
    override suspend fun hasPermission(): Boolean = false
    override suspend fun requestPermission(): Boolean = false
    override fun replaceAll(reminders: List<PlannedReminder>) {}
}
