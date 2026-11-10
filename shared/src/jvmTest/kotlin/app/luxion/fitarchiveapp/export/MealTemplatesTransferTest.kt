package app.luxion.fitarchiveapp.export

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.data.SqlDelightMealTemplateStore
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.model.NewMealTemplate
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import app.luxion.fitarchiveapp.model.TemplateItem
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Plantillas de comida en exportar e importar (docs/design.md §2.17): colección opcional, fusión sin duplicados. */
class MealTemplatesTransferTest {
    private class Device {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { EasyFoodDatabase.Schema.create(it) }
        val db = EasyFoodDatabase(driver)
        val templates = SqlDelightMealTemplateStore(db.mealTemplateQueries)
        val target = SqlDelightImportTarget(
            db.productQueries, db.diaryEntryQueries, db.customFoodQueries, db.weightEntryQueries,
            db.recipeQueries, db.settingsQueries, db.favoriteFoodQueries, templateQueries = db.mealTemplateQueries,
        )
        val exporter = DataExporter(target) { 5_000L }
        val importer = DataImporter(target)
    }

    private val n = Nutrients(100.0, 1.0, 1.0, 1.0)
    private fun item(name: String, amount: Double = 50.0, unit: QuantityUnit = QuantityUnit.GRAMS, barcode: String? = "111") =
        TemplateItem(barcode, name, Quantity(amount, unit), if (unit == QuantityUnit.SERVINGS) 30.0 else null, n)

    private fun template(name: String, vararg items: TemplateItem) = NewMealTemplate(name, items.toList())

    private fun exportedItem(name: String, quantity: Double = 50.0) =
        ExportedTemplateItem("111", name, quantity, QuantityUnit.GRAMS, null, ExportedNutrients(kcal = 100.0))

    private fun exported(id: Long, name: String, updated: Long = 1L, items: List<ExportedTemplateItem> = listOf(exportedItem("Pan"))) =
        ExportedMealTemplate(id, name, items, createdAtMillis = 1L, updatedAtMillis = updated)

    private fun json(vararg t: ExportedMealTemplate) =
        ExportCodec.encode(ExportFile(ExportCodec.CURRENT_VERSION, 1L, mealTemplates = t.toList()))

    @Test
    fun formatVersionIsNotBumpedByTheNewCollection() {
        assertEquals(1, ExportCodec.CURRENT_VERSION)
    }

    @Test
    fun oldFilesWithoutTheFieldReadAsEmpty() {
        val old = """{"schemaVersion":1,"exportedAtMillis":1,"products":[],"diaryEntries":[]}"""
        assertEquals(emptyList(), ExportCodec.decode(old).mealTemplates)
    }

    @Test
    fun roundTripKeepsNamesItemsQuantitiesUnitsAndOrder() = runTest {
        val src = Device()
        src.templates.create(
            template("Desayuno", item("Avena", 40.0), item("Yogur", 2.0, QuantityUnit.SERVINGS, barcode = null)), 10L,
        )
        src.templates.create(template("Cena", item("Tortilla")), 20L)
        val dst = Device()

        val report = dst.importer.import(src.exporter.exportToJson())

        assertEquals(2, report.mealTemplates.added)
        assertEquals(src.templates.all().map { it.copy(id = 0) }, dst.templates.all().map { it.copy(id = 0) })
        src.driver.close()
        dst.driver.close()
    }

    @Test
    fun importingTwiceIsIdempotent() = runTest {
        val src = Device()
        src.templates.create(template("Desayuno", item("Avena")), 10L)
        val dst = Device()
        val file = src.exporter.exportToJson()

        dst.importer.import(file)
        val again = dst.importer.import(file)

        assertEquals(1, again.mealTemplates.unchanged)
        assertEquals(0, again.mealTemplates.added)
        assertEquals(1, dst.templates.all().size)
        src.driver.close()
        dst.driver.close()
    }

    @Test
    fun sameNameWithOtherContentKeepsTheLocalOneUnlessTheFileIsNewer() = runTest {
        val dst = Device()
        val id = dst.templates.create(template("Desayuno", item("Local")), 100L) // updatedAt = 100

        val older = dst.importer.import(json(exported(7, "desayuno", updated = 50L, items = listOf(exportedItem("Remoto")))))
        assertEquals(1, older.mealTemplates.keptLocal)
        assertEquals(listOf("Local"), dst.templates.find(id)!!.items.map { it.name })

        val newer = dst.importer.import(json(exported(7, "DESAYUNO", updated = 500L, items = listOf(exportedItem("Remoto", 70.0)))))
        assertEquals(1, newer.mealTemplates.updated)
        val read = dst.templates.find(id)!!
        assertEquals(listOf("Remoto"), read.items.map { it.name })
        assertEquals(70.0, read.items.single().quantity.amount)
        assertEquals(1, dst.templates.all().size)
        dst.driver.close()
    }

    @Test
    fun invalidTemplatesAreSkippedAndReportedWhileTheRestImports() = runTest {
        val dst = Device()
        val file = json(
            exported(1, "Buena"),
            exported(2, "Sin alimentos", items = emptyList()),
            exported(3, "  ", items = listOf(exportedItem("Pan"))),
            exported(4, "Cantidad cero", items = listOf(exportedItem("Pan", 0.0))),
            exported(5, "Negativos", items = listOf(exportedItem("Pan").copy(nutrients = ExportedNutrients(kcal = -1.0)))),
        )

        val report = dst.importer.import(file)

        assertEquals(1, report.mealTemplates.added)
        assertEquals(4, report.mealTemplates.invalid)
        assertEquals(4, report.invalidItems.size)
        assertEquals(listOf("Buena"), dst.templates.all().map { it.name })
        dst.driver.close()
    }

    @Test
    fun aTargetWithoutTemplateQueriesExportsNoTemplates() = runTest {
        val dst = Device()
        dst.templates.create(template("Desayuno", item("Avena")), 10L)
        val bare = SqlDelightImportTarget(
            dst.db.productQueries, dst.db.diaryEntryQueries, dst.db.customFoodQueries, dst.db.weightEntryQueries,
            dst.db.recipeQueries, dst.db.settingsQueries, dst.db.favoriteFoodQueries,
        )
        assertTrue(DataExporter(bare) { 1L }.buildFile().mealTemplates.isEmpty())
        dst.driver.close()
    }
}
