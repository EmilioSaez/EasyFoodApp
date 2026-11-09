package app.luxion.fitarchiveapp.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.model.NewWeightEntry
import app.luxion.fitarchiveapp.model.WeightEntry
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SqlDelightWeightStoreTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: EasyFoodDatabase
    private lateinit var store: SqlDelightWeightStore

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(driver)
        db = EasyFoodDatabase(driver)
        store = SqlDelightWeightStore(db.weightEntryQueries)
    }

    @AfterTest
    fun tearDown() = driver.close()

    private fun e(date: String, kg: Double) = NewWeightEntry(date, kg)

    @Test
    fun emptyAtStart() = runTest { assertEquals(emptyList(), store.all()) }

    @Test
    fun savesAndListsOldestFirst() = runTest {
        store.save(e("2026-11-03", 79.0), 30L)
        store.save(e("2026-11-01", 80.5), 10L)
        store.save(e("2026-11-02", 79.8), 20L)
        assertEquals(
            listOf(
                WeightEntry("2026-11-01", 80.5, 10L, 10L),
                WeightEntry("2026-11-02", 79.8, 20L, 20L),
                WeightEntry("2026-11-03", 79.0, 30L, 30L),
            ),
            store.all(),
        )
    }

    @Test
    fun savingTheSameDayReplacesKeepingCreationTime() = runTest {
        store.save(e("2026-11-01", 80.0), 10L)
        store.save(e("2026-11-01", 79.0), 99L)
        assertEquals(listOf(WeightEntry("2026-11-01", 79.0, 10L, 99L)), store.all())
    }

    @Test
    fun updateChangesWeightAndDate() = runTest {
        store.save(e("2026-11-01", 80.0), 10L)
        assertTrue(store.update("2026-11-01", e("2026-10-30", 81.0), 50L))
        assertEquals(listOf(WeightEntry("2026-10-30", 81.0, 10L, 50L)), store.all())
    }

    @Test
    fun updateOntoAnotherDayReplacesIt() = runTest {
        store.save(e("2026-11-01", 80.0), 10L)
        store.save(e("2026-11-02", 79.0), 20L)
        assertTrue(store.update("2026-11-01", e("2026-11-02", 78.0), 60L))
        assertEquals(listOf("2026-11-02"), store.all().map { it.date })
        assertEquals(78.0, store.all().single().weightKg)
    }

    @Test
    fun updateOfMissingEntryReturnsFalseAndWritesNothing() = runTest {
        assertFalse(store.update("2026-11-01", e("2026-11-01", 80.0), 1L))
        assertEquals(emptyList(), store.all())
    }

    @Test
    fun deleteRemovesAndReportsExistence() = runTest {
        store.save(e("2026-11-01", 80.0), 10L)
        assertTrue(store.delete("2026-11-01"))
        assertFalse(store.delete("2026-11-01"))
        assertEquals(emptyList(), store.all())
    }

    @Test
    fun databaseRejectsOutOfRangeWeightEvenBypassingTheModel() {
        val failed = runCatching { db.weightEntryQueries.upsert("2026-11-01", 5.0, 0, 0) }.isFailure
        assertTrue(failed)
    }

    @Test
    fun migrationFromV4CreatesTableAndKeepsData() = runTest {
        // Simula una base v4: sin la tabla de peso, con un objetivo ya guardado.
        SqlDelightGoalStore(db.dailyGoalQueries).save(app.luxion.fitarchiveapp.model.DailyGoals(kcal = 2000))
        driver.execute(null, "DROP TABLE WeightEntryEntity", 0)

        EasyFoodDatabase.Schema.migrate(driver, 4, EasyFoodDatabase.Schema.version)
        assertTrue(EasyFoodDatabase.Schema.version >= 5L)

        val migrated = SqlDelightWeightStore(EasyFoodDatabase(driver).weightEntryQueries)
        migrated.save(e("2026-11-01", 80.0), 1L)
        assertEquals(1, migrated.all().size)
        assertEquals(2000, SqlDelightGoalStore(db.dailyGoalQueries).get().kcal)

        // Idempotente: repetirla sobre una base que ya tiene la tabla no falla ni borra datos.
        EasyFoodDatabase.Schema.migrate(driver, 4, EasyFoodDatabase.Schema.version)
        assertEquals(1, migrated.all().size)
    }
}
