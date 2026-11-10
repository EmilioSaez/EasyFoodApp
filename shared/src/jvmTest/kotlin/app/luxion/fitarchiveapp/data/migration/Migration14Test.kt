package app.luxion.fitarchiveapp.data.migration

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Migración 14.sqm (v14 -> v15): tabla `ProductRefreshEntity` de seguimiento del refresco de productos de OFF.
 * TODO: pasar a `MigrationTestKit` cuando entre la convención de tests de migración (PR de migraciones-tests).
 */
class Migration14Test {
    private fun JdbcSqliteDriver.rows(sql: String, columns: Int = 1): List<List<String?>> = executeQuery(
        null, sql,
        { c ->
            val out = mutableListOf<List<String?>>()
            while (c.next().value) out += (0 until columns).map { c.getString(it) }
            QueryResult.Value(out)
        }, 0,
    ).value

    /** BD en el estado anterior a 14.sqm: esquema actual sin la tabla de seguimiento ni su trigger. */
    private fun legacy(): JdbcSqliteDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(driver)
        driver.execute(null, "DROP TRIGGER product_refresh_delete", 0)
        driver.execute(null, "DROP TABLE ProductRefreshEntity", 0)
        return driver
    }

    @Test
    fun keepsEveryExistingRowAndBackfillsOnlyOffProducts() {
        val driver = legacy()
        driver.execute(
            null,
            "INSERT INTO ProductEntity(barcode, name, brand, kcal, source, cached_at) VALUES " +
                "('1', 'Pan', 'Marca', 250, 'OPEN_FOOD_FACTS', 777), ('generic:2', 'Manzana', NULL, 52, 'GENERIC', 5)",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO DiaryEntryEntity(date, meal, barcode, name, quantity, unit, kcal_100g, created_at) " +
                "VALUES ('2026-11-01', 'LUNCH', '1', 'Pan', 100, 'GRAMS', 250, 1)",
            0,
        )
        driver.execute(null, "INSERT INTO FavoriteFoodEntity(key, created_at) VALUES ('1', 1)", 0)
        val before = driver.rows("SELECT barcode, name, brand, kcal, cached_at FROM ProductEntity ORDER BY barcode", 5)

        EasyFoodDatabase.Schema.migrate(driver, 11, EasyFoodDatabase.Schema.version)

        assertEquals(before, driver.rows("SELECT barcode, name, brand, kcal, cached_at FROM ProductEntity ORDER BY barcode", 5))
        assertEquals(listOf("250.0"), driver.rows("SELECT kcal_100g FROM DiaryEntryEntity").map { it[0] })
        assertEquals(listOf("1"), driver.rows("SELECT key FROM FavoriteFoodEntity").map { it[0] })
        assertEquals(
            listOf(listOf<String?>("1", "777", null, null, null)),
            driver.rows("SELECT barcode, refreshed_at, checked_at, changed_at, change_summary FROM ProductRefreshEntity", 5),
        )
        driver.close()
    }

    @Test
    fun isIdempotentAndRecreatesTheCleanupTrigger() {
        val driver = legacy()
        driver.execute(null, "INSERT INTO ProductEntity(barcode, name, source, cached_at) VALUES ('1', 'Pan', 'OPEN_FOOD_FACTS', 1)", 0)

        EasyFoodDatabase.Schema.migrate(driver, 11, EasyFoodDatabase.Schema.version)
        EasyFoodDatabase.Schema.migrate(driver, 11, EasyFoodDatabase.Schema.version)

        assertEquals(1, driver.rows("SELECT barcode FROM ProductRefreshEntity").size)
        driver.execute(null, "DELETE FROM ProductEntity WHERE barcode = '1'", 0)
        assertEquals(0, driver.rows("SELECT barcode FROM ProductRefreshEntity").size)
        driver.close()
    }

    @Test
    fun migratedSchemaHasTheSameTablesAsAFreshDatabase() {
        val fresh = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(fresh)
        val migrated = legacy()
        EasyFoodDatabase.Schema.migrate(migrated, 11, EasyFoodDatabase.Schema.version)

        val sql = "SELECT name FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' AND name NOT LIKE 'ProductFts_%' ORDER BY name"
        assertEquals(fresh.rows(sql).map { it[0] }, migrated.rows(sql).map { it[0] })
        fresh.close()
        migrated.close()
    }
}
