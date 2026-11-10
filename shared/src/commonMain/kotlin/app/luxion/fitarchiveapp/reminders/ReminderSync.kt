package app.luxion.fitarchiveapp.reminders

import app.luxion.fitarchiveapp.data.DiaryStore
import app.luxion.fitarchiveapp.data.SettingsStore
import app.luxion.fitarchiveapp.data.WaterStore
import app.luxion.fitarchiveapp.data.WeightStore
import app.luxion.fitarchiveapp.domain.ReminderContext
import app.luxion.fitarchiveapp.domain.ReminderPlanner
import app.luxion.fitarchiveapp.domain.ReminderSettings

/**
 * Pone al día lo programado en el sistema: lee ajustes y registros, calcula el plan y lo entrega a [scheduler].
 * Se llama al arrancar y tras cada cambio de pantalla o de ajustes, para que un aviso ya cumplido (comida o peso
 * registrados) desaparezca.
 */
class ReminderSync(
    private val settings: SettingsStore,
    private val diary: DiaryStore,
    private val weights: WeightStore,
    private val water: WaterStore?,
    private val scheduler: ReminderScheduler,
) {
    /** Devuelve si los recordatorios están activados en los ajustes. */
    suspend fun sync(today: String, nowMinutes: Int): Boolean {
        val config = settings.reminders() ?: ReminderSettings()
        if (!config.enabled || !scheduler.supported || !scheduler.hasPermission()) {
            scheduler.replaceAll(emptyList())
            return config.enabled
        }
        val ctx = ReminderContext(
            today = today,
            nowMinutes = nowMinutes,
            mealsPerDay = settings.mealsPerDay(),
            loggedMealsToday = diary.entriesForDate(today).map { it.meal }.toSet(),
            weights = weights.all(),
            waterTodayMl = water?.total(today) ?: 0,
            waterGoalMl = water?.goalMl() ?: Int.MAX_VALUE,
        )
        scheduler.replaceAll(ReminderPlanner.plan(config, ctx))
        return true
    }
}
