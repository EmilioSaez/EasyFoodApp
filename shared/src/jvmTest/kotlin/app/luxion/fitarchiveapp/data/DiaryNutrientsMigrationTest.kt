package app.luxion.fitarchiveapp.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Migración 16.sqm: columnas nuevas en el Diario sin perder las entradas existentes. */
class DiaryNutrientsMigrationTest {
    private val extra = listOf("saturated_fat_100g", "sugars_100g", "fiber_100g", "salt_100g")

    private fun JdbcSqliteDriver.columns(): List<String> = executeQuery(
        null, "PRAGMA table_info(DiaryEntryEntity)",
        { c ->
            val names = mutableListOf<String>()
            while (c.next().value) names += c.getString(1)!!
            QueryResult.Value(names)
        }, 0,
    ).value

    @Test
    fun existingEntriesSurviveWithNullExtras() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(driver)
        // Estado v16: sin las columnas nuevas.
        for (c in extra) driver.execute(null, "ALTER TABLE DiaryEntryEntity DROP COLUMN $c", 0)
        driver.execute(
            null,
            "INSERT INTO DiaryEntryEntity(id, date, meal, barcode, name, quantity, unit, serving_size_g, " +
                "kcal_100g, protein_100g, carbs_100g, fat_100g, created_at) " +
                "VALUES (7, '2026-11-04', 'LUNCH', '123', 'Arroz', 150.0, 'GRAMS', 30.0, 350.0, 7.0, 78.0, 1.0, 99)",
            0,
        )

        EasyFoodDatabase.Schema.migrate(driver, 16, EasyFoodDatabase.Schema.version)

        assertEquals(extra, driver.columns().takeLast(4))
        val e = SqlDelightDiaryStore(EasyFoodDatabase(driver).diaryEntryQueries).find(7)!!
        assertEquals("Arroz", e.name)
        assertEquals(150.0, e.quantity.amount)
        assertEquals(30.0, e.servingSizeG)
        assertEquals(350.0, e.nutrients.kcal)
        assertEquals(78.0, e.nutrients.carbsG)
        assertEquals(99L, e.createdAtMillis)
        assertNull(e.nutrients.fiberG)
        assertNull(e.nutrients.saltG)
        driver.close()
    }

    @Test
    fun migratedColumnsMatchFreshSchema() {
        val fresh = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(fresh)
        val migrated = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(migrated)
        for (c in extra) migrated.execute(null, "ALTER TABLE DiaryEntryEntity DROP COLUMN $c", 0)
        EasyFoodDatabase.Schema.migrate(migrated, 16, EasyFoodDatabase.Schema.version)
        assertEquals(fresh.columns(), migrated.columns())
        fresh.close(); migrated.close()
    }
}
