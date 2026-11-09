package app.luxion.fitarchiveapp.domain

import app.luxion.fitarchiveapp.model.NewWeightEntry
import app.luxion.fitarchiveapp.model.WeightEntry
import app.luxion.fitarchiveapp.model.WeightParse
import app.luxion.fitarchiveapp.model.WeightRules
import app.luxion.fitarchiveapp.model.epochDayToIsoDate
import app.luxion.fitarchiveapp.model.isoDateToEpochDay
import app.luxion.fitarchiveapp.ui.screens.formatDayMonth
import app.luxion.fitarchiveapp.ui.screens.formatDayMonthYear
import app.luxion.fitarchiveapp.ui.screens.formatKg
import app.luxion.fitarchiveapp.ui.screens.formatKgDelta
import app.luxion.fitarchiveapp.ui.screens.goalSummaryText
import app.luxion.fitarchiveapp.ui.screens.projectionText
import app.luxion.fitarchiveapp.ui.screens.weightDateError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeightProgressTest {
    private fun w(date: String, kg: Double) = WeightEntry(date, kg)

    // --- Fechas ---

    @Test
    fun epochDayRoundTripsAndValidatesCalendar() {
        assertEquals(0L, isoDateToEpochDay("1970-01-01"))
        assertEquals(19_723L, isoDateToEpochDay("2024-01-01"))
        for (d in listOf("2024-02-29", "2026-11-05", "1999-12-31", "2000-03-01")) {
            assertEquals(d, epochDayToIsoDate(isoDateToEpochDay(d)!!))
        }
        assertNull(isoDateToEpochDay("2026-02-29"))
        assertNull(isoDateToEpochDay("2026-13-01"))
        assertNull(isoDateToEpochDay("2026-04-31"))
        assertNull(isoDateToEpochDay("2026-4-1"))
        assertNull(isoDateToEpochDay("hoy"))
        assertNull(isoDateToEpochDay("2026/11/05"))
        assertNull(isoDateToEpochDay("+026-11-05"))
    }

    // --- Validación ---

    @Test
    fun parsesCommaAndDotDecimals() {
        assertEquals(WeightParse.Valid(78.4), WeightRules.parse("78,4"))
        assertEquals(WeightParse.Valid(78.4), WeightRules.parse("78.4"))
        assertEquals(WeightParse.Valid(80.0), WeightRules.parse(" 80 "))
        assertEquals(WeightParse.Valid(78.4), WeightRules.parse("78,36"))
    }

    @Test
    fun rejectsEmptyNonNumericAndOutOfRange() {
        for (bad in listOf("", "  ", "abc", "-5", "19,9", "500,1", "1e400", "NaN", "78,4,1")) {
            assertTrue(WeightRules.parse(bad) is WeightParse.Invalid, "debería ser inválido: '$bad'")
        }
        assertEquals(WeightParse.Valid(20.0), WeightRules.parse("20"))
        assertEquals(WeightParse.Valid(500.0), WeightRules.parse("500"))
    }

    @Test
    fun newWeightEntryValidates() {
        NewWeightEntry("2026-11-05", 75.0)
        assertFailsWith<IllegalArgumentException> { NewWeightEntry("2026-11-05", 19.0) }
        assertFailsWith<IllegalArgumentException> { NewWeightEntry("2026-11-05", Double.NaN) }
        assertFailsWith<IllegalArgumentException> { NewWeightEntry("2026-02-30", 75.0) }
    }

    @Test
    fun formDateMustBeRealAndNotFuture() {
        assertNull(weightDateError("2026-11-05", "2026-11-05"))
        assertNull(weightDateError("2026-01-01", "2026-11-05"))
        assertNotNull(weightDateError("2026-11-06", "2026-11-05"))
        assertNotNull(weightDateError("2026-02-30", "2026-11-05"))
        assertNotNull(weightDateError("", "2026-11-05"))
    }

    // --- Rangos y resumen ---

    private val series = listOf(
        w("2026-06-01", 85.0), w("2026-09-10", 82.0), w("2026-10-10", 80.0),
        w("2026-10-20", 79.5), w("2026-11-05", 78.5),
    )

    @Test
    fun rangesFilterByDaysEndingToday() {
        val today = "2026-11-05"
        assertEquals(5, WeightProgress.inRange(series, WeightRange.ALL, today).size)
        assertEquals(
            listOf("2026-09-10", "2026-10-10", "2026-10-20", "2026-11-05"),
            WeightProgress.inRange(series, WeightRange.DAYS_90, today).map { it.date },
        )
        assertEquals(
            listOf("2026-10-10", "2026-10-20", "2026-11-05"),
            WeightProgress.inRange(series, WeightRange.DAYS_30, today).map { it.date },
        )
    }

    @Test
    fun rangeBoundaryIsInclusiveAndFutureIgnored() {
        // 30 días terminando el 05-11 empiezan el 07-10.
        val edge = listOf(w("2026-10-06", 80.0), w("2026-10-07", 79.0), w("2026-11-06", 70.0))
        assertEquals(listOf("2026-10-07"), WeightProgress.inRange(edge, WeightRange.DAYS_30, "2026-11-05").map { it.date })
    }

    @Test
    fun unsortedInputIsSorted() {
        val shuffled = listOf(series[3], series[0], series[4], series[1], series[2])
        assertEquals(series.map { it.date }, WeightProgress.inRange(shuffled, WeightRange.ALL, "2026-11-05").map { it.date })
    }

    @Test
    fun summaryComparesWithFirstEntryOfTheRange() {
        val all = WeightProgress.summary(WeightProgress.inRange(series, WeightRange.ALL, "2026-11-05"))!!
        assertEquals(-6.5, all.deltaKg)
        assertEquals("2026-06-01", all.first.date)
        val month = WeightProgress.summary(WeightProgress.inRange(series, WeightRange.DAYS_30, "2026-11-05"))!!
        assertEquals(-1.5, month.deltaKg)
        assertEquals(78.5, month.latest.weightKg)
    }

    @Test
    fun summaryOfSingleEntryHasNoChangeAndEmptyIsNull() {
        val s = WeightProgress.summary(listOf(w("2026-11-05", 78.5)))!!
        assertTrue(!s.hasChange)
        assertEquals(0.0, s.deltaKg)
        assertNull(WeightProgress.summary(emptyList()))
    }

    @Test
    fun deltaIsRoundedToTenths() {
        // 78,3 - 78,1 en coma flotante no es exactamente 0,2.
        assertEquals(0.2, WeightProgress.summary(listOf(w("2026-11-01", 78.1), w("2026-11-02", 78.3)))!!.deltaKg)
    }

    // --- Gráfica ---

    @Test
    fun emptyHasNoChart() = assertNull(WeightProgress.chart(emptyList()))

    @Test
    fun singlePointIsCentered() {
        val c = WeightProgress.chart(listOf(w("2026-11-05", 78.5)))!!
        assertEquals(1, c.points.size)
        assertEquals(0.5f, c.points[0].x)
        assertTrue(c.points[0].y in 0f..1f)
        assertTrue(c.yMin < 78.5 && c.yMax > 78.5)
    }

    @Test
    fun pointsAreSpacedProportionallyToTime() {
        val c = WeightProgress.chart(listOf(w("2026-11-01", 80.0), w("2026-11-02", 79.0), w("2026-11-11", 78.0)))!!
        assertEquals(listOf(0f, 0.1f, 1f), c.points.map { it.x })
        assertTrue(c.points[0].y > c.points[2].y) // más peso = más arriba
    }

    @Test
    fun axisContainsDataWithPaddingAndReasonableTickCount() {
        val data = listOf(
            listOf(w("2026-11-01", 80.0), w("2026-11-02", 80.0)), // todo igual
            listOf(w("2026-11-01", 80.0), w("2026-11-02", 80.3)), // variación mínima
            listOf(w("2026-01-01", 120.0), w("2026-11-02", 62.5)), // variación grande
            listOf(w("2026-11-01", 20.0), w("2026-11-02", 500.0)), // extremos
        )
        for (d in data) {
            val c = WeightProgress.chart(d)!!
            assertTrue(c.yMin < d.minOf { it.weightKg }, "yMin $c")
            assertTrue(c.yMax > d.maxOf { it.weightKg }, "yMax $c")
            assertTrue(c.ticks.size in 2..8, "ticks ${c.ticks}")
            assertEquals(c.yMin, c.ticks.first())
            assertEquals(c.yMax, c.ticks.last())
            c.points.forEach { assertTrue(it.y in 0f..1f && it.x in 0f..1f) }
        }
    }

    @Test
    fun manyPointsStayInsideTheUnitSquare() {
        val many = (0 until 400).map { w(epochDayToIsoDate(20_000L + it), 90.0 - it * 0.03 + (it % 7) * 0.2) }
        val c = WeightProgress.chart(many)!!
        assertEquals(400, c.points.size)
        assertEquals(0f, c.points.first().x)
        assertEquals(1f, c.points.last().x)
        c.points.forEach { assertTrue(it.y in 0f..1f) }
    }

    @Test
    fun chartHasOnePointPerEntryInEachRange() {
        val today = "2026-11-09"
        val d0 = isoDateToEpochDay(today)!!
        // Un registro al día durante 120 días (el más antiguo, a 119 días de hoy).
        val series = (0 until 120).map { w(epochDayToIsoDate(d0 - 119 + it), 80.0 - it * 0.05) }
        for ((range, expected) in listOf(WeightRange.DAYS_30 to 30, WeightRange.DAYS_90 to 90, WeightRange.ALL to 120)) {
            val visible = WeightProgress.inRange(series, range, today)
            val c = WeightProgress.chart(visible)!!
            assertEquals(expected, c.points.size, "$range")
            assertEquals(visible.map { it.date }, c.points.map { it.entry.date }, "$range")
            assertEquals(0f, c.points.first().x)
            assertEquals(1f, c.points.last().x)
            // x crece con el tiempo y la y refleja el peso (más peso = más arriba).
            c.points.zipWithNext().forEach { (a, b) -> assertTrue(a.x < b.x && a.y > b.y, "$range") }
            c.points.forEach { assertEquals(c.yFraction(it.entry.weightKg), it.y, 1e-6f) }
        }
    }

    @Test
    fun sameDayEntriesKeepOnePointEach() {
        val c = WeightProgress.chart(listOf(w("2026-11-05", 78.5), w("2026-11-05", 78.0)))!!
        assertEquals(2, c.points.size)
        assertEquals(listOf(0.5f, 0.5f), c.points.map { it.x })
    }

    // --- Formato ---

    @Test
    fun formatsForDisplay() {
        assertEquals("5 nov", formatDayMonth("2026-11-05"))
        assertEquals("5 nov 2026", formatDayMonthYear("2026-11-05"))
        assertEquals("78,4 kg", formatKg(78.4))
        assertEquals("80,0 kg", formatKg(80.0))
        assertEquals("−1,5 kg", formatKgDelta(-1.5))
        assertEquals("+0,3 kg", formatKgDelta(0.3))
        assertEquals("Sin cambios", formatKgDelta(0.0))
    }

    // --- Peso objetivo y proyección ---

    /** Un registro cada [every] días desde [from], bajando (o subiendo) [perDay] kg al día desde [start]. */
    private fun trend(from: String, n: Int, start: Double, perDay: Double, every: Int = 1): List<WeightEntry> {
        val d0 = isoDateToEpochDay(from)!!
        return (0 until n).map { w(epochDayToIsoDate(d0 + it * every), start + perDay * it * every) }
    }

    @Test
    fun noGoalOrNoEntriesGivesNoProgress() {
        assertNull(WeightProgress.goalProgress(trend("2026-11-01", 10, 80.0, -0.1), null, "2026-11-10"))
        assertNull(WeightProgress.goalProgress(emptyList(), 70.0, "2026-11-10"))
    }

    @Test
    fun remainingAndPercentFromFirstEntry() {
        val p = WeightProgress.goalProgress(listOf(w("2026-11-01", 90.0), w("2026-11-10", 85.0)), 80.0, "2026-11-10")!!
        assertEquals(5.0, p.remainingKg)
        assertEquals(50, p.percent)
        assertEquals(85.0, p.currentKg)
        assertEquals(false, p.reached)
        assertEquals("Te faltan 5,0 kg (50 % del camino)", goalSummaryText(p))
    }

    @Test
    fun goalAboveCurrentWeightWorksForGaining() {
        val p = WeightProgress.goalProgress(listOf(w("2026-11-01", 60.0), w("2026-11-10", 63.0)), 70.0, "2026-11-10")!!
        assertEquals(7.0, p.remainingKg)
        assertEquals(30, p.percent)
    }

    @Test
    fun reachedGoalHas100PercentNoRemainingAndNoProjection() {
        val down = WeightProgress.goalProgress(listOf(w("2026-11-01", 90.0), w("2026-11-10", 79.5)), 80.0, "2026-11-10")!!
        assertEquals(true, down.reached); assertEquals(100, down.percent); assertEquals(0.0, down.remainingKg)
        assertNull(down.projection)
        val up = WeightProgress.goalProgress(listOf(w("2026-11-01", 60.0), w("2026-11-10", 70.0)), 70.0, "2026-11-10")!!
        assertEquals(true, up.reached); assertEquals("¡Objetivo alcanzado!", goalSummaryText(up))
    }

    @Test
    fun movingAwayFromGoalClampsPercentAtZero() {
        val p = WeightProgress.goalProgress(listOf(w("2026-11-01", 85.0), w("2026-11-10", 88.0)), 80.0, "2026-11-10")!!
        assertEquals(0, p.percent)
        assertEquals(8.0, p.remainingKg)
    }

    @Test
    fun startingExactlyAtGoalDoesNotDivideByZero() {
        val p = WeightProgress.goalProgress(listOf(w("2026-11-01", 80.0), w("2026-11-10", 81.0)), 80.0, "2026-11-10")!!
        assertEquals(0, p.percent)
        assertEquals(1.0, p.remainingKg)
    }

    @Test
    fun tooFewEntriesOrTooShortSpanGivesNotEnoughData() {
        val two = listOf(w("2026-11-01", 90.0), w("2026-11-20", 88.0))
        assertEquals(WeightProjection.NotEnoughData, WeightProgress.goalProgress(two, 80.0, "2026-11-20")!!.projection)
        val tight = trend("2026-11-01", 4, 90.0, -0.2) // 4 registros en 3 días
        assertEquals(WeightProjection.NotEnoughData, WeightProgress.goalProgress(tight, 80.0, "2026-11-04")!!.projection)
    }

    @Test
    fun oldEntriesOutsideTheWindowDoNotCountAsTrend() {
        // Muchos registros hace meses y solo dos recientes: la ventana de 28 días no llega a 3.
        val entries = trend("2026-06-01", 10, 95.0, -0.1) + listOf(w("2026-11-01", 85.0), w("2026-11-10", 84.0))
        assertEquals(WeightProjection.NotEnoughData, WeightProgress.goalProgress(entries, 80.0, "2026-11-10")!!.projection)
    }

    @Test
    fun trendTowardGoalEstimatesDate() {
        // 0,1 kg/día durante 15 días: 90 → 88,6; faltan 8,6 kg → 86 días tras el último registro (2026-11-15).
        val entries = trend("2026-11-01", 15, 90.0, -0.1)
        val p = WeightProgress.goalProgress(entries, 80.0, "2026-11-15")!!
        val est = assertIs<WeightProjection.Estimated>(p.projection)
        assertEquals(-0.7, est.kgPerWeek)
        assertEquals(86, est.daysLeft)
        assertEquals(epochDayToIsoDate(isoDateToEpochDay("2026-11-15")!! + 86), est.date)
        assertTrue(projectionText(est).contains("Llegarías el "))
    }

    @Test
    fun estimateCountsFromTodayWhenLastEntryIsOld() {
        val entries = trend("2026-11-01", 15, 90.0, -0.1) // último registro 2026-11-15
        val est = assertIs<WeightProjection.Estimated>(
            WeightProgress.goalProgress(entries, 80.0, "2026-12-01")!!.projection,
        )
        assertEquals(86 - 16, est.daysLeft) // 16 días desde el último registro hasta hoy
    }

    @Test
    fun estimateInThePastIsClampedToToday() {
        val entries = trend("2026-11-01", 15, 90.0, -0.1)
        val est = assertIs<WeightProjection.Estimated>(
            WeightProgress.goalProgress(entries, 80.0, "2027-06-01")!!.projection,
        )
        assertEquals(0, est.daysLeft)
        assertEquals("2027-06-01", est.date)
        assertTrue(projectionText(est).startsWith("Llegarías hoy"))
    }

    @Test
    fun trendInTheOppositeDirectionOrFlatIsMovingAway() {
        val gaining = trend("2026-11-01", 15, 85.0, 0.1)
        assertEquals(WeightProjection.MovingAway, WeightProgress.goalProgress(gaining, 80.0, "2026-11-15")!!.projection)
        val flat = trend("2026-11-01", 15, 85.0, 0.0)
        assertEquals(WeightProjection.MovingAway, WeightProgress.goalProgress(flat, 80.0, "2026-11-15")!!.projection)
        val losingButWantToGain = trend("2026-11-01", 15, 70.0, -0.1)
        assertEquals(WeightProjection.MovingAway, WeightProgress.goalProgress(losingButWantToGain, 80.0, "2026-11-15")!!.projection)
    }

    @Test
    fun verySlowTrendIsTooSlow() {
        // 0,012 kg/día hacia un objetivo a 20 kg: >730 días.
        val entries = trend("2026-11-01", 15, 100.0, -0.012)
        assertEquals(WeightProjection.TooSlow, WeightProgress.goalProgress(entries, 80.0, "2026-11-15")!!.projection)
    }

    @Test
    fun trendUsesRegressionNotJustEndpoints() {
        // Un pesaje atípico en medio no debe disparar la estimación como lo haría una recta primero-último.
        val entries = listOf(
            w("2026-11-01", 90.0), w("2026-11-05", 89.6), w("2026-11-09", 92.0), w("2026-11-13", 88.8), w("2026-11-17", 88.4),
        )
        val est = assertIs<WeightProjection.Estimated>(WeightProgress.goalProgress(entries, 80.0, "2026-11-17")!!.projection)
        assertTrue(est.kgPerWeek < 0)
        assertTrue(est.daysLeft > 60)
    }
}
