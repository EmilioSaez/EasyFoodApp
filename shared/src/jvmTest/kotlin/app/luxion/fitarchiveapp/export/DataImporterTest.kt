package app.luxion.fitarchiveapp.export

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.data.SqlDelightCustomFoodStore
import app.luxion.fitarchiveapp.data.SqlDelightDiaryStore
import app.luxion.fitarchiveapp.data.SqlDelightProductStore
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.NewCustomFood
import app.luxion.fitarchiveapp.model.NewDiaryEntry
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Product
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DataImporterTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: EasyFoodDatabase
    private lateinit var products: SqlDelightProductStore
    private lateinit var diary: SqlDelightDiaryStore
    private lateinit var foods: SqlDelightCustomFoodStore
    private lateinit var exporter: DataExporter
    private lateinit var importer: DataImporter

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(driver)
        db = EasyFoodDatabase(driver)
        products = SqlDelightProductStore(db.productQueries)
        diary = SqlDelightDiaryStore(db.diaryEntryQueries)
        foods = SqlDelightCustomFoodStore(db.customFoodQueries)
        exporter = DataExporter(SqlDelightExportSource(db.productQueries, db.diaryEntryQueries, db.customFoodQueries)) { 5_000L }
        importer = DataImporter(SqlDelightImportTarget(db.productQueries, db.diaryEntryQueries, db.customFoodQueries))
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
    private val pan = NewCustomFood("Pan casero", "Horno", Nutrients(250.0, 8.0, 50.0, 2.0, fiberG = 3.0), 40.0, "1 rebanada")

    private fun file(
        products: List<ExportedProduct> = emptyList(),
        diary: List<ExportedDiaryEntry> = emptyList(),
        foods: List<ExportedCustomFood> = emptyList(),
    ) = ExportCodec.encode(ExportFile(ExportCodec.CURRENT_VERSION, 1L, products, diary, foods))

    private fun exportedNutella(updated: Long = 100L, name: String = "Nutella") = ExportedProduct(
        barcode = nutella.barcode, name = name, nutrients = ExportedNutrients(kcal = 539.0), updatedAtMillis = updated,
    )

    private fun exportedPan(id: Long = 1, created: Long = 10, updated: Long = 10, kcal: Double = 250.0, name: String = "Pan casero") =
        ExportedCustomFood(id, name, "Horno", ExportedNutrients(kcal, 8.0, 50.0, 2.0, fiberG = 3.0), 40.0, "1 rebanada", created, updated)

    private fun exportedEntry(id: Long = 1, created: Long = 100, meal: MealType = MealType.LUNCH, quantity: Double = 100.0) =
        ExportedDiaryEntry(id, "2026-11-04", meal, null, "Arroz", quantity, QuantityUnit.GRAMS, null,
            ExportedNutrients(130.0, 2.7, 28.0, 0.3), created)

    @Test
    fun roundTripIntoEmptyDatabaseRestoresEverything() = runTest {
        products.save(nutella, 1_234L)
        diary.add(entry, 100L)
        foods.create(pan, 50L)
        val json = exporter.exportToJson()

        val other = EasyFoodDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { EasyFoodDatabase.Schema.create(it) })
        val report = DataImporter(SqlDelightImportTarget(other.productQueries, other.diaryEntryQueries, other.customFoodQueries))
            .import(json)

        assertEquals(ImportCounts(added = 1), report.products)
        assertEquals(ImportCounts(added = 1), report.customFoods)
        assertEquals(ImportCounts(added = 1), report.diaryEntries)
        val again = DataExporter(SqlDelightExportSource(other.productQueries, other.diaryEntryQueries, other.customFoodQueries)) { 5_000L }
        assertEquals(ExportCodec.decode(json), ExportCodec.decode(again.exportToJson()))
    }

    @Test
    fun importingTheSameFileTwiceIsIdempotent() = runTest {
        products.save(nutella, 1_234L)
        diary.add(entry, 100L)
        diary.add(entry, 100L) // dos entradas realmente idénticas siguen siendo dos
        foods.create(pan, 50L)
        val json = exporter.exportToJson()

        val report = importer.import(json)

        assertEquals(ImportCounts(unchanged = 1), report.products)
        assertEquals(ImportCounts(unchanged = 1), report.customFoods)
        assertEquals(ImportCounts(unchanged = 2), report.diaryEntries)
        assertEquals(json, exporter.exportToJson())
    }

    @Test
    fun productConflictNewerImportWinsOlderKeepsLocal() = runTest {
        products.save(nutella, 500L)

        val older = importer.import(file(products = listOf(exportedNutella(updated = 400L, name = "Viejo"))))
        assertEquals(ImportCounts(keptLocal = 1), older.products)
        assertEquals("Nutella", products.find(nutella.barcode)!!.product.name)

        val newer = importer.import(file(products = listOf(exportedNutella(updated = 600L, name = "Nuevo"))))
        assertEquals(ImportCounts(updated = 1), newer.products)
        assertEquals("Nuevo", products.find(nutella.barcode)!!.product.name)
        assertEquals(600L, products.find(nutella.barcode)!!.fetchedAtMillis)

        val tie = importer.import(file(products = listOf(exportedNutella(updated = 600L, name = "Empate"))))
        assertEquals(ImportCounts(keptLocal = 1), tie.products)
    }

    @Test
    fun productsKeepTheirSource() = runTest {
        importer.import(file(products = listOf(exportedNutella().copy(source = "GENERIC"))))
        assertEquals("GENERIC", db.productQueries.selectAll().executeAsList().single().source)
    }

    @Test
    fun customFoodMatchedByContentIgnoringIdAndCase() = runTest {
        foods.create(pan, 50L) // id local 1
        // Otro dispositivo: mismo alimento con id 77, nombre en otro caso y espacios.
        val sameFood = exportedPan(id = 77, created = 999, updated = 999, name = "  PAN CASERO ")

        val report = importer.import(file(foods = listOf(sameFood)))

        assertEquals(ImportCounts(unchanged = 1), report.customFoods)
        assertEquals(1, foods.all().size)
    }

    @Test
    fun customFoodSameIdDifferentContentIsADifferentFood() = runTest {
        foods.create(pan, 50L) // id local 1, createdAt 50
        val other = exportedPan(id = 1, created = 7, updated = 7, name = "Queso") // id coincide por casualidad

        val report = importer.import(file(foods = listOf(other)))

        assertEquals(ImportCounts(added = 1), report.customFoods)
        assertEquals(listOf("Pan casero", "Queso"), foods.all().map { it.name })
    }

    @Test
    fun customFoodRenamedLocallyIsRecognisedByIdAndCreationDate() = runTest {
        val id = foods.create(pan, 50L)
        foods.update(id, pan.copy(name = "Pan de centeno"), 80L)
        // Export antiguo del mismo alimento (mismo id y createdAt 50, aún con el nombre viejo).
        val old = exportedPan(id = id, created = 50, updated = 50)

        val report = importer.import(file(foods = listOf(old)))

        assertEquals(ImportCounts(keptLocal = 1), report.customFoods)
        assertEquals(listOf("Pan de centeno"), foods.all().map { it.name })
    }

    @Test
    fun customFoodConflictNewestWins() = runTest {
        val id = foods.create(pan, 50L)

        val newer = importer.import(file(foods = listOf(exportedPan(id = 9, created = 20, updated = 90, kcal = 300.0, name = "pan casero"))))

        assertEquals(ImportCounts(updated = 1), newer.customFoods)
        val updated = foods.find(id)!!
        assertEquals(300.0, updated.nutrients.kcal)
        assertEquals(50L, updated.createdAtMillis)
        assertEquals(90L, updated.updatedAtMillis)
        assertEquals(1, foods.all().size)

        val older = importer.import(file(foods = listOf(exportedPan(id = 9, created = 20, updated = 60, kcal = 100.0, name = "Pan casero"))))
        assertEquals(ImportCounts(keptLocal = 1), older.customFoods)
        assertEquals(300.0, foods.find(id)!!.nutrients.kcal)
    }

    @Test
    fun diaryDuplicateIgnoresIdAndEditedLocalEntryWins() = runTest {
        val id = diary.add(entry.copy(name = "Arroz", barcode = null, meal = MealType.LUNCH,
            quantity = Quantity(100.0, QuantityUnit.GRAMS), servingSizeG = null,
            nutrients = Nutrients(130.0, 2.7, 28.0, 0.3)), 100L)

        val sameWithOtherId = importer.import(file(diary = listOf(exportedEntry(id = 555))))
        assertEquals(ImportCounts(unchanged = 1), sameWithOtherId.diaryEntries)

        diary.update(id, MealType.DINNER, Quantity(250.0, QuantityUnit.GRAMS)) // el usuario la edita
        val conflict = importer.import(file(diary = listOf(exportedEntry())))
        assertEquals(ImportCounts(keptLocal = 1), conflict.diaryEntries)
        val local = diary.find(id)!!
        assertEquals(MealType.DINNER, local.meal)
        assertEquals(250.0, local.quantity.amount)
        assertEquals(1, diary.entriesForDate("2026-11-04").size)
    }

    @Test
    fun diaryEntryWithOtherCreationTimeIsAnotherEntry() = runTest {
        diary.add(entry, 100L)
        val report = importer.import(file(diary = listOf(entry.toExportedWithId(1, 101L))))
        assertEquals(ImportCounts(added = 1), report.diaryEntries)
        assertEquals(2, diary.entriesForDate("2026-11-04").size)
    }

    @Test
    fun invalidItemsAreSkippedAndReportedRestIsImported() = runTest {
        val json = """{"schemaVersion":1,"exportedAtMillis":1,
          "products":[{"barcode":"","name":"x"},{"barcode":"111","name":"Bueno"}],
          "diaryEntries":[
            {"id":1,"date":"2026-11-04","meal":"LUNCH","name":"neg","quantity":-3.0,"unit":"GRAMS","createdAtMillis":1},
            {"id":2,"date":"ayer","meal":"LUNCH","name":"fecha","quantity":3.0,"unit":"GRAMS","createdAtMillis":2},
            {"id":3,"date":"2026-11-04","meal":"LUNCH","name":"ok","quantity":3.0,"unit":"GRAMS","createdAtMillis":3}],
          "customFoods":[{"id":1,"name":"Sin macros"},{"id":2,"name":"Neg","nutrients":{"kcal":-1,"proteinG":1,"carbsG":1,"fatG":1}}]}"""

        val report = importer.import(json)

        assertEquals(ImportCounts(added = 1, invalid = 1), report.products)
        assertEquals(ImportCounts(added = 1, invalid = 2), report.diaryEntries)
        assertEquals(ImportCounts(invalid = 2), report.customFoods)
        assertEquals(5, report.invalidItems.size)
        assertEquals(listOf("ok"), diary.entriesForDate("2026-11-04").map { it.name })
        assertEquals(emptyList(), foods.all())
    }

    @Test
    fun legacyVersion1FileWithoutCustomFoodsImports() = runTest {
        val report = importer.import("""{"schemaVersion":1,"exportedAtMillis":1,"products":[{"barcode":"111","name":"Viejo"}]}""")
        assertEquals(ImportCounts(added = 1), report.products)
        assertEquals(ImportCounts(), report.customFoods)
    }

    @Test
    fun invalidFileFailsWithoutTouchingData() = runTest {
        foods.create(pan, 50L)
        assertFailsWith<ExportFormatException> { importer.import("""{"schemaVersion":99,"exportedAtMillis":1}""") }
        assertFailsWith<ExportFormatException> { importer.import("basura") }
        assertEquals(1, foods.all().size)
    }

    @Test
    fun previewDoesNotWrite() = runTest {
        val plan = importer.preview(file(foods = listOf(exportedPan()), diary = listOf(exportedEntry())))
        assertEquals(ImportCounts(added = 1), plan.report.customFoods)
        assertTrue(!plan.changes.isEmpty)
        assertEquals(emptyList(), foods.all())
        assertEquals(emptyList(), diary.entriesForDate("2026-11-04"))
    }

    @Test
    fun duplicatesInsideTheSameFileAreNotInsertedTwice() = runTest {
        val report = importer.import(file(
            products = listOf(exportedNutella(), exportedNutella()),
            foods = listOf(exportedPan(), exportedPan(id = 2)),
        ))
        assertEquals(ImportCounts(added = 1, unchanged = 1), report.products)
        assertEquals(ImportCounts(added = 1, unchanged = 1), report.customFoods)
    }

    private fun NewDiaryEntry.toExportedWithId(id: Long, created: Long) = ExportedDiaryEntry(
        id, date, meal, barcode, name, quantity.amount, quantity.unit, servingSizeG, nutrients.toExported(), created,
    )
}
