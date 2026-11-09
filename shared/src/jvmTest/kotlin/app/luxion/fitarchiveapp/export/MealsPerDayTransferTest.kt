package app.luxion.fitarchiveapp.export

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.data.SqlDelightDiaryStore
import app.luxion.fitarchiveapp.data.SqlDelightSettingsStore
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.NewDiaryEntry
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `meals_per_day` y las comidas nuevas en exportar e importar (docs/design.md §2.9). */
class MealsPerDayTransferTest {
    private class Device {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { EasyFoodDatabase.Schema.create(it) }
        val db = EasyFoodDatabase(driver)
        val settings = SqlDelightSettingsStore(db.settingsQueries)
        val diary = SqlDelightDiaryStore(db.diaryEntryQueries)
        val target = SqlDelightImportTarget(
            db.productQueries, db.diaryEntryQueries, db.customFoodQueries, db.weightEntryQueries,
            db.recipeQueries, db.settingsQueries,
        )
        val exporter = DataExporter(target) { 5_000L }
        val importer = DataImporter(target)
    }

    private fun json(mealsPerDay: Int?, version: Int = ExportCodec.CURRENT_VERSION) =
        ExportCodec.encode(ExportFile(version, 1L, mealsPerDay = mealsPerDay))

    @Test
    fun exportCarriesTheSetting() = runTest {
        val src = Device()
        src.settings.setMealsPerDay(5)
        assertEquals(5, ExportCodec.decode(src.exporter.exportToJson()).mealsPerDay)
        src.driver.close()
    }

    @Test
    fun exportWithoutChoiceHasNoSetting() = runTest {
        val src = Device()
        assertNull(ExportCodec.decode(src.exporter.exportToJson()).mealsPerDay)
        src.driver.close()
    }

    @Test
    fun oldFilesWithoutTheFieldReadAsNull() {
        val old = """{"schemaVersion":1,"exportedAtMillis":1,"products":[],"diaryEntries":[]}"""
        assertNull(ExportCodec.decode(old).mealsPerDay)
    }

    @Test
    fun importingIntoADeviceThatHasNotChosenAdoptsTheSetting() = runTest {
        val dst = Device()
        dst.importer.import(json(6))
        assertEquals(6, dst.settings.mealsPerDay())
        dst.driver.close()
    }

    @Test
    fun importKeepsTheLocalSettingWhenAlreadyChosen() = runTest {
        val dst = Device()
        dst.settings.setMealsPerDay(3)
        dst.importer.import(json(6))
        assertEquals(3, dst.settings.mealsPerDay())
        dst.driver.close()
    }

    @Test
    fun importingAFileWithoutTheFieldChangesNothing() = runTest {
        val dst = Device()
        dst.importer.import(json(null))
        assertNull(dst.settings.mealsPerDay())
        dst.settings.setMealsPerDay(5)
        dst.importer.import(json(null))
        assertEquals(5, dst.settings.mealsPerDay())
        dst.driver.close()
    }

    @Test
    fun outOfRangeValueIsReportedAndIgnored() = runTest {
        val dst = Device()
        val report = dst.importer.import(json(9))
        assertNull(dst.settings.mealsPerDay())
        assertEquals(1, report.invalidItems.size)
        assertTrue(report.invalidItems.single().contains("Comidas al día"))
        dst.driver.close()
    }

    @Test
    fun previewDoesNotWriteTheSetting() = runTest {
        val dst = Device()
        dst.importer.preview(json(4))
        assertNull(dst.settings.mealsPerDay())
        dst.driver.close()
    }

    @Test
    fun newMealTypesRoundTrip() = runTest {
        val src = Device()
        MealType.entries.forEachIndexed { i, meal ->
            src.diary.add(
                NewDiaryEntry("2026-11-04", meal, null, "E$i", Quantity(50.0, QuantityUnit.GRAMS), null, Nutrients(100.0, 1.0, 1.0, 1.0)),
                i.toLong(),
            )
        }
        val dst = Device()
        dst.importer.import(src.exporter.exportToJson())
        assertEquals(MealType.entries.toSet(), dst.diary.entriesForDate("2026-11-04").map { it.meal }.toSet())
        src.driver.close(); dst.driver.close()
    }
}
