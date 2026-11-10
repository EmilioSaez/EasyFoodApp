package app.luxion.fitarchiveapp.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.model.ExerciseRules
import app.luxion.fitarchiveapp.model.NewExercise
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SqlDelightExerciseStoreTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: EasyFoodDatabase
    private lateinit var store: SqlDelightExerciseStore

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(driver)
        db = EasyFoodDatabase(driver)
        store = SqlDelightExerciseStore(db.exerciseQueries)
    }

    @AfterTest
    fun tearDown() = driver.close()

    private fun count(d: JdbcSqliteDriver, table: String): Long = d.executeQuery(
        null, "SELECT COUNT(*) FROM $table",
        { c -> QueryResult.Value(if (c.next().value) c.getLong(0) else null) }, 0,
    ).value!!

    @Test
    fun emptyDayHasNoEntriesAndPercentDefaultsToHalf() = runTest {
        assertEquals(emptyList(), store.entriesForDate("2026-11-10"))
        assertEquals(ExerciseRules.DEFAULT_PERCENT, store.percent())
        assertEquals(emptyMap(), store.dailyKcal("0000-01-01", "9999-12-31"))
    }

    @Test
    fun severalActivitiesPerDayKeepRegistrationOrderAndStayPerDay() = runTest {
        val a = store.add(NewExercise("2026-11-10", "Correr", 400, 40), 1L)
        val b = store.add(NewExercise("2026-11-10", "  Pesas  ", 150, null), 2L)
        store.add(NewExercise("2026-11-11", "Bici", 300, 60), 3L)

        val day = store.entriesForDate("2026-11-10")
        assertEquals(listOf(a, b), day.map { it.id })
        assertEquals(listOf("Correr", "Pesas"), day.map { it.name }) // el nombre se guarda sin espacios sobrantes
        assertEquals(listOf<Int?>(40, null), day.map { it.minutes })
        assertEquals(1, store.entriesForDate("2026-11-11").size)
    }

    @Test
    fun deleteRemovesOnlyThatActivity() = runTest {
        val a = store.add(NewExercise("2026-11-10", "Correr", 400, 40), 1L)
        val b = store.add(NewExercise("2026-11-10", "Pesas", 150, null), 2L)
        store.delete(a)
        assertEquals(listOf(b), store.entriesForDate("2026-11-10").map { it.id })
        store.delete(999) // borrar algo que no existe no falla
    }

    @Test
    fun dailyKcalSumsPerDayWithinTheRange() = runTest {
        store.add(NewExercise("2026-11-09", "Andar", 100), 1L)
        store.add(NewExercise("2026-11-10", "Correr", 400), 2L)
        store.add(NewExercise("2026-11-10", "Pesas", 150), 3L)
        store.add(NewExercise("2026-11-12", "Bici", 300), 4L)
        assertEquals(mapOf("2026-11-10" to 550, "2026-11-12" to 300), store.dailyKcal("2026-11-10", "2026-11-12"))
    }

    @Test
    fun percentIsEditableValidatedAndKeptInASingleRow() = runTest {
        store.setPercent(0, 1L)
        assertEquals(0, store.percent())
        store.setPercent(75, 2L)
        assertEquals(75, store.percent())
        assertEquals(1L, count(driver, "ExerciseSettingsEntity"))
        assertFailsWith<IllegalArgumentException> { store.setPercent(101, 3L) }
        assertFailsWith<IllegalArgumentException> { store.setPercent(-1, 3L) }
        assertEquals(75, store.percent())
    }

    @Test
    fun theDatabaseAlsoRejectsInvalidValues() {
        assertFailsWith<Exception> { db.exerciseQueries.insert("2026-11-10", "Correr", 0, null, 1L) }
        assertFailsWith<Exception> { db.exerciseQueries.insert("2026-11-10", "Correr", 10_001, null, 1L) }
        assertFailsWith<Exception> { db.exerciseQueries.insert("2026-11-10", "  ", 100, null, 1L) }
        assertFailsWith<Exception> { db.exerciseQueries.insert("2026-11-10", "Correr", 100, 0, 1L) }
        assertFailsWith<Exception> { db.exerciseQueries.upsertPercent(101, 1L) }
        assertEquals(0L, count(driver, "ExerciseEntity"))
    }

    @Test
    fun newExerciseValidatesItsFields() {
        assertFailsWith<IllegalArgumentException> { NewExercise("2026-11-10", " ", 100) }
        assertFailsWith<IllegalArgumentException> { NewExercise("2026-11-10", "x".repeat(61), 100) }
        assertFailsWith<IllegalArgumentException> { NewExercise("2026-11-10", "Correr", 0) }
        assertFailsWith<IllegalArgumentException> { NewExercise("2026-11-10", "Correr", 100, 1441) }
        assertFailsWith<IllegalArgumentException> { NewExercise("10/11/2026", "Correr", 100) }
    }
}
