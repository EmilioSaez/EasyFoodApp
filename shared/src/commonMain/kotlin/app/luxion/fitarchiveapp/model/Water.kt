package app.luxion.fitarchiveapp.model

/** Reglas del registro de agua (docs/design.md §2.13). Todo en ml. */
object WaterRules {
    const val DEFAULT_GOAL_ML = 2000
    const val MIN_GOAL_ML = 500
    const val MAX_GOAL_ML = 10_000

    /** Tope de un día: evita totales absurdos por pulsaciones repetidas. */
    const val MAX_DAY_ML = 20_000

    const val GLASS_ML = 250
    const val BOTTLE_ML = 500
    const val LITER_ML = 1000

    fun isValidGoal(ml: Int) = ml in MIN_GOAL_ML..MAX_GOAL_ML

    /** Fracción del objetivo cubierta (puede superar 1). */
    fun progress(ml: Int, goalMl: Int): Float = if (goalMl <= 0) 0f else ml.toFloat() / goalMl
}

/** Total de agua de un día. */
data class WaterDay(val date: String, val ml: Int, val updatedAtMillis: Long)
