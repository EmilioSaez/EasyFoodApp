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
import kotlin.time.Duration.Companion.days

class SqlDelightProductStoreTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var store: SqlDelightProductStore

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(driver)
        store = SqlDelightProductStore(EasyFoodDatabase(driver).productQueries)
    }

    @AfterTest
    fun tearDown() = driver.close()

    private val full = Product(
        barcode = "3017620422003", name = "Nutella", brand = "Ferrero", imageUrl = "http://img",
        nutrients = Nutrients(539.0, 6.3, 57.5, 30.9, saturatedFatG = 10.6, sugarsG = 56.3, fiberG = null, saltG = 0.107),
        servingSizeG = 15.0, servingLabel = "1 cucharada",
    )

    @Test
    fun roundTripsAllFields() = runTest {
        store.save(full, 1234L)
        assertEquals(CachedProduct(full, 1234L), store.find(full.barcode))
    }

    @Test
    fun roundTripsNullFields() = runTest {
        val minimal = Product("1", "Mínimo", null, null, Nutrients(null, null, null, null))
        store.save(minimal, 1L)
        assertEquals(CachedProduct(minimal, 1L), store.find("1"))
    }

    @Test
    fun missingProductIsNull() = runTest {
        assertNull(store.find("0"))
    }

    @Test
    fun saveOverwritesAndRefreshesTimestamp() = runTest {
        store.save(full, 1L)
        store.save(full.copy(name = "Nutella 2"), 2L)
        assertEquals(CachedProduct(full.copy(name = "Nutella 2"), 2L), store.find(full.barcode))
    }

    @Test
    fun repositoryWorksWithRealDatabase() = runTest {
        val remote = object : ProductRemoteSource {
            var calls = 0
            override suspend fun findByBarcode(barcode: String): ProductLookup {
                calls++
                return ProductLookup.Found(full)
            }
        }
        val now = 10L * 24 * 60 * 60 * 1000
        val repo = ProductRepository(remote, store, ttl = 30.days, nowMillis = { now })
        assertEquals(false, (repo.getByBarcode(full.barcode) as ProductResult.Found).fromCache)
        assertEquals(true, (repo.getByBarcode(full.barcode) as ProductResult.Found).fromCache)
        assertEquals(1, remote.calls)
    }
}
