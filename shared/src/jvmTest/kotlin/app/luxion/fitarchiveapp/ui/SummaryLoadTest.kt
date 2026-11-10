package app.luxion.fitarchiveapp.ui

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.data.SqlDelightDiaryStore
import app.luxion.fitarchiveapp.data.SqlDelightGoalStore
import app.luxion.fitarchiveapp.data.SqlDelightWeightStore
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.domain.PeriodRange
import app.luxion.fitarchiveapp.domain.SummaryPeriod
import app.luxion.fitarchiveapp.model.DailyGoals
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.NewDiaryEntry
import app.luxion.fitarchiveapp.model.NewWeightEntry
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import app.luxion.fitarchiveapp.ui.screens.loadPeriodSummary
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
}
