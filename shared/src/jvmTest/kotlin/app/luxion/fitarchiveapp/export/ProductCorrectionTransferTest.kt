package app.luxion.fitarchiveapp.export

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.data.SqlDelightProductStore
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Product
import app.luxion.fitarchiveapp.model.ProductCorrection
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Correcciones de productos en exportar e importar: colección opcional, sin subir la versión del formato. */
class ProductCorrectionTransferTest {
    private class Device {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { EasyFoodDatabase.Schema.create(it) }
        val db = EasyFoodDatabase(driver)
        val products = SqlDelightProductStore(db.productQueries)
        val target = SqlDelightImportTarget(
            db.productQueries, db.diaryEntryQueries, db.customFoodQueries, db.weightEntryQueries,
            db.recipeQueries, db.settingsQueries, db.favoriteFoodQueries,
        )
        val exporter = DataExporter(target) { 5_000L }
        val importer = DataImporter(target)
    }

    private val off = Product("3017620422003", "Nutella", "Ferrero", null, Nutrients(900.0, 6.0, 57.0, 30.0))
    private val fixed = Nutrients(539.0, 6.3, 57.5, 30.9)

    @Test
    fun formatVersionIsNotBumped() = assertEquals(1, ExportCodec.CURRENT_VERSION)

    @Test
    fun oldFilesWithoutTheCollectionReadAsEmpty() {
        val old = """{"schemaVersion":1,"exportedAtMillis":1,"products":[],"diaryEntries":[]}"""
        assertEquals(emptyList(), ExportCodec.decode(old).productCorrections)
    }

    @Test
    fun correctionTravelsToAnotherDeviceAndOriginalsStayOriginal() = runTest {
        val src = Device()
        src.products.save(off, 1L)
        src.products.saveCorrection(ProductCorrection(off.barcode, fixed, 7L))
        val dst = Device()
        dst.products.save(off, 1L)

        val report = dst.importer.import(src.exporter.exportToJson())

        assertEquals(1, report.productCorrections.added)
        val p = dst.products.find(off.barcode)!!.product
        assertEquals(fixed, p.nutrients)
        assertEquals(off.nutrients, p.originalNutrients)
        // el producto exportado lleva los valores originales, no los corregidos
        assertEquals(off.nutrients.kcal, ExportCodec.decode(src.exporter.exportToJson()).products.single().nutrients.kcal)
        src.driver.close(); dst.driver.close()
    }

    @Test
    fun importingTwiceIsADuplicate() = runTest {
        val src = Device()
        src.products.save(off, 1L)
        src.products.saveCorrection(ProductCorrection(off.barcode, fixed, 7L))
        val json = src.exporter.exportToJson()
        val dst = Device()
        dst.importer.import(json)

        val again = dst.importer.import(json)

        assertEquals(1, again.productCorrections.unchanged)
        assertTrue(dst.importer.preview(json).changes.productCorrections.isEmpty())
        src.driver.close(); dst.driver.close()
    }

    @Test
    fun newerCorrectionWinsAndTiesKeepLocal() = runTest {
        val dst = Device()
        dst.products.saveCorrection(ProductCorrection(off.barcode, fixed, 10L))
        fun file(n: Nutrients, at: Long) = ExportCodec.encode(
            ExportFile(1, 1L, productCorrections = listOf(ExportedProductCorrection(off.barcode, n.toExported(), at))),
        )

        val older = dst.importer.import(file(fixed.copy(kcal = 500.0), 5L))
        val tie = dst.importer.import(file(fixed.copy(kcal = 501.0), 10L))
        assertEquals(1, older.productCorrections.keptLocal)
        assertEquals(1, tie.productCorrections.keptLocal)
        assertEquals(539.0, dst.db.productQueries.selectCorrection(off.barcode).executeAsOne().kcal)

        val newer = dst.importer.import(file(fixed.copy(kcal = 502.0), 11L))
        assertEquals(1, newer.productCorrections.updated)
        assertEquals(502.0, dst.db.productQueries.selectCorrection(off.barcode).executeAsOne().kcal)
        dst.driver.close()
    }

    @Test
    fun invalidCorrectionsAreSkippedAndReported() = runTest {
        val dst = Device()
        val bad = ExportedProductCorrection("1", ExportedNutrients(kcal = -5.0, proteinG = 1.0, carbsG = 1.0, fatG = 1.0))
        val noKcal = ExportedProductCorrection("2", ExportedNutrients(proteinG = 1.0, carbsG = 1.0, fatG = 1.0))
        val json = ExportCodec.encode(ExportFile(1, 1L, productCorrections = listOf(bad, noKcal)))

        val report = dst.importer.import(json)

        assertEquals(2, report.productCorrections.invalid)
        assertEquals(2, report.invalidItems.size)
        assertEquals(0, dst.db.productQueries.selectAllCorrections().executeAsList().size)
        dst.driver.close()
    }
}
