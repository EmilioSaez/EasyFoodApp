package app.luxion.fitarchiveapp.export

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.data.SqlDelightDiaryStore
import app.luxion.fitarchiveapp.data.generic.GenericFoodImporter
import app.luxion.fitarchiveapp.data.SqlDelightProductStore
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.NewDiaryEntry
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Product
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DataExporterTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: EasyFoodDatabase
    private lateinit var products: SqlDelightProductStore
    private lateinit var diary: SqlDelightDiaryStore
    private lateinit var exporter: DataExporter

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(driver)
        db = EasyFoodDatabase(driver)
        products = SqlDelightProductStore(db.productQueries)
        diary = SqlDelightDiaryStore(db.diaryEntryQueries)
        exporter = DataExporter(SqlDelightExportSource(db.productQueries, db.diaryEntryQueries)) { 5_000L }
    }

    @AfterTest
    fun tearDown() = driver.close()

    private val nutella = Product(
        barcode = "3017620422003", name = "Nutella", brand = "Ferrero", imageUrl = "https://x/y.jpg",
        nutrients = Nutrients(539.0, 6.3, 57.5, 30.9, 10.6, 56.3, null, 0.107),
        servingSizeG = 15.0, servingLabel = "15 g",
    )
    private val entry = NewDiaryEntry(
        date = "2026-11-04", meal = MealType.BREAKFAST, barcode = nutella.barcode, name = "Nutella",
        quantity = Quantity(2.0, QuantityUnit.SERVINGS), servingSizeG = 15.0,
        nutrients = Nutrients(539.0, 6.3, 57.5, 30.9),
    )

    @Test
    fun emptyDatabaseExportsEmptyFileWithVersion() = runTest {
        val file = ExportCodec.decode(exporter.exportToJson())
        assertEquals(ExportCodec.CURRENT_VERSION, file.schemaVersion)
        assertEquals(5_000L, file.exportedAtMillis)
        assertEquals(emptyList(), file.products)
        assertEquals(emptyList(), file.diaryEntries)
    }

    @Test
    fun jsonContainsSchemaVersion() = runTest {
        assertContains(exporter.exportToJson(), "\"schemaVersion\": 1")
    }

    @Test
    fun roundTripsProductsAndDiary() = runTest {
        products.save(nutella, 1_234L)
        products.save(Product("8410000000001", "Sin datos", null, null, Nutrients(null, null, null, null)), 7L)
        val id1 = diary.add(entry, 100L)
        val id2 = diary.add(entry.copy(date = "2026-11-03", meal = MealType.DINNER, barcode = null,
            quantity = Quantity(120.0, QuantityUnit.GRAMS), servingSizeG = null,
            nutrients = Nutrients(null, null, null, null)), 200L)

        val file = ExportCodec.decode(exporter.exportToJson())

        assertEquals(
            listOf(nutella, Product("8410000000001", "Sin datos", null, null, Nutrients(null, null, null, null))).sortedBy { it.name },
            file.products.map { it.toCachedProduct().product },
        )
        assertEquals(listOf(1_234L), file.products.filter { it.barcode == nutella.barcode }.map { it.updatedAtMillis })
        assertEquals(listOf(diary.find(id2)!!, diary.find(id1)!!), file.diaryEntries.map { it.toDomain() })
    }

    @Test
    fun excludesEmbeddedGenericFoodsButKeepsOtherSources() = runTest {
        products.save(nutella, 1L)
        // La base genérica se siembra en cada dispositivo: no debe viajar en la exportación.
        val tsv = listOf(
            "#easyfood-generic-foods v1",
            listOf("id", "name", "kcal", "protein_g", "carbs_g", "fat_g", "saturated_fat_g", "sugars_g", "fiber_g", "salt_g").joinToString("\t"),
            listOf("1", "Apple", "52", "0.3", "14", "0.2", "", "10", "2.4", "0").joinToString("\t"),
        ).joinToString("\n")
        GenericFoodImporter(db.productQueries).import(tsv, 9L)
        assertEquals(1, GenericFoodImporter(db.productQueries).importedCount().toInt())

        val file = ExportCodec.decode(exporter.exportToJson())

        assertEquals(listOf(nutella.barcode), file.products.map { it.barcode })
    }

    @Test
    fun ignoresUnknownFieldsAndMissingCollections() {
        val file = ExportCodec.decode("""{"schemaVersion":1,"exportedAtMillis":9,"goals":{"kcal":2000}}""")
        assertEquals(9L, file.exportedAtMillis)
        assertEquals(emptyList(), file.products)
    }

    @Test
    fun rejectsFutureVersion() {
        val e = assertFailsWith<ExportFormatException> {
            ExportCodec.decode("""{"schemaVersion":99,"exportedAtMillis":1,"products":[{"weird":true}]}""")
        }
        assertContains(e.message!!, "99")
    }

    @Test
    fun rejectsMissingVersionAndGarbage() {
        assertFailsWith<ExportFormatException> { ExportCodec.decode("""{"exportedAtMillis":1}""") }
        assertFailsWith<ExportFormatException> { ExportCodec.decode("no es json") }
        assertFailsWith<ExportFormatException> { ExportCodec.decode("""{"schemaVersion":1}""") }
    }

    @Test
    fun rejectsInvalidEntryQuantity() {
        val json = """{"schemaVersion":1,"exportedAtMillis":1,"diaryEntries":[{"id":1,"date":"2026-11-04",
            "meal":"LUNCH","name":"x","quantity":-3.0,"unit":"GRAMS","createdAtMillis":1}]}"""
        val file = ExportCodec.decode(json)
        assertFailsWith<IllegalArgumentException> { file.diaryEntries.single().toDomain() }
    }
}
