package app.luxion.fitarchiveapp.data

import app.luxion.fitarchiveapp.db.SettingsQueries
import app.luxion.fitarchiveapp.domain.MealPlan
import app.luxion.fitarchiveapp.domain.ReminderSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [SettingsStore] respaldado por la tabla `SettingsEntity` de SQLDelight. */
class SqlDelightSettingsStore(
    private val queries: SettingsQueries,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SettingsStore {
    override suspend fun mealsPerDay(): Int? = withContext(dispatcher) {
        queries.selectMealsPerDay().executeAsOneOrNull()?.meals_per_day?.toInt()
    }

    override suspend fun setMealsPerDay(count: Int) = withContext(dispatcher) {
        require(MealPlan.isValid(count)) {
            "Las comidas al día deben estar entre ${MealPlan.MIN_MEALS} y ${MealPlan.MAX_MEALS}"
        }
        queries.upsertMealsPerDay(count.toLong())
        Unit
    }

    override suspend fun reminders(): ReminderSettings? = withContext(dispatcher) {
        ReminderSettings.fromJson(queries.selectReminders().executeAsOneOrNull()?.reminders)
    }

    override suspend fun setReminders(settings: ReminderSettings) = withContext(dispatcher) {
        queries.upsertReminders(settings.sanitized().toJson())
        Unit
    }
}
