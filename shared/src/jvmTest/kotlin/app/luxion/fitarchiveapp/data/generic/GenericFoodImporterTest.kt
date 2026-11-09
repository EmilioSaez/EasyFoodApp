package app.luxion.fitarchiveapp.data.generic

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.data.SqlDelightProductSearchStore
import app.luxion.fitarchiveapp.data.SqlDelightProductStore
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Product
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GenericFoodImporterTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: EasyFoodDatabase
    private lateinit var importer: GenericFoodImporter
    private lateinit var search: SqlDelightProductSearchStore

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(driver)
        db = EasyFoodDatabase(driver)
        importer = GenericFoodImporter(db.productQueries)
        search = SqlDelightProductSearchStore(db.productSearchQueries)
    }

    @AfterTest
    fun tearDown() = driver.close()

    private val sample = listOf(
        GenericFoodFormat.MAGIC,
        GenericFoodFormat.COLUMNS.joinToString("\t"),
        "1\tApples, raw, with skin\t52\t0.26\t13.8\t0.17\t0.03\t10.4\t2.4\t0",
        "2\tBread, wheat\t266\t10.7\t49.2\t3.3\t\t5.7\t2.7\t1.3",
    ).joinToString("\n")

    @Test
    fun importsWithGenericSourceAndIndexesForSearch() = runTest {
        val result = importer.import(sample, nowMillis = 1_000)
        assertEquals(GenericFoodImportResult(imported = 2, skipped = 0), result)
        assertEquals(2, importer.importedCount())
        assertEquals(listOf("Apples, raw, with skin"), search.search("apple").map { it.name })
        val stored = db.productQueries.selectByBarcode("generic:2").executeAsOne()
        assertEquals("GENERIC", stored.source)
        assertEquals(266.0, stored.kcal)
        assertNull(stored.saturated_fat_g)
    }

    @Test
    fun reimportIsIdempotentAndKeepsSearchIndexClean() = runTest {
        importer.import(sample, 1)
        importer.import(sample.replace("Bread, wheat", "Bread, whole"), 2)
        assertEquals(2, importer.importedCount())
        assertEquals(emptyList(), search.search("wheat").map { it.name })
        assertEquals(listOf("Bread, whole"), search.search("whole").map { it.name })
    }

    @Test
    fun doesNotTouchOtherSources() = runTest {
        SqlDelightProductStore(db.productQueries).save(
            Product("8410000000000", "Apple juice", "Brand", null, Nutrients(45.0, 0.1, 11.0, 0.1)), 0,
        )
        importer.import(sample, 1)
        assertEquals(1, db.productQueries.countBySource("OPEN_FOOD_FACTS").executeAsOne())
        assertEquals(2, importer.importedCount())
    }

    @Test
    fun embeddedResourceImportsAndIsSearchable() = runTest {
        val text = readEmbeddedGenericFoods()
        val result = importer.import(text, 0)
        assertEquals(0, result.skipped)
        assertTrue(result.imported > 5_000, "imported=${result.imported}")
        assertTrue(search.search("apples raw").isNotEmpty())
        assertTrue(text.length < 2_000_000)
    }

    private val translations = listOf(
        GenericFoodFormat.TRANSLATION_MAGIC,
        GenericFoodFormat.TRANSLATION_COLUMNS.joinToString("\t"),
        "1\tManzanas, crudas, con piel",
        "2\tPan, de trigo",
    ).joinToString("\n")

    @Test
    fun importsSpanishNamesAndSearchesWithoutAccents() = runTest {
        importer.import(sample, 1, translations)
        assertEquals("Manzanas, crudas, con piel", db.productQueries.selectByBarcode("generic:1").executeAsOne().name)
        assertEquals(listOf("Manzanas, crudas, con piel"), search.search("manzana").map { it.name })
        assertEquals(listOf("Pan, de trigo"), search.search("TRIGO pán").map { it.name })
        assertEquals(emptyList(), search.search("apples").map { it.name })
    }

    @Test
    fun reimportWithTranslationsUpdatesExistingEnglishRowsAndIndex() = runTest {
        importer.import(sample, 1)
        assertEquals(GenericFoodFormat.DATA_VERSION, importer.importedDataVersion())
        assertEquals(listOf("Bread, wheat"), search.search("bread").map { it.name })
        importer.import(sample, 2, translations)
        assertEquals(2, importer.importedCount())
        assertEquals(emptyList(), search.search("bread").map { it.name })
        assertEquals(listOf("Pan, de trigo"), search.search("pan").map { it.name })
    }

    @Test
    fun dataVersionIsZeroUntilFirstImport() = runTest {
        assertEquals(0, importer.importedDataVersion())
        importer.import(sample, 1)
        assertEquals(GenericFoodFormat.DATA_VERSION, importer.importedDataVersion())
    }

    @Test
    fun embeddedTranslationCoversEveryFoodAndFindsSpanishNames() = runTest {
        val foods = GenericFoodFormat.parse(readEmbeddedGenericFoods()).foods
        val names = GenericFoodFormat.parseTranslations(readEmbeddedGenericTranslations())
        assertEquals(foods.map { it.id }.toSet(), names.keys, "toda fila debe estar traducida y no sobrar ids")
        val untranslated = foods.filter { names[it.id] == it.name && it.name.any(Char::isLowerCase) && it.name.contains(", ") }
        assertTrue(untranslated.size < 100, "nombres sin traducir (marcas y préstamos aparte): ${untranslated.take(5)}")
        importer.import(readEmbeddedGenericFoods(), 0, readEmbeddedGenericTranslations())
        assertTrue(search.search("manzana cruda").isNotEmpty())
        assertTrue(search.search("platano").isNotEmpty())
        assertTrue(search.search("pechuga pollo").isNotEmpty())
    }
}
