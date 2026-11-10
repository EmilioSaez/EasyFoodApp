package app.luxion.fitarchiveapp.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Product
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SqlDelightFavoriteStoreTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: EasyFoodDatabase
    private lateinit var store: SqlDelightFavoriteStore

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(driver)
        db = EasyFoodDatabase(driver)
        store = SqlDelightFavoriteStore(db.favoriteFoodQueries)
    }

    @AfterTest
    fun tearDown() = driver.close()

    private fun product(barcode: String, name: String) =
        Product(barcode, name, null, null, Nutrients(100.0, 1.0, 1.0, 1.0))

    @Test
    fun startsEmpty() = runTest {
        assertEquals(emptyList(), store.keys())
        assertEquals(emptyList(), store.products())
    }

    @Test
    fun marksAndUnmarks() = runTest {
        store.setFavorite("123", true, 10L)
        store.setFavorite("custom:4", true, 20L)
        assertEquals(listOf("custom:4", "123"), store.keys()) // la más reciente primero
        store.setFavorite("123", false, 30L)
        assertEquals(listOf("custom:4"), store.keys())
        store.setFavorite("123", false, 40L) // desmarcar lo no marcado no falla
        assertEquals(listOf("custom:4"), store.keys())
    }

    @Test
    fun markingTwiceKeepsASingleRowAndTheFirstDate() = runTest {
        store.setFavorite("123", true, 10L)
        store.setFavorite("123", true, 99L)
        assertEquals(listOf("123"), store.keys())
        assertEquals(10L, db.favoriteFoodQueries.selectAll().executeAsOne().created_at)
    }

    @Test
    fun blankKeyIsRejected() = runTest {
        assertFailsWith<IllegalArgumentException> { store.setFavorite("  ", true, 1L) }
    }

    @Test
    fun productsComeOnlyFromTheCacheAndOnlyIfFavorite() = runTest {
        val products = SqlDelightProductStore(db.productQueries)
        products.save(product("1", "Galletas"), 1L)
        products.save(product("2", "Leche"), 1L)
        products.save(product("generic:7", "Manzana"), 1L)
        store.setFavorite("2", true, 5L)
        store.setFavorite("generic:7", true, 6L)
        store.setFavorite("999", true, 7L) // no está en la caché
        store.setFavorite("custom:3", true, 8L) // propio: no sale en products()
        assertEquals(listOf("generic:7", "2"), store.products().map { it.barcode })
        assertEquals(4, store.keys().size)
    }

    @Test
    fun favoriteSurvivesCachePurge() = runTest {
        SqlDelightProductStore(db.productQueries).save(product("2", "Leche"), 1L)
        store.setFavorite("2", true, 5L)
        db.productQueries.deleteByBarcode("2")
        assertEquals(emptyList(), store.products())
        assertEquals(listOf("2"), store.keys())
    }

    @Test
    fun migrationFromV9CreatesTheTableIdempotentlyAndKeepsData() = runTest {
        // Estado v9: sin tabla de favoritos.
        driver.execute(null, "DROP TABLE FavoriteFoodEntity", 0)
        SqlDelightProductStore(db.productQueries).save(product("2", "Leche"), 1L)

        driver.execute(null, "ALTER TABLE DiaryEntryEntity DROP COLUMN quick", 0) // la añade 12.sqm (no es idempotente)
        EasyFoodDatabase.Schema.migrate(driver, 9, EasyFoodDatabase.Schema.version)
        driver.execute(null, "ALTER TABLE DiaryEntryEntity DROP COLUMN quick", 0)
        EasyFoodDatabase.Schema.migrate(driver, 9, EasyFoodDatabase.Schema.version)

        assertTrue(EasyFoodDatabase.Schema.version >= 10)
        val migrated = SqlDelightFavoriteStore(EasyFoodDatabase(driver).favoriteFoodQueries)
        assertEquals(emptyList(), migrated.keys())
        migrated.setFavorite("2", true, 3L)
        assertEquals(listOf("Leche"), migrated.products().map { it.name })
        assertEquals(1L, driver.executeQuery(
            null, "SELECT COUNT(*) FROM ProductEntity",
            { c -> QueryResult.Value(if (c.next().value) c.getLong(0) else null) }, 0,
        ).value)
    }
}
