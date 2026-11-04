package app.luxion.fitarchiveapp.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Product
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SqlDelightProductSearchStoreTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: EasyFoodDatabase
    private lateinit var products: SqlDelightProductStore
    private lateinit var search: SqlDelightProductSearchStore

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(driver)
        db = EasyFoodDatabase(driver)
        products = SqlDelightProductStore(db.productQueries)
        search = SqlDelightProductSearchStore(db.productSearchQueries)
    }

    @AfterTest
    fun tearDown() = driver.close()

    private fun product(barcode: String, name: String, brand: String? = null) =
        Product(barcode, name, brand, null, Nutrients(100.0, 1.0, 1.0, 1.0))

    private suspend fun names(text: String) = search.search(text).map { it.name }

    @Test
    fun findsByNameAndBrand() = runTest {
        products.save(product("1", "Leche entera", "Hacendado"), 0)
        products.save(product("2", "Nutella", "Ferrero"), 0)
        assertEquals(listOf("Leche entera"), names("leche"))
        assertEquals(listOf("Nutella"), names("ferrero"))
    }

    @Test
    fun matchesPrefixesIgnoringCaseAndAccents() = runTest {
        products.save(product("1", "Galletas María", "Cuétara"), 0)
        assertEquals(listOf("Galletas María"), names("MARIA"))
        assertEquals(listOf("Galletas María"), names("gall"))
        assertEquals(listOf("Galletas María"), names("cuetara galle"))
    }

    @Test
    fun allTermsMustMatch() = runTest {
        products.save(product("1", "Leche entera"), 0)
        products.save(product("2", "Leche desnatada"), 0)
        assertEquals(listOf("Leche desnatada"), names("leche desnatada"))
    }

    @Test
    fun blankOrSymbolOnlyQueryReturnsEmpty() = runTest {
        products.save(product("1", "Leche"), 0)
        assertEquals(emptyList(), names("   "))
        assertEquals(emptyList(), names("\"*()-"))
    }

    @Test
    fun ftsSyntaxInInputDoesNotBreakTheQuery() = runTest {
        products.save(product("1", "Leche"), 0)
        // Los operadores FTS se tratan como palabras literales: no hay error de sintaxis ni coincidencias falsas.
        assertEquals(emptyList(), names("leche AND OR NOT \"(leche"))
        assertEquals(listOf("Leche"), names("\"(leche)*"))
    }

    @Test
    fun reSavingReplacesIndexedTextWithoutDuplicates() = runTest {
        products.save(product("1", "Leche entera"), 0)
        products.save(product("1", "Bebida de avena"), 1) // INSERT OR REPLACE
        assertEquals(emptyList(), names("leche"))
        assertEquals(listOf("Bebida de avena"), names("avena"))
    }

    @Test
    fun deletingProductRemovesItFromIndex() = runTest {
        products.save(product("1", "Leche"), 0)
        db.productQueries.deleteByBarcode("1")
        assertEquals(emptyList(), names("leche"))
        assertNull(products.find("1"))
    }

    @Test
    fun updatingProductReindexes() = runTest {
        products.save(product("1", "Leche"), 0)
        driver.execute(null, "UPDATE ProductEntity SET name = 'Yogur' WHERE barcode = '1'", 0)
        assertEquals(emptyList(), names("leche"))
        assertEquals(listOf("Yogur"), names("yogur"))
    }

    @Test
    fun nameMatchesRankAboveBrandMatches() = runTest {
        products.save(product("1", "Chocolate negro", "Valor"), 0)
        products.save(product("2", "Galletas", "Chocolate Marca"), 0)
        assertEquals(listOf("Chocolate negro", "Galletas"), names("chocolate"))
    }

    @Test
    fun respectsLimit() = runTest {
        repeat(5) { products.save(product("$it", "Leche $it"), 0) }
        assertEquals(2, search.search("leche", limit = 2).size)
    }

    @Test
    fun migrationBackfillsExistingProducts() = runTest {
        val old = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        // Esquema v1 (sin FTS): se reconstruye la tabla y se aplica la migración 1 -> 2.
        old.execute(null, "CREATE TABLE ProductEntity (barcode TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, brand TEXT, image_url TEXT, kcal REAL, protein_g REAL, carbs_g REAL, sugars_g REAL, fat_g REAL, saturated_fat_g REAL, fiber_g REAL, salt_g REAL, serving_size_g REAL, serving_label TEXT, source TEXT NOT NULL DEFAULT 'OPEN_FOOD_FACTS', cached_at INTEGER NOT NULL)", 0)
        old.execute(null, "INSERT INTO ProductEntity(barcode, name, brand, cached_at) VALUES ('9', 'Queso fresco', 'Pascual', 0)", 0)
        EasyFoodDatabase.Schema.migrate(old, 1, EasyFoodDatabase.Schema.version)
        val migrated = SqlDelightProductSearchStore(EasyFoodDatabase(old).productSearchQueries)
        assertEquals(listOf("Queso fresco"), migrated.search("queso").map { it.name })
        old.close()
    }
}
