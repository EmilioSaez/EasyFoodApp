package app.luxion.fitarchiveapp.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import app.luxion.fitarchiveapp.android.MainActivity
import app.luxion.fitarchiveapp.android.R
import app.luxion.fitarchiveapp.domain.DayWidgetSnapshot
import app.luxion.fitarchiveapp.domain.MacroProgress
import java.util.Locale
import kotlin.math.abs

/**
 * Widget de inicio: kcal que quedan hoy, macros y agua, con «+ Vaso» y «Entrada rápida» que abren la app en esa
 * pantalla. Se redibuja al salir la app de primer plano ([requestRefresh] desde `MainActivity`), al cambiar de
 * día/hora/zona y cada 30 minutos como red de seguridad. Usa RemoteViews (sin librerías extra).
 */
class DailyWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) = refreshAsync(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> refreshAsync(context)
        }
    }

    private fun refreshAsync(context: Context) {
        val pending = goAsync()
        Thread {
            try {
                refresh(context)
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        /** Redibuja todos los widgets colocados. Bloquea (lee la base): llamar fuera del hilo principal. */
        fun refresh(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, DailyWidget::class.java))
            if (ids.isEmpty()) return
            manager.updateAppWidget(ids, render(app, WidgetData.load(app)))
        }

        /** Pide el redibujado desde cualquier hilo (no espera). */
        fun requestRefresh(context: Context) {
            val app = context.applicationContext
            if (AppWidgetManager.getInstance(app).getAppWidgetIds(ComponentName(app, DailyWidget::class.java)).isEmpty()) return
            Thread { runCatching { refresh(app) } }.start()
        }

        private fun render(context: Context, snapshot: DayWidgetSnapshot?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_daily)
            views.setOnClickPendingIntent(R.id.widget_root, open(context, MainActivity.DEST_DIARY, 0))
            views.setOnClickPendingIntent(R.id.widget_water, open(context, MainActivity.DEST_WATER, 1))
            views.setOnClickPendingIntent(R.id.widget_quick, open(context, MainActivity.DEST_QUICK, 2))
            if (snapshot == null) {
                views.setTextViewText(R.id.widget_value, "—")
                views.setTextViewText(R.id.widget_label, context.getString(R.string.widget_open_app))
                views.setViewVisibility(R.id.widget_macros, View.GONE)
                views.setViewVisibility(R.id.widget_water_line, View.GONE)
                return views
            }
            val remaining = snapshot.remainingKcal
            views.setTextViewText(R.id.widget_value, number(remaining?.let { abs(it) } ?: snapshot.eatenKcal))
            views.setTextViewText(
                R.id.widget_label,
                context.getString(
                    when {
                        remaining == null -> R.string.widget_kcal_eaten
                        remaining >= 0 -> R.string.widget_kcal_left
                        else -> R.string.widget_kcal_over
                    },
                ),
            )
            views.setViewVisibility(R.id.widget_macros, View.VISIBLE)
            views.setTextViewText(R.id.widget_macros, "P ${macro(snapshot.protein)} · H ${macro(snapshot.carbs)} · G ${macro(snapshot.fat)}")
            views.setViewVisibility(R.id.widget_water_line, View.VISIBLE)
            views.setTextViewText(
                R.id.widget_water_line,
                "${context.getString(R.string.widget_water)} ${liters(snapshot.waterMl)} / ${liters(snapshot.waterGoalMl)} L",
            )
            return views
        }

        private fun open(context: Context, destination: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .putExtra(MainActivity.EXTRA_DESTINATION, destination)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun number(n: Int) = String.format(Locale.getDefault(), "%,d", n)

        private fun macro(m: MacroProgress) = if (m.goalG != null) "${m.eatenG}/${m.goalG}" else "${m.eatenG}"

        private fun liters(ml: Int) = String.format(Locale.getDefault(), "%.1f", ml / 1000.0)
    }
}
