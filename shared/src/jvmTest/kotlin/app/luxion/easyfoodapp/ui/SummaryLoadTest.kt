package app.luxion.easyfoodapp.ui

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.easyfoodapp.data.SqlDelightDiaryStore
import app.luxion.easyfoodapp.data.SqlDelightGoalStore
import app.luxion.easyfoodapp.data.SqlDelightWeightStore
import app.luxion.easyfoodapp.db.EasyFoodDatabase
import app.luxion.easyfoodapp.domain.PeriodRange
import app.luxion.easyfoodapp.domain.SummaryPeriod
import app.luxion.easyfoodapp.domain.WeekComparison
import app.luxion.easyfoodapp.model.DailyGoals
import app.luxion.easyfoodapp.model.MealType
import app.luxion.easyfoodapp.model.NewDiaryEntry
import app.luxion.easyfoodapp.model.NewWeightEntry
import app.luxion.easyfoodapp.model.Nutrients
import app.luxion.easyfoodapp.model.Quantity
import app.luxion.easyfoodapp.model.QuantityUnit
import app.luxion.easyfoodapp.ui.screens.loadPeriodSummary
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** El resumen se construye con los almacenes reales (SQLDelight en memoria), sin tabla propia. */
class SummaryLoadTest {
    private fun withDb(block: suspend (EasyFoodDatabase) -> Unit) = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(driver)
        try { block(EasyFoodDatabase(driver)) } finally { driver.close() }
    }

    private suspend fun log(db: EasyFoodDatabase, date: String, kcalPer100: Double, grams: Double) {
        SqlDelightDiaryStore(db.diaryEntryQueries).add(
            NewDiaryEntry(date, MealType.LUNCH, null, "Arroz", Quantity(grams, QuantityUnit.GRAMS), null, Nutrients(kcalPer100, 10.0, 40.0, 2.0)),
            1L,
        )
    }

    private suspend fun load(db: EasyFoodDatabase, range: PeriodRange, today: String) = loadPeriodSummary(
        range, today,
        SqlDelightDiaryStore(db.diaryEntryQueries), SqlDelightGoalStore(db.dailyGoalQueries), SqlDelightWeightStore(db.weightEntryQueries),
    )

    @Test
    fun weekSummaryComesFromDiaryGoalsAndWeights() = withDb { db ->
        SqlDelightGoalStore(db.dailyGoalQueries).save(DailyGoals(kcal = 2000))
        log(db, "2026-11-03", 200.0, 1000.0) // 2.000 kcal: cumplido
        log(db, "2026-11-04", 100.0, 1000.0) // hoy: 1.000 kcal, aún sin completar
        val weights = SqlDelightWeightStore(db.weightEntryQueries)
        weights.save(NewWeightEntry("2026-11-02", 80.0), 1L)
        weights.save(NewWeightEntry("2026-11-04", 79.5), 2L)

        val s = load(db, PeriodRange.of(SummaryPeriod.WEEK, "2026-11-04"), "2026-11-04")

        assertEquals(2, s.loggedDays)
        assertEquals(1500.0, s.averages!!.kcal, 1e-9)
        assertEquals(1, s.completedDays)
        assertEquals(2, s.judgedDays) // lunes (sin registro) y martes; hoy no se juzga
        assertEquals(-0.5, s.weight!!.deltaKg, 1e-9)
        assertEquals(-500.0, s.weight!!.kcalVsGoal!!, 1e-9)
    }

    @Test
    fun otherPeriodsAndMissingGoalAreHandled() = withDb { db ->
        log(db, "2026-10-20", 100.0, 1500.0)

        val october = load(db, PeriodRange.of(SummaryPeriod.MONTH, "2026-10-01"), "2026-11-04")
        assertEquals(1, october.loggedDays)
        assertNull(october.completedDays) // sin objetivo no se evalúa
        assertNull(october.bestDay)

        val november = load(db, PeriodRange.of(SummaryPeriod.MONTH, "2026-11-04"), "2026-11-04")
        assertEquals(true, november.isEmpty)
    }

    @Test
    fun weekComparisonAndWeightTrendComeFromStoredData() = withDb { db ->
        SqlDelightGoalStore(db.dailyGoalQueries).save(DailyGoals(kcal = 2000))
        log(db, "2026-11-02", 200.0, 1000.0) // semana anterior: 2.000 kcal
        log(db, "2026-11-03", 100.0, 1000.0) // semana anterior: 1.000 kcal
        log(db, "2026-11-09", 200.0, 1000.0) // semana actual: 2.000 kcal
        val weights = SqlDelightWeightStore(db.weightEntryQueries)
        weights.save(NewWeightEntry("2026-11-02", 82.0), 1L)
        weights.save(NewWeightEntry("2026-11-09", 80.0), 2L)
        weights.save(NewWeightEntry("2026-11-10", 79.0), 3L)

        val range = PeriodRange.of(SummaryPeriod.WEEK, "2026-11-10")
        val current = load(db, range, "2026-11-10")
        val comparison = WeekComparison.of(current, load(db, range.shifted(-1), "2026-11-10"))!!

        assertEquals(500.0, comparison.kcal.delta!!, 1e-9) // 2.000 frente a 1.500
        assertEquals(0.0, comparison.completedDays.delta!!, 1e-9) // 1 y 1 cumplidos
        // La media móvil del lunes incluye el pesaje de la semana anterior.
        assertEquals(listOf("2026-11-09", "2026-11-10"), current.weightTrend.map { it.date })
        assertEquals(81.0, current.weightTrend[0].weightKg, 1e-9)
    }
}
