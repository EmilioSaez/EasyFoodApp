package app.luxion.fitarchiveapp.domain

import app.luxion.fitarchiveapp.model.WeightEntry
import app.luxion.fitarchiveapp.model.WeightRules
import app.luxion.fitarchiveapp.model.isoDateToEpochDay
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** Rango temporal de la gráfica de peso. [days] `null` = todo el historial. */
enum class WeightRange(val label: String, val days: Int?) {
    DAYS_30("30 días", 30),
    DAYS_90("90 días", 90),
    ALL("Todo", null),
}

/** Resumen de un tramo: primer y último registro y la diferencia entre ambos. */
data class WeightSummary(val first: WeightEntry, val latest: WeightEntry) {
    /** Cambio desde el primer registro del tramo (negativo = has bajado); redondeado a 0,1 kg. */
    val deltaKg: Double get() = WeightRules.round(latest.weightKg - first.weightKg)
    val hasChange: Boolean get() = first.date != latest.date
}

/** Punto de la gráfica en coordenadas normalizadas: `x` 0..1 de izquierda a derecha, `y` 0..1 de abajo arriba. */
data class WeightChartPoint(val x: Float, val y: Float, val entry: WeightEntry)

data class WeightChartModel(
    val points: List<WeightChartPoint>,
    val yMin: Double,
    val yMax: Double,
    /** Valores de las marcas del eje Y, de menor a mayor (incluyen [yMin] y [yMax]). */
    val ticks: List<Double>,
) {
    /** Fracción vertical (0..1, abajo arriba) de [kg]. */
    fun yFraction(kg: Double): Float = ((kg - yMin) / (yMax - yMin)).toFloat()
}

object WeightProgress {
    /** Máximo de puntos con marcador; por encima solo se dibuja la línea para que siga siendo legible. */
    const val MAX_MARKED_POINTS = 45

    /** Registros de [range] terminados en [today] (inclusive), ordenados por fecha. */
    fun inRange(entries: List<WeightEntry>, range: WeightRange, today: String): List<WeightEntry> {
        val sorted = entries.sortedBy { it.date }
        val days = range.days ?: return sorted
        val todayDay = isoDateToEpochDay(today) ?: return sorted
        val from = todayDay - days + 1
        return sorted.filter { e -> isoDateToEpochDay(e.date)?.let { it in from..todayDay } == true }
    }

    fun summary(visible: List<WeightEntry>): WeightSummary? {
        if (visible.isEmpty()) return null
        val sorted = visible.sortedBy { it.date }
        return WeightSummary(sorted.first(), sorted.last())
    }

    /** Geometría de la gráfica de línea; `null` si no hay registros. */
    fun chart(visible: List<WeightEntry>): WeightChartModel? {
        val sorted = visible.sortedBy { it.date }.filter { isoDateToEpochDay(it.date) != null }
        if (sorted.isEmpty()) return null
        val (yMin, yMax, ticks) = axis(sorted.minOf { it.weightKg }, sorted.maxOf { it.weightKg })
        val firstDay = isoDateToEpochDay(sorted.first().date)!!
        val lastDay = isoDateToEpochDay(sorted.last().date)!!
        val spanDays = (lastDay - firstDay).toFloat()
        val points = sorted.map {
            val x = if (spanDays == 0f) 0.5f else (isoDateToEpochDay(it.date)!! - firstDay) / spanDays
            WeightChartPoint(x, ((it.weightKg - yMin) / (yMax - yMin)).toFloat(), it)
        }
        return WeightChartModel(points, yMin, yMax, ticks)
    }

    private data class Axis(val min: Double, val max: Double, val ticks: List<Double>)

    private val STEPS = listOf(0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0)

    /** Eje Y "bonito": límites múltiplos del paso, con 3-6 marcas y algo de aire alrededor de los datos. */
    private fun axis(minKg: Double, maxKg: Double): Axis {
        val span = maxKg - minKg
        val step = STEPS.firstOrNull { (maxOf(span, 1.0) + it) / it <= 5.5 } ?: STEPS.last()
        var lo = floor(minKg / step) * step
        var hi = ceil(maxKg / step) * step
        // Que los datos no toquen el borde.
        if (abs(minKg - lo) < step * 0.15) lo -= step
        if (abs(hi - maxKg) < step * 0.15) hi += step
        if (hi - lo < step) hi = lo + step
        val ticks = generateSequence(lo) { it + step }.takeWhile { it <= hi + 1e-9 }.map { WeightRules.round(it) }.toList()
        return Axis(lo, hi, ticks)
    }
}
