package app.luxion.easyfoodapp.data.supermarket

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.easyfoodapp.data.SqlDelightProductSearchStore
import app.luxion.easyfoodapp.data.SqlDelightProductStore
import app.luxion.easyfoodapp.data.generic.GenericSeedState
import app.luxion.easyfoodapp.db.EasyFoodDatabase
import app.luxion.easyfoodapp.model.Nutrients
import app.luxion.easyfoodapp.model.Product
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SupermarketCatalogTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: EasyFoodDatabase
    private lateinit var importer: SupermarketImporter
    private lateinit var products: SqlDelightProductStore
    private lateinit var search: SqlDelightProductSearchStore

    private fun tsv(vararg rows: String) = buildString {
        appendLine(SupermarketFormat.MAGIC)
        appendLine("# comentario")
        appendLine(SupermarketFormat.COLUMNS.joinToString("\t"))
        rows.forEach { appendLine(it) }
    }

    private val sample = tsv(
        "8480000000011\tLeche entera\tHacendado\t64\t3.1\t4.7\t3.6\t2.3\t4.7\t\t0.1",
        "8480000000028\tYogur natural\tDia\t60\t4\t5\t3\t\t\t\t",
        "malo\tCódigo no numérico\tX\t1\t1\t1\t1\t\t\t\t",
        "8480000000035\tSin kcal\tLidl\t\t1\t1\t1\t\t\t\t",
        "8480000000042\tColumnas de menos\tLidl\t5",
    )

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(driver)
        db = EasyFoodDatabase(driver)
        importer = SupermarketImporter(db.productQueries)
        products = SqlDelightProductStore(db.productQueries)
        search = SqlDelightProductSearchStore(db.productSearchQueries)
    }

    @AfterTest
    fun tearDown() = driver.close()

    @Test
    fun parsesValidRowsAndSkipsMalformedOnes() {
        val parsed = SupermarketFormat.parse(sample)
        assertEquals(listOf("8480000000011", "8480000000028"), parsed.products.map { it.barcode })
        assertEquals(3, parsed.skipped)
        val milk = parsed.products.first()
        assertEquals("Hacendado", milk.brand)
        assertEquals(Nutrients(64.0, 3.1, 4.7, 3.6, 2.3, 4.7, null, 0.1), milk.nutrients)
    }

    @Test
    fun rejectsUnknownFormat() {
        assertFailsWith<IllegalArgumentException> { SupermarketFormat.parse("otra cosa\n") }
        assertFailsWith<IllegalArgumentException> { SupermarketFormat.parse(SupermarketFormat.MAGIC + "\nx\ty\n") }
    }

    @Test
    fun importStoresProductsWithSupermarketSourceAndIndexesThem() = runTest {
        val result = importer.import(sample, nowMillis = 7)
        assertEquals(SupermarketImportResult(imported = 2, skipped = 3), result)
        assertEquals(2, importer.importedCount())
        val cached = products.find("8480000000011")!!
        assertTrue(cached.product.fromCatalog)
        assertEquals(7, cached.fetchedAtMillis)
        assertEquals(listOf("Leche entera"), search.search("leche").map { it.name })
        assertEquals(listOf("Yogur natural"), search.search("dia yogur").map { it.name })
    }

    @Test
    fun importNeverOverwritesAnExistingProduct() = runTest {
        products.save(Product("8480000000011", "Mi leche", "Otra", null, Nutrients(1.0, 0.0, 0.0, 0.0)), 1)
        importer.import(sample, nowMillis = 7)
        val kept = products.find("8480000000011")!!
        assertEquals("Mi leche", kept.product.name)
        assertFalse(kept.product.fromCatalog)
        assertEquals(1, importer.importedCount()) // solo el yogur
    }

    @Test
    fun catalogIsRankedAboveOtherSourcesForTheSameQuery() = runTest {
        products.save(Product("111", "Leche entera", "Marca", null, Nutrients(60.0, 3.0, 5.0, 3.0)), 1)
        importer.import(tsv("8480000000011\tLeche entera\tHacendado\t64\t3\t5\t3\t\t\t\t"), nowMillis = 2)
        assertEquals(listOf("8480000000011", "111"), search.search("leche").map { it.barcode })
    }

    @Test
    fun catalogCountsAsEmbeddedNotAsUserProductsInOnlyMineSearch() = runTest {
        importer.import(sample, nowMillis = 2)
        products.save(Product("111", "Leche de avena", null, null, Nutrients(40.0, 1.0, 6.0, 1.0)), 1)
        assertEquals(listOf("Leche de avena"), search.search("leche", 20, onlyMine = true).map { it.name })
    }

    @Test
    fun seederLoadsOnceAndSkipsWhenAlreadyLoaded() = runTest {
        var reads = 0
        val seeder = SupermarketSeeder(importer, nowMillis = { 5 }, readResource = { reads++; sample })
        assertEquals(GenericSeedState.Idle, seeder.state.value)
        seeder.ensureSeeded()
        assertEquals(GenericSeedState.Done, seeder.state.value)
        assertEquals(2, importer.importedCount())
        SupermarketSeeder(importer, nowMillis = { 5 }, readResource = { reads++; sample }).ensureSeeded()
        assertEquals(1, reads)
    }

    @Test
    fun seederReportsFailureAndCanRetry() = runTest {
        var ok = false
        val seeder = SupermarketSeeder(importer, nowMillis = { 5 }, readResource = { if (ok) sample else error("sin recurso") })
        seeder.ensureSeeded()
        assertIs<GenericSeedState.Failed>(seeder.state.value)
        assertEquals(0, importer.importedCount())
        ok = true
        seeder.ensureSeeded()
        assertEquals(GenericSeedState.Done, seeder.state.value)
    }

    @Test
    fun embeddedResourceImportsAndIsSearchable() = runTest {
        val result = importer.import(readEmbeddedSupermarketProducts(), nowMillis = 1)
        assertTrue(result.imported > 10_000, "catálogo demasiado pequeño: ${result.imported}")
        assertTrue(result.skipped == 0, "filas mal formadas: ${result.skipped}")
        assertTrue(search.search("hacendado leche").isNotEmpty())
        assertTrue(search.search("lidl").isNotEmpty())
    }
}
