package app.luxion.fitarchiveapp.domain

import app.luxion.fitarchiveapp.model.MealType
import kotlin.math.abs

/** Comidas al día que hace el usuario y cómo se reparten (docs/design.md §2.9). */
object MealPlan {
    const val MIN_MEALS = 3
    const val MAX_MEALS = 6
    const val DEFAULT_MEALS = 4

    val options: IntRange = MIN_MEALS..MAX_MEALS

    /** Comidas del preset de [count] comidas al día, en orden de aparición; `null` o fuera de rango usa el de por defecto. */
    fun preset(count: Int?): List<MealType> = when (count) {
        3 -> listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)
        5 -> listOf(MealType.BREAKFAST, MealType.MID_MORNING, MealType.LUNCH, MealType.SNACK, MealType.DINNER)
        6 -> MealType.entries
        else -> listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.SNACK, MealType.DINNER)
    }

    fun isValid(count: Int): Boolean = count in options

    /**
     * Secciones que enseña el Diario: las del preset más las comidas que ya no están en él pero tienen entradas
     * ([withEntries]), para no ocultar datos. Siempre en orden de aparición en el día.
     */
    fun visible(count: Int?, withEntries: Set<MealType>): List<MealType> {
        val active = preset(count).toSet()
        return MealType.entries.filter { it in active || it in withEntries }
    }

    /** Hora típica de cada comida, en minutos desde medianoche (solo para elegir la más cercana a una hora). */
    private fun typicalMinute(meal: MealType): Int = when (meal) {
        MealType.BREAKFAST -> 8 * 60 + 30
        MealType.MID_MORNING -> 11 * 60
        MealType.LUNCH -> 14 * 60
        MealType.SNACK -> 17 * 60 + 30
        MealType.DINNER -> 21 * 60
        MealType.LATE_SNACK -> 23 * 60 + 30
    }

    /** La comida de [active] más cercana a [minuteOfDay] (0 a 1439); con empate, la primera del día. */
    fun nearest(active: List<MealType>, minuteOfDay: Int): MealType {
        require(active.isNotEmpty()) { "Debe haber alguna comida activa" }
        fun distance(meal: MealType): Int {
            val d = abs(typicalMinute(meal) - minuteOfDay)
            return minOf(d, 24 * 60 - d)
        }
        return active.minBy { distance(it) }
    }
}
