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
import app.luxion.fitarchiveapp.ui.screens.weightDateError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        assertTrue(c.points.size > WeightProgress.MAX_MARKED_POINTS)
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
}
