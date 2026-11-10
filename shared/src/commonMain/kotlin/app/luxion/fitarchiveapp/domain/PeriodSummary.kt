package app.luxion.fitarchiveapp.domain

import app.luxion.fitarchiveapp.model.DiaryEntry
import app.luxion.fitarchiveapp.model.WeightEntry
import app.luxion.fitarchiveapp.model.WeightRules
import app.luxion.fitarchiveapp.model.epochDayToIsoDate
import app.luxion.fitarchiveapp.model.isoDateToEpochDay
import kotlin.math.abs

/** Periodo del resumen (docs/design.md §2.19): semana de lunes a domingo o mes natural. */
enum class SummaryPeriod { WEEK, MONTH }

/** Intervalo de fechas ISO del periodo, ambas incluidas. */
data class PeriodRange(val period: SummaryPeriod, val start: String, val end: String) {
    val dayCount: Int get() = (isoDateToEpochDay(end)!! - isoDateToEpochDay(start)!! + 1).toInt()

    fun contains(date: String): Boolean = date in start..end

    /** Mismo tipo de periodo, [delta] periodos después (negativo = antes). */
    fun shifted(delta: Int): PeriodRange = of(period, when (period) {
        SummaryPeriod.WEEK -> epochDayToIsoDate(isoDateToEpochDay(start)!! + 7L * delta)
        SummaryPeriod.MONTH -> monthStartPlus(start, delta)
    })

    companion object {
        /** Periodo que contiene [date]. */
        fun of(period: SummaryPeriod, date: String): PeriodRange {
            val day = isoDateToEpochDay(date) ?: error("Fecha no válida: $date")
            return when (period) {
                SummaryPeriod.WEEK -> {
                    val monday = day - (day + 3).mod(7L)
                    PeriodRange(period, epochDayToIsoDate(monday), epochDayToIsoDate(monday + 6))
                }
                SummaryPeriod.MONTH -> {
                    val first = date.substring(0, 8) + "01"
                    val nextFirst = isoDateToEpochDay(monthStartPlus(first, 1))!!
                    PeriodRange(period, first, epochDayToIsoDate(nextFirst - 1))
                }
            }
        }

        private fun monthStartPlus(firstOfMonth: String, months: Int): String {
            val index = firstOfMonth.substring(0, 4).toInt() * 12 + (firstOfMonth.substring(5, 7).toInt() - 1) + months
            val year = index.floorDiv(12)
            val month = index.mod(12) + 1
            return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-01"
        }
    }
}

/** Un día del periodo con sus totales (`null` = sin entradas ese día) y su estado frente al objetivo. */
data class SummaryDay(
    val date: String,
    val status: DayStatus,
    val totals: DayTotals?,
)

data class DayTotals(val kcal: Double, val proteinG: Double, val carbsG: Double, val fatG: Double)

/** Media diaria de kcal y macros sobre los días con registro. */
data class DailyAverages(val kcal: Double, val proteinG: Double, val carbsG: Double, val fatG: Double)

/**
 * Cambio de peso dentro del periodo (entre el primer y el último pesaje) junto a las kcal medias del mismo periodo.
 * [kcalVsGoal] es la media menos el objetivo (negativo = por debajo); `null` sin objetivo.
 */
data class WeightVsCalories(
    val first: WeightEntry,
    val last: WeightEntry,
    val deltaKg: Double,
    val avgKcal: Double,
    val kcalVsGoal: Double?,
)

/** Resumen de un periodo, derivado del diario, los objetivos y los pesajes (sin tabla propia). */
data class PeriodSummary(
    val range: PeriodRange,
    val today: String,
    val goalKcal: Int?,
    /** Todos los días del periodo, en orden. */
    val days: List<SummaryDay>,
    val loggedDays: Int,
    /** `null` si no hay ningún día con registro. */
    val averages: DailyAverages?,
    /** Días completados (objetivo ±10 %); `null` sin objetivo. */
    val completedDays: Int?,
    /** Días que ya se pueden juzgar (cerrados, o completados hoy). */
    val judgedDays: Int,
    /** Racha más larga de días completados seguidos dentro del periodo. */
    val bestStreak: Int,
    /** Día registrado más cercano al objetivo; `null` sin objetivo o sin días juzgables. */
    val bestDay: SummaryDay?,
    /** Día registrado más lejos del objetivo; `null` si solo hay un día juzgable. */
    val worstDay: SummaryDay?,
    /** `null` con menos de dos pesajes en el periodo o sin días con registro. */
    val weight: WeightVsCalories?,
    /** Pesajes del periodo, por fecha (para la gráfica). */
    val weights: List<WeightEntry>,
) {
    val isEmpty: Boolean get() = loggedDays == 0
    val isCurrent: Boolean get() = range.contains(today)
}

object PeriodSummaries {
    /**
     * Construye el resumen de [range]. Las kcal y macros de una entrada sin dato (o en porciones sin tamaño) cuentan 0,
     * igual que `DiaryStore.daySummaries`. Las medias se calculan sobre los días con registro; los días futuros no cuentan.
     */
    fun build(
        range: PeriodRange,
        today: String,
        goalKcal: Int?,
        entries: List<DiaryEntry>,
        weights: List<WeightEntry>,
    ): PeriodSummary {
        val byDate = entries.filter { range.contains(it.date) }.groupBy { it.date }.mapValues { (_, list) ->
            list.map { it.totals }.let { totals ->
                DayTotals(
                    kcal = totals.sumOf { it.kcal ?: 0.0 },
                    proteinG = totals.sumOf { it.proteinG ?: 0.0 },
                    carbsG = totals.sumOf { it.carbsG ?: 0.0 },
                    fatG = totals.sumOf { it.fatG ?: 0.0 },
                )
            }
        }
        val firstDay = isoDateToEpochDay(range.start)!!
        val days = (0 until range.dayCount).map { i ->
            val date = epochDayToIsoDate(firstDay + i)
            val totals = byDate[date]
            SummaryDay(date, DayStatus.of(date, today, goalKcal, totals?.let { DaySummary(date, it.kcal) }), totals)
        }
        val logged = days.filter { it.totals != null && it.date <= today }
        val averages = if (logged.isEmpty()) null else DailyAverages(
            kcal = logged.sumOf { it.totals!!.kcal } / logged.size,
            proteinG = logged.sumOf { it.totals!!.proteinG } / logged.size,
            carbsG = logged.sumOf { it.totals!!.carbsG } / logged.size,
            fatG = logged.sumOf { it.totals!!.fatG } / logged.size,
        )

        val completed = days.count { it.status == DayStatus.COMPLETED }
        val judged = days.count { it.status == DayStatus.COMPLETED || it.status == DayStatus.MISSED }

        var best = 0
        var run = 0
        for (day in days) {
            run = if (day.status == DayStatus.COMPLETED) run + 1 else 0
            if (run > best) best = run
        }

        // Mejor y peor día: solo días con registro ya cerrados (o completados hoy), por distancia al objetivo.
        val ranked = if (goalKcal == null) emptyList() else
            logged.filter { it.status == DayStatus.COMPLETED || it.status == DayStatus.MISSED }
                .sortedWith(compareBy({ abs(it.totals!!.kcal - goalKcal) }, { it.date }))
        val bestDay = ranked.firstOrNull()
        val worstDay = ranked.takeIf { it.size > 1 }?.last()

        val weightsIn = weights.filter { range.contains(it.date) && it.date <= today }.sortedBy { it.date }
        val weightTrend = if (weightsIn.size >= 2 && averages != null) {
            val first = weightsIn.first()
            val last = weightsIn.last()
            WeightVsCalories(
                first = first,
                last = last,
                deltaKg = WeightRules.round(last.weightKg - first.weightKg),
                avgKcal = averages.kcal,
                kcalVsGoal = goalKcal?.let { averages.kcal - it },
            )
        } else null

        return PeriodSummary(
            range = range,
            today = today,
            goalKcal = goalKcal,
            days = days,
            loggedDays = logged.size,
            averages = averages,
            completedDays = if (goalKcal == null) null else completed,
            judgedDays = judged,
            bestStreak = best,
            bestDay = bestDay,
            worstDay = worstDay,
            weight = weightTrend,
            weights = weightsIn,
        )
    }
}
