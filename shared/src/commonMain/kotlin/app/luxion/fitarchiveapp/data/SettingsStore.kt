package app.luxion.fitarchiveapp.data

/** Ajustes del usuario (la implementación real es [SqlDelightSettingsStore]). */
interface SettingsStore {
    /** Comidas al día (3 a 6), o `null` si aún no se ha elegido (primera vez). */
    suspend fun mealsPerDay(): Int?

    /** Guarda las comidas al día; lanza [IllegalArgumentException] si no están entre 3 y 6. */
    suspend fun setMealsPerDay(count: Int)
}
