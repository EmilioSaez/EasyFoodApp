package app.luxion.fitarchiveapp.data

import app.luxion.fitarchiveapp.domain.ReminderSettings

/** Ajustes del usuario (la implementación real es [SqlDelightSettingsStore]). */
interface SettingsStore {
    /** Comidas al día (3 a 6), o `null` si aún no se ha elegido (primera vez). */
    suspend fun mealsPerDay(): Int?

    /** Guarda las comidas al día; lanza [IllegalArgumentException] si no están entre 3 y 6. */
    suspend fun setMealsPerDay(count: Int)

    /** Recordatorios locales, o `null` si nunca se han configurado (todo apagado). */
    suspend fun reminders(): ReminderSettings? = null

    /** Guarda los recordatorios (ya saneados). */
    suspend fun setReminders(settings: ReminderSettings) {}
}
