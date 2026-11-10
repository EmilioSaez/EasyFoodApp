package app.luxion.fitarchiveapp.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Product
import app.luxion.fitarchiveapp.model.ProductCorrection
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

/** Correcciones del usuario sobre los valores de un producto: sobreviven al refresco y se pueden revertir. */
class ProductCorrectionTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: EasyFoodDatabase
    private lateinit var store: SqlDelightProductStore
    private lateinit var search: SqlDelightProductSearchStore

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(driver)
        db = EasyFoodDatabase(driver)
        store = SqlDelightProductStore(db.productQueries)
        search = SqlDelightProductSearchStore(db.productSearchQueries)
    }

    @AfterTest
    fun tearDown() = driver.close()

    private val original = Nutrients(900.0, 6.0, 57.0, 30.0)
    private val fixed = Nutrients(539.0, 6.3, 57.5, 30.9, saltG = 0.1)
    private val product = Product("3017620422003", "Nutella", "Ferrero", null, original)

    @Test
    fun correctedProductShowsFixedValuesAndKeepsTheOriginal() = runTest {
        store.save(product, 1L)
        store.saveCorrection(ProductCorrection(product.barcode, fixed, 5L))

        val p = store.find(product.barcode)!!.product
        assertEquals(fixed, p.nutrients)
        assertEquals(original, p.originalNutrients)
        assertTrue(p.isCorrected)
        assertEquals("Nutella", p.name)
    }

    @Test
    fun uncorrectedProductIsUntouched() = runTest {
        store.save(product, 1L)
        assertEquals(product, store.find(product.barcode)!!.product)
        assertFalse(store.find(product.barcode)!!.product.isCorrected)
    }

    @Test
    fun refreshingTheProductFromOffDoesNotOverwriteTheCorrection() = runTest {
        store.save(product, 1L)
        store.saveCorrection(ProductCorrection(product.barcode, fixed, 5L))

        store.save(product.copy(nutrients = Nutrients(880.0, 1.0, 1.0, 1.0)), 99L) // OFF cambia sus datos

        val p = store.find(product.barcode)!!.product
        assertEquals(fixed, p.nutrients)
        assertEquals(Nutrients(880.0, 1.0, 1.0, 1.0), p.originalNutrients)
    }

    @Test
    fun revertingShowsTheCurrentOriginalAgain() = runTest {
        store.save(product, 1L)
        store.saveCorrection(ProductCorrection(product.barcode, fixed, 5L))
        store.clearCorrection(product.barcode)

        assertEquals(product, store.find(product.barcode)!!.product)
    }

    @Test
    fun correctingTwiceReplacesTheFirstCorrection() = runTest {
        store.save(product, 1L)
        store.saveCorrection(ProductCorrection(product.barcode, fixed, 5L))
        val second = fixed.copy(kcal = 540.0)
        store.saveCorrection(ProductCorrection(product.barcode, second, 6L))

        assertEquals(second, store.find(product.barcode)!!.product.nutrients)
        assertEquals(original, store.find(product.barcode)!!.product.originalNutrients)
    }

    @Test
    fun searchResultsCarryTheCorrection() = runTest {
        store.save(product, 1L)
        store.save(Product("2", "Nutella light", null, null, Nutrients(300.0, 1.0, 1.0, 1.0)), 1L)
        store.saveCorrection(ProductCorrection(product.barcode, fixed, 5L))

        val results = search.search("nutella").associateBy { it.barcode }
        assertEquals(fixed, results.getValue(product.barcode).nutrients)
        assertNull(results.getValue("2").originalNutrients)
    }

    @Test
    fun genericFoodsCanBeCorrectedToo() = runTest {
        val generic = Product("generic:7", "Arroz", null, null, original)
        store.save(generic, 1L)
        store.saveCorrection(ProductCorrection("generic:7", fixed, 5L))
        assertEquals(fixed, store.find("generic:7")!!.product.nutrients)
    }

    @Test
    fun invalidCorrectionsAreRejected() {
        assertFailsWith<IllegalArgumentException> { ProductCorrection("1", Nutrients(null, 1.0, 1.0, 1.0)) }
        assertFailsWith<IllegalArgumentException> { ProductCorrection("1", Nutrients(100.0, -1.0, 1.0, 1.0)) }
        assertFailsWith<IllegalArgumentException> { ProductCorrection("1", Nutrients(1000.0, 1.0, 1.0, 1.0)) }
        assertFailsWith<IllegalArgumentException> { ProductCorrection("1", Nutrients(100.0, 101.0, 1.0, 1.0)) }
        assertFailsWith<IllegalArgumentException> { ProductCorrection(" ", fixed) }
    }

    @Test
    fun repositoryAppliesTheCorrectionToAFreshDownload() = runTest {
        val fresh = Nutrients(870.0, 2.0, 2.0, 2.0)
        val remote = object : ProductRemoteSource {
            override suspend fun findByBarcode(barcode: String) = ProductLookup.Found(product.copy(nutrients = fresh))
        }
        val repo = ProductRepository(remote, store, ttl = 30.days, nowMillis = { 100L })
        store.saveCorrection(ProductCorrection(product.barcode, fixed, 5L)) // producto aún no cacheado

        val result = repo.getByBarcode(product.barcode) as ProductResult.Found

        assertFalse(result.fromCache)
        assertEquals(fixed, result.product.nutrients)
        assertEquals(fresh, result.product.originalNutrients)
        // y revertir desde el repositorio devuelve el original en la siguiente lectura
        repo.revertCorrection(product.barcode)
        assertEquals(fresh, (repo.getByBarcode(product.barcode) as ProductResult.Found).product.nutrients)
    }

    @Test
    fun manualRefreshKeepsTheCorrectionAndJudgesChangesAgainstTheSourceValues() = runTest {
        store.save(product, 1L) // OFF: 900 kcal (dato erróneo)
        store.saveCorrection(ProductCorrection(product.barcode, fixed, 5L))
        val newer = Nutrients(539.0, 6.3, 57.5, 30.9) // OFF corrige su propio dato
        val remote = object : ProductRemoteSource {
            override suspend fun findByBarcode(barcode: String) = ProductLookup.Found(product.copy(nutrients = newer))
        }
        val repo = ProductRepository(remote, store, ttl = 30.days, nowMillis = { 100L })

        val result = repo.refresh(product.barcode) as RefreshResult.Updated

        // lo que se muestra sigue siendo la corrección; el original pasa a ser el nuevo dato de OFF
        assertEquals(fixed, result.product.nutrients)
        assertEquals(newer, result.product.originalNutrients)
        val stored = store.find(product.barcode)!!.product
        assertEquals(fixed, stored.nutrients)
        assertEquals(newer, stored.originalNutrients)
        // el cambio se calcula contra el valor de la fuente (900 → 539 kcal), no contra la corrección
        assertTrue(result.change != null)
    }
}
