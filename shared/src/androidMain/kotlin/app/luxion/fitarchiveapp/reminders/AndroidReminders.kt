package app.luxion.fitarchiveapp.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.luxion.fitarchiveapp.domain.PlannedReminder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId

/** Programación de avisos con AlarmManager (sin alarmas exactas: pueden retrasarse unos minutos con Doze). */
internal object AndroidReminders {
    const val CHANNEL_ID = "recordatorios"
    private const val PREFS = "easyfood_reminders"
    private const val KEY_PLAN = "plan"
    private const val EXTRA_ID = "id"
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_BODY = "body"
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(PlannedReminder.serializer())

    fun triggerMillis(r: PlannedReminder): Long =
        LocalDate.parse(r.date).atTime(r.minutes / 60, r.minutes % 60).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun load(context: Context): List<PlannedReminder> {
        val text = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PLAN, null) ?: return emptyList()
        return try {
            json.decodeFromString(serializer, text)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(context: Context, plan: List<PlannedReminder>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_PLAN, json.encodeToString(serializer, plan)).apply()
    }

    private fun pendingIntent(context: Context, r: PlannedReminder, create: Boolean): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(EXTRA_ID, r.id).putExtra(EXTRA_TITLE, r.title).putExtra(EXTRA_BODY, r.body)
        val flags = PendingIntent.FLAG_IMMUTABLE or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, r.id, intent, flags)
    }

    /** Cancela lo programado antes y programa [plan] (solo lo que aún no ha pasado). */
    fun replaceAll(context: Context, plan: List<PlannedReminder>) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        load(context).forEach { old ->
            pendingIntent(context, old, create = false)?.let {
                alarms.cancel(it)
                it.cancel()
            }
        }
        save(context, plan)
        scheduleStored(context, alarms, plan)
    }

    /** Tras reiniciar el móvil o actualizar la app las alarmas se pierden: se reprograma el último plan guardado. */
    fun restore(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        scheduleStored(context, alarms, load(context))
    }

    private fun scheduleStored(context: Context, alarms: AlarmManager, plan: List<PlannedReminder>) {
        val now = System.currentTimeMillis()
        for (r in plan) {
            val at = triggerMillis(r)
            if (at <= now) continue
            val pi = pendingIntent(context, r, create = true) ?: continue
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Recordatorios", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Avisos para registrar comidas, peso y agua"
            },
        )
    }

    fun notificationsAllowed(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return manager.areNotificationsEnabled()
    }

    fun show(context: Context, intent: Intent) {
        if (!notificationsAllowed(context)) return
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val id = intent.getIntExtra(EXTRA_ID, 0)
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        // Icono pequeño monocromo (androidApp/res/drawable/ic_stat_reminder); si falta, el de la app.
        val small = context.resources.getIdentifier("ic_stat_reminder", "drawable", context.packageName)
            .takeIf { it != 0 } ?: context.applicationInfo.icon
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(small)
            .setContentTitle(intent.getStringExtra(EXTRA_TITLE))
            .setContentText(intent.getStringExtra(EXTRA_BODY))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        manager.notify(id, notification)
    }
}

/** Recibe la alarma y muestra la notificación. Declarado en el manifiesto de `androidApp`. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = AndroidReminders.show(context, intent)
}

/** Reprograma los avisos al arrancar el móvil o actualizar la app. Declarado en el manifiesto de `androidApp`. */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            AndroidReminders.restore(context)
        }
    }
}

private class AndroidReminderScheduler(
    private val context: Context,
    private val launch: () -> Unit,
) : ReminderScheduler {
    /** Respuesta pendiente del diálogo de permiso; la completa el launcher de Compose. */
    var pending: CompletableDeferred<Boolean>? = null

    override val supported: Boolean = true

    override suspend fun hasPermission(): Boolean = AndroidReminders.notificationsAllowed(context)

    override suspend fun requestPermission(): Boolean {
        if (AndroidReminders.notificationsAllowed(context)) return true
        // Antes de Android 13 no hay diálogo: si las notificaciones están desactivadas, solo se activan en Ajustes.
        if (Build.VERSION.SDK_INT < 33) return false
        val result = CompletableDeferred<Boolean>()
        pending = result
        launch()
        return result.await()
    }

    override fun replaceAll(reminders: List<PlannedReminder>) {
        AndroidReminders.ensureChannel(context)
        AndroidReminders.replaceAll(context, reminders)
    }
}

@Composable
actual fun rememberReminderScheduler(): ReminderScheduler {
    val context = LocalContext.current.applicationContext
    val holder = remember { arrayOfNulls<AndroidReminderScheduler>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        holder[0]?.pending?.complete(granted)
        holder[0]?.pending = null
    }
    return remember(context) {
        AndroidReminderScheduler(context) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }.also { holder[0] = it }
    }
}
