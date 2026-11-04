package app.luxion.fitarchiveapp.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Product
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** El índice FTS usa el rowid de ProductEntity (migración 2.sqm); debe seguir sincronizado en todos los caminos. */
class ProductFtsMigrationTest {
    private fun product(barcode: String, name: String) =
        Product(barcode, name, "Marca", null, Nutrients(100.0, 1.0, 1.0, 1.0))

    private fun JdbcSqliteDriver.count(table: String): Long = executeQuery(
        null, "SELECT count(*) FROM $table", { c -> QueryResult.Value(if (c.next().value) c.getLong(0) else null) }, 0,
    ).value ?: 0L

    @Test
    fun ftsStaysInSyncAfterRepeatedUpsertsUpdatesAndDeletes() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(driver)
        val db = EasyFoodDatabase(driver)
        val products = SqlDelightProductStore(db.productQueries)
        val search = SqlDelightProductSearchStore(db.productSearchQueries)

        products.save(product("1", "Galletas de avena"), 1)
        products.save(product("2", "Leche entera"), 1)
        products.save(product("1", "Galletas integrales"), 2) // upsert sobre existente
        products.save(product("1", "Galletas integrales"), 3)
        assertEquals(emptyList(), search.search("avena").map { it.barcode })
        assertEquals(listOf("1"), search.search("integrales").map { it.barcode })

        db.productQueries.deleteByBarcode("2")
        assertEquals(emptyList(), search.search("leche").map { it.barcode })
        // El rowid liberado se reutiliza: no debe heredar el texto antiguo.
        products.save(product("3", "Yogur natural"), 4)
        assertEquals(listOf("3"), search.search("yogur").map { it.barcode })
        assertEquals(emptyList(), search.search("leche").map { it.barcode })

        assertEquals(driver.count("ProductEntity"), driver.count("ProductFts"))
        driver.close()
    }

    @Test
    fun migrationFromV2RebuildsTheIndexAndKeepsSearchWorking() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(driver)
        // Devuelve la BD al estado v2: FTS y triggers antiguos (por barcode), sin índice por origen.
        val v2 = listOf(
            "DROP TRIGGER product_fts_insert", "DROP TRIGGER product_fts_update", "DROP TRIGGER product_fts_delete",
            "DROP TABLE ProductFts", "DROP INDEX product_source_idx",
            "CREATE VIRTUAL TABLE ProductFts USING fts5(name, brand, barcode UNINDEXED, tokenize = 'unicode61 remove_diacritics 2')",
            "CREATE TRIGGER product_fts_insert AFTER INSERT ON ProductEntity BEGIN " +
                "DELETE FROM ProductFts WHERE barcode = new.barcode; " +
                "INSERT INTO ProductFts(name, brand, barcode) VALUES (new.name, new.brand, new.barcode); END",
            "CREATE TRIGGER product_fts_update AFTER UPDATE ON ProductEntity BEGIN " +
                "DELETE FROM ProductFts WHERE barcode = old.barcode; " +
                "INSERT INTO ProductFts(name, brand, barcode) VALUES (new.name, new.brand, new.barcode); END",
            "CREATE TRIGGER product_fts_delete AFTER DELETE ON ProductEntity BEGIN " +
                "DELETE FROM ProductFts WHERE barcode = old.barcode; END",
            "INSERT INTO ProductEntity(barcode, name, source, cached_at) VALUES " +
                "('1', 'Pan integral', 'OPEN_FOOD_FACTS', 0), ('2', 'Queso fresco', 'GENERIC', 0)",
        )
        for (sql in v2) driver.execute(null, sql, 0)

        EasyFoodDatabase.Schema.migrate(driver, 2, EasyFoodDatabase.Schema.version)

        val db = EasyFoodDatabase(driver)
        val search = SqlDelightProductSearchStore(db.productSearchQueries)
        assertEquals(listOf("1"), search.search("integral").map { it.barcode })
        assertEquals(listOf("2"), search.search("queso").map { it.barcode })
        // Tras migrar, los triggers nuevos mantienen el índice.
        SqlDelightProductStore(db.productQueries).save(product("2", "Queso curado"), 1)
        assertEquals(emptyList(), search.search("fresco").map { it.barcode })
        assertEquals(listOf("2"), search.search("curado").map { it.barcode })
        assertEquals(driver.count("ProductEntity"), driver.count("ProductFts"))
        driver.close()
    }
}
