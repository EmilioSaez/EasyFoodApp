package app.luxion.fitarchiveapp.export

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.data.SqlDelightDiaryStore
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.QuickEntry
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Entradas rápidas en exportar e importar: campo opcional `quick`, sin subir la versión del formato (docs/design.md §2.16). */
class QuickEntryTransferTest {
    private class Device {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { EasyFoodDatabase.Schema.create(it) }
        val db = EasyFoodDatabase(driver)
        val diary = SqlDelightDiaryStore(db.diaryEntryQueries)
        val target = SqlDelightImportTarget(
            db.productQueries, db.diaryEntryQueries, db.customFoodQueries, db.weightEntryQueries,
            db.recipeQueries, db.settingsQueries, db.favoriteFoodQueries, waterQueries = db.waterQueries,
        )
        val exporter = DataExporter(target) { 5_000L }
        val importer = DataImporter(target)
    }

    private val quick = QuickEntry.new("2026-11-04", MealType.LUNCH, "Menú del día", 850.0, 30.0, null, 40.0)

    @Test
    fun exportCarriesTheQuickFlagAndTotalsWithoutBumpingTheVersion() = runTest {
        val src = Device()
        src.diary.add(quick, 10L)
        val file = ExportCodec.decode(src.exporter.exportToJson())
        val e = file.diaryEntries.single()
        assertTrue(e.quick)
        assertEquals(100.0, e.quantity)
        assertEquals(850.0, e.nutrients.kcal)
        assertEquals(ExportCodec.CURRENT_VERSION, file.schemaVersion)
        src.driver.close()
    }

    @Test
    fun oldFilesWithoutTheFieldReadAsRegularEntries() = runTest {
        val old = """{"schemaVersion":1,"exportedAtMillis":1,"diaryEntries":[{"id":1,"date":"2026-11-04","meal":"LUNCH",""" +
            """"name":"Arroz","quantity":150.0,"unit":"GRAMS","createdAtMillis":5}]}"""
        assertFalse(ExportCodec.decode(old).diaryEntries.single().quick)
        val dst = Device()
        dst.importer.import(old)
        assertFalse(dst.diary.entriesForDate("2026-11-04").single().quick)
        dst.driver.close()
    }

    @Test
    fun roundTripBetweenDevicesKeepsQuickEntriesAndTheirTotals() = runTest {
        val src = Device()
        src.diary.add(quick, 10L)
        src.diary.add(QuickEntry.new("2026-11-04", MealType.DINNER, "", 500.0, null, null, null), 11L)
        val dst = Device()
        val report = dst.importer.import(src.exporter.exportToJson())
        assertEquals(2, report.diaryEntries.added)
        val imported = dst.diary.entriesForDate("2026-11-04")
        assertEquals(listOf(true, true), imported.map { it.quick })
        assertEquals(Nutrients(850.0, 30.0, null, 40.0), imported.first { it.meal == MealType.LUNCH }.nutrients)
        assertEquals(QuickEntry.DEFAULT_NAME, imported.first { it.meal == MealType.DINNER }.name)
        assertEquals(1350.0, dst.diary.daySummaries("2026-11-04", "2026-11-04").single().kcal, 1e-9)
        src.driver.close(); dst.driver.close()
    }

    @Test
    fun importingTwiceDoesNotDuplicateQuickEntries() = runTest {
        val src = Device()
        src.diary.add(quick, 10L)
        val text = src.exporter.exportToJson()
        val dst = Device()
        dst.importer.import(text)
        val second = dst.importer.import(text)
        assertEquals(1, dst.diary.entriesForDate("2026-11-04").size)
        assertEquals(1, second.diaryEntries.unchanged)
        src.driver.close(); dst.driver.close()
    }

    @Test
    fun aQuickEntryWithAnotherQuantityIsReportedAsInvalid() = runTest {
        val bad = """{"schemaVersion":1,"exportedAtMillis":1,"diaryEntries":[{"id":1,"date":"2026-11-04","meal":"LUNCH",""" +
            """"name":"X","quantity":30.0,"unit":"GRAMS","nutrients":{"kcal":500.0},"createdAtMillis":5,"quick":true}]}"""
        val dst = Device()
        val report = dst.importer.import(bad)
        assertEquals(1, report.diaryEntries.invalid)
        assertTrue(dst.diary.entriesForDate("2026-11-04").isEmpty())
        dst.driver.close()
    }
}
