package app.luxion.fitarchiveapp.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.model.NewWeightEntry
import app.luxion.fitarchiveapp.model.WaterRules
import app.luxion.fitarchiveapp.ui.components.formatLiters
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SqlDelightWaterStoreTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: EasyFoodDatabase
    private lateinit var store: SqlDelightWaterStore

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(driver)
        db = EasyFoodDatabase(driver)
        store = SqlDelightWaterStore(db.waterQueries)
    }

    @AfterTest
    fun tearDown() = driver.close()

    private fun count(table: String): Long = driver.executeQuery(
        null, "SELECT COUNT(*) FROM $table",
        { c -> QueryResult.Value(if (c.next().value) c.getLong(0) else null) }, 0,
    ).value!!

    @Test
    fun emptyDayIsZeroAndGoalDefaultsToTwoLiters() = runTest {
        assertEquals(0, store.total("2026-11-04"))
        assertEquals(2000, store.goalMl())
    }

    @Test
    fun addsAccumulateWithinADayAndStayPerDay() = runTest {
        assertEquals(250, store.add("2026-11-04", 250, 1L))
        assertEquals(750, store.add("2026-11-04", 500, 2L))
        assertEquals(1000, store.add("2026-11-05", 1000, 3L))
        assertEquals(750, store.total("2026-11-04"))
        assertEquals(1000, store.total("2026-11-05"))
    }

    @Test
    fun undoSubtractsAndAZeroDayHasNoRow() = runTest {
        store.add("2026-11-04", 250, 1L)
        assertEquals(0, store.add("2026-11-04", -250, 2L))
        assertEquals(0L, count("WaterEntity"))
    }

    @Test
    fun totalIsClampedBetweenZeroAndTheDailyCap() = runTest {
        assertEquals(0, store.add("2026-11-04", -500, 1L))
        assertEquals(WaterRules.MAX_DAY_ML, store.add("2026-11-04", 50_000, 2L))
        assertEquals(WaterRules.MAX_DAY_ML, store.add("2026-11-04", 1000, 3L))
    }

    @Test
    fun goalIsEditableValidatedAndKeptInASingleRow() = runTest {
        store.setGoalMl(2500, 1L)
        store.setGoalMl(3000, 2L)
        assertEquals(3000, store.goalMl())
        assertEquals(1L, count("WaterGoalEntity"))
        assertFailsWith<IllegalArgumentException> { store.setGoalMl(100, 3L) }
        assertFailsWith<IllegalArgumentException> { store.setGoalMl(20_000, 3L) }
        assertEquals(3000, store.goalMl())
    }

    @Test
    fun theDatabaseAlsoRejectsInvalidValues() {
        assertFailsWith<Exception> { db.waterQueries.upsert("2026-11-04", 0, 1L) }
        assertFailsWith<Exception> { db.waterQueries.upsert("2026-11-04", 30_000, 1L) }
        assertFailsWith<Exception> { db.waterQueries.upsertGoal(100, 1L) }
    }

    @Test
    fun migrationFromV8CreatesTheWaterTablesAndKeepsOtherData() = runTest {
        SqlDelightWeightStore(db.weightEntryQueries).save(NewWeightEntry("2026-11-01", 80.0), 1L)
        driver.execute(null, "DROP TABLE WaterEntity", 0)
        driver.execute(null, "DROP TABLE WaterGoalEntity", 0)
        for (c in listOf("saturated_fat_100g", "sugars_100g", "fiber_100g", "salt_100g")) driver.execute(null, "ALTER TABLE DiaryEntryEntity DROP COLUMN $c", 0) // 16.sqm
        assertTrue(EasyFoodDatabase.Schema.version >= 9)

        // Idempotente: aplicarla dos veces da el mismo resultado.
        driver.execute(null, "ALTER TABLE DiaryEntryEntity DROP COLUMN quick", 0) // la añade 12.sqm (no es idempotente)
        EasyFoodDatabase.Schema.migrate(driver, 8, EasyFoodDatabase.Schema.version)
        for (c in listOf("quick", "saturated_fat_100g", "sugars_100g", "fiber_100g", "salt_100g")) driver.execute(null, "ALTER TABLE DiaryEntryEntity DROP COLUMN $c", 0) // 12.sqm y 16.sqm
        EasyFoodDatabase.Schema.migrate(driver, 8, EasyFoodDatabase.Schema.version)

        val migrated = SqlDelightWaterStore(EasyFoodDatabase(driver).waterQueries)
        assertEquals(0, migrated.total("2026-11-04"))
        assertEquals(2000, migrated.goalMl())
        assertEquals(250, migrated.add("2026-11-04", 250, 1L))
        assertEquals(1, SqlDelightWeightStore(db.weightEntryQueries).all().size)
    }

    @Test
    fun formatsLitersWithCommaAndNoTrailingZeros() {
        assertEquals("0", formatLiters(0))
        assertEquals("0,25", formatLiters(250))
        assertEquals("0,5", formatLiters(500))
        assertEquals("1,25", formatLiters(1250))
        assertEquals("2", formatLiters(2000))
        assertEquals("2,75", formatLiters(2750))
        assertEquals("10", formatLiters(10_000))
    }
}
