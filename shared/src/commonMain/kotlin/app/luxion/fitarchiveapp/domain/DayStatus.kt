package app.luxion.fitarchiveapp.domain

import app.luxion.fitarchiveapp.model.epochDayToIsoDate
import app.luxion.fitarchiveapp.model.isoDateToEpochDay

/** Kcal registradas en un día con alguna entrada (los días sin entradas no tienen resumen). */
data class DaySummary(val date: String, val kcal: Double)

/**
 * Estado de un día frente al objetivo de kcal (docs/design.md §2.12). Fechas ISO (`YYYY-MM-DD`), comparables como texto.
 */
enum class DayStatus {
    /** Hay comida registrada y las kcal quedan dentro del margen del objetivo. */
    COMPLETED,

    /** Día pasado con registro fuera de margen, o sin registro. */
    MISSED,

    /** Hoy, aún sin completar: no cuenta como fallo hasta que acabe el día. */
    TODAY_PENDING,

    FUTURE,

    /** Sin objetivo definido: no se puede evaluar. Día con registro. */
    LOGGED_NO_GOAL,

    /** Sin objetivo definido: no se puede evaluar. Día sin registro. */
    EMPTY_NO_GOAL;

    companion object {
        /** Margen de cumplimiento (±10 % del objetivo): colores, racha y marcas del arco lo usan desde aquí. */
        const val MARGIN = 0.10

        private const val EPSILON = 1e-6

        fun lowerBound(goalKcal: Int): Double = goalKcal * (1 - MARGIN)

        fun upperBound(goalKcal: Int): Double = goalKcal * (1 + MARGIN)

        fun isWithinMargin(kcal: Double, goalKcal: Int): Boolean =
            kcal >= lowerBound(goalKcal) - EPSILON && kcal <= upperBound(goalKcal) + EPSILON

        /** Estado de [date] dado el objetivo vigente y su [summary] (`null` = sin entradas ese día). */
        fun of(date: String, today: String, goalKcal: Int?, summary: DaySummary?): DayStatus = when {
            date > today -> FUTURE
            goalKcal == null -> if (summary != null) LOGGED_NO_GOAL else EMPTY_NO_GOAL
            summary != null && isWithinMargin(summary.kcal, goalKcal) -> COMPLETED
            date == today -> TODAY_PENDING
            else -> MISSED
        }

        /**
         * Días completados consecutivos que terminan hoy. Si hoy aún no está completado se cuenta hasta ayer
         * (la racha no se rompe hasta que acaba el día). Sin objetivo, 0.
         * [bonusKcal] son las kcal que el ejercicio suma al objetivo de cada día (docs/design.md §2.14).
         */
        fun streak(
            today: String,
            goalKcal: Int?,
            summaries: Map<String, DaySummary>,
            bonusKcal: Map<String, Int> = emptyMap(),
        ): Int {
            if (goalKcal == null) return 0
            fun completed(date: String) =
                of(date, today, goalKcal + (bonusKcal[date] ?: 0), summaries[date]) == COMPLETED
            var day = isoDateToEpochDay(today) ?: return 0
            if (!completed(today)) day--
            var count = 0
            // Una racha no puede ser más larga que el número de días con registro.
            while (count <= summaries.size && completed(epochDayToIsoDate(day))) {
                count++
                day--
            }
            return count
        }
    }
}
