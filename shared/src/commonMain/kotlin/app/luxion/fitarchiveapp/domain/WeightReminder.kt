package app.luxion.fitarchiveapp.domain

import app.luxion.fitarchiveapp.model.WeightEntry
import app.luxion.fitarchiveapp.model.isoDateToEpochDay

/** Aviso semanal de pesaje: se deriva de los registros, sin guardar nada más. */
object WeightReminder {
    /** Días desde el último registro a partir de los cuales toca pesarse. */
    const val INTERVAL_DAYS = 7

    /**
     * `true` si no hay registros o el más reciente tiene [INTERVAL_DAYS] días o más respecto a [today]
     * (fecha ISO). Un último registro futuro no avisa; con [today] inválida tampoco.
     */
    fun isDue(entries: List<WeightEntry>, today: String): Boolean {
        val todayDay = isoDateToEpochDay(today) ?: return false
        val lastDay = entries.mapNotNull { isoDateToEpochDay(it.date) }.maxOrNull() ?: return true
        return todayDay - lastDay >= INTERVAL_DAYS
    }

    /** Días desde el último registro, o `null` si no hay ninguno (o [today] es inválida). */
    fun daysSinceLast(entries: List<WeightEntry>, today: String): Long? {
        val todayDay = isoDateToEpochDay(today) ?: return null
        val lastDay = entries.mapNotNull { isoDateToEpochDay(it.date) }.maxOrNull() ?: return null
        return todayDay - lastDay
    }
}
