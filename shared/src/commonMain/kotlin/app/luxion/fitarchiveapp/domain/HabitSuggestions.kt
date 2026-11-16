package app.luxion.fitarchiveapp.domain

import app.luxion.fitarchiveapp.model.DiaryEntry
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.NewDiaryEntry
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.isoDateToEpochDay

/** Alimento que sueles apuntar en una comida, con la cantidad habitual y cuántas veces lo has hecho. */
data class HabitSuggestion(
    val barcode: String?,
    val name: String,
    val quantity: Quantity,
    val servingSizeG: Double?,
    val nutrients: Nutrients,
    /** Veces que aparece en esa comida dentro de la ventana de historial. */
    val count: Int,
    /** Cuántas de esas veces fue en el mismo día de la semana que el día de destino. */
    val sameWeekdayCount: Int,
) {
    fun toNewEntry(date: String, meal: MealType) = NewDiaryEntry(date, meal, barcode, name, quantity, servingSizeG, nutrients)
}

/**
 * Sugerencias por hora y hábito (docs/design.md §2.28): qué sueles comer en cada comida (la comida fija la hora) y qué día
 * de la semana, calculado solo con el historial local del diario, sin tabla nueva ni IA.
 */
object HabitSuggestions {
    /** Días de historial que se miran hacia atrás desde el día de destino. */
    const val WINDOW_DAYS = 60

    /** Veces mínimas que un alimento debe aparecer en la comida para considerarse un hábito. */
    const val MIN_OCCURRENCES = 2

    const val DEFAULT_LIMIT = 5

    /**
     * Hasta [limit] alimentos habituales de [meal] para el día [date], del más al menos probable.
     * [history] son entradas de cualquier día; solo cuentan las de [meal] de los [WINDOW_DAYS] días anteriores a [date]
     * (las entradas rápidas no son un alimento y se ignoran). Cada aparición pesa 1, y 2 si cae en el mismo día de la
     * semana que [date]; empata la más reciente y luego el nombre. Se omiten los alimentos que [date] ya tiene en [meal].
     * La cantidad propuesta es la más repetida (con empate, la más reciente).
     */
    fun suggest(
        history: List<DiaryEntry>,
        meal: MealType,
        date: String,
        limit: Int = DEFAULT_LIMIT,
    ): List<HabitSuggestion> {
        val target = isoDateToEpochDay(date) ?: return emptyList()
        val already = history.filter { it.date == date && it.meal == meal && !it.quick }.mapTo(mutableSetOf()) { foodKey(it) }
        val candidates = history.filter { e ->
            if (e.quick || e.meal != meal) return@filter false
            val day = isoDateToEpochDay(e.date) ?: return@filter false
            day < target && target - day <= WINDOW_DAYS
        }
        return candidates.groupBy { foodKey(it) }
            .filterKeys { it !in already }
            .values
            .filter { it.size >= MIN_OCCURRENCES }
            .map { rows ->
                val latest = rows.maxWith(compareBy<DiaryEntry>({ it.date }, { it.createdAtMillis }))
                val sameWeekday = rows.count { weekday(it.date) == weekday(date) }
                val usual = rows.groupBy { it.quantity }.values
                    .maxWith(compareBy<List<DiaryEntry>>({ it.size }, { g -> g.maxOf { it.date } }))
                    .maxWith(compareBy<DiaryEntry>({ it.date }, { it.createdAtMillis }))
                Triple(
                    HabitSuggestion(latest.barcode, latest.name, usual.quantity, usual.servingSizeG, latest.nutrients, rows.size, sameWeekday),
                    rows.size + sameWeekday,
                    latest.date,
                )
            }
            .sortedWith(compareByDescending<Triple<HabitSuggestion, Int, String>> { it.second }.thenByDescending { it.third }.thenBy { it.first.name })
            .take(limit)
            .map { it.first }
    }

    /** Clave del alimento: su código (o clave propia/genérica) y, sin él, el nombre sin distinguir mayúsculas. */
    private fun foodKey(e: DiaryEntry): String = e.barcode ?: ("name:" + e.name.trim().lowercase())

    /** Día de la semana, 0 = lunes. */
    private fun weekday(date: String): Int = ((isoDateToEpochDay(date) ?: 0L) + 3).mod(7L).toInt()
}
