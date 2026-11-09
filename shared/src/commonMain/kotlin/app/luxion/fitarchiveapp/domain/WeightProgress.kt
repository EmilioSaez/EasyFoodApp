package app.luxion.fitarchiveapp.domain

import app.luxion.fitarchiveapp.model.WeightEntry
import app.luxion.fitarchiveapp.model.WeightRules
import app.luxion.fitarchiveapp.model.epochDayToIsoDate
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

/** Qué se sabe de cuándo se llegará al objetivo según la tendencia de los pesajes. */
sealed interface WeightProjection {
    /** Pocos registros recientes (hacen falta [WeightProgress.MIN_TREND_ENTRIES] en [WeightProgress.TREND_WINDOW_DAYS] días, repartidos en una semana o más). */
    data object NotEnoughData : WeightProjection

    /** La tendencia es plana o va en sentido contrario al objetivo. */
    data object MovingAway : WeightProjection

    /** Se acerca, pero tan despacio que superaría [WeightProgress.MAX_PROJECTION_DAYS] días. */
    data object TooSlow : WeightProjection

    /** Llegada estimada: [date] ISO, a [daysLeft] días de hoy, con una tendencia de [kgPerWeek] (con signo). */
    data class Estimated(val date: String, val daysLeft: Int, val kgPerWeek: Double) : WeightProjection
}

/** Distancia al objetivo; con [projection] solo si aún no se ha alcanzado. */
data class WeightGoalProgress(
    val targetKg: Double,
    val currentKg: Double,
    /** Kg que faltan (siempre ≥ 0, a 0,1 kg); 0 si se alcanzó. */
    val remainingKg: Double,
    /** Avance 0..100 desde el primer registro hasta el objetivo; 100 solo si se alcanzó. */
    val percent: Int,
    val reached: Boolean,
    val projection: WeightProjection?,
)

object WeightProgress {
    const val TREND_WINDOW_DAYS = 28
    const val MIN_TREND_ENTRIES = 3
    const val MIN_TREND_SPAN_DAYS = 7
    const val MAX_PROJECTION_DAYS = 730

    /** Por debajo de esta pendiente (kg/día) se considera que el peso no se mueve. */
    private const val MIN_SLOPE_KG_PER_DAY = 0.01

    /**
     * Distancia al [targetKg] desde el último registro y estimación de cuándo se llegará según la tendencia
     * (regresión lineal de los pesajes de los últimos [TREND_WINDOW_DAYS] días). `null` si no hay objetivo
     * o no hay ningún registro. El porcentaje parte del primer registro del historial.
     */
    fun goalProgress(entries: List<WeightEntry>, targetKg: Double?, today: String): WeightGoalProgress? {
        if (targetKg == null) return null
        val sorted = entries.sortedBy { it.date }.filter { isoDateToEpochDay(it.date) != null }
        val first = sorted.firstOrNull() ?: return null
        val current = sorted.last()
        val remaining = WeightRules.round(abs(current.weightKg - targetKg))
        val crossed = when {
            first.weightKg > targetKg -> current.weightKg <= targetKg
            first.weightKg < targetKg -> current.weightKg >= targetKg
            else -> false // empezó en el objetivo: solo cuenta estar en él
        }
        val reached = remaining == 0.0 || crossed
        val span = first.weightKg - targetKg
        val percent = when {
            reached -> 100
            span == 0.0 -> 0
            else -> floor((first.weightKg - current.weightKg) / span * 100).toInt().coerceIn(0, 99)
        }
        return WeightGoalProgress(
            targetKg = targetKg,
            currentKg = current.weightKg,
            remainingKg = if (reached) 0.0 else remaining,
            percent = percent,
            reached = reached,
            projection = if (reached) null else project(sorted, targetKg, today),
        )
    }

    private fun project(sorted: List<WeightEntry>, targetKg: Double, today: String): WeightProjection {
        val lastDay = isoDateToEpochDay(sorted.last().date)!!
        val recent = sorted.filter { isoDateToEpochDay(it.date)!! > lastDay - TREND_WINDOW_DAYS }
        val xs = recent.map { isoDateToEpochDay(it.date)!!.toDouble() }
        if (recent.size < MIN_TREND_ENTRIES || xs.max() - xs.min() < MIN_TREND_SPAN_DAYS) return WeightProjection.NotEnoughData
        val ys = recent.map { it.weightKg }
        val mx = xs.average()
        val my = ys.average()
        val slope = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / xs.sumOf { (it - mx) * (it - mx) }
        val needed = targetKg - sorted.last().weightKg
        // Hacia el objetivo = misma dirección que lo que falta, y con pendiente apreciable.
        if (slope * needed <= 0 || abs(slope) < MIN_SLOPE_KG_PER_DAY) return WeightProjection.MovingAway
        val daysFromLast = ceil(needed / slope).toLong()
        if (daysFromLast > MAX_PROJECTION_DAYS) return WeightProjection.TooSlow
        val todayDay = isoDateToEpochDay(today) ?: lastDay
        val targetDay = maxOf(lastDay + daysFromLast, todayDay)
        return WeightProjection.Estimated(epochDayToIsoDate(targetDay), (targetDay - todayDay).toInt(), WeightRules.round(slope * 7))
    }

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
