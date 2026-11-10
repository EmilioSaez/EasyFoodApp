package app.luxion.fitarchiveapp.export

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.data.SqlDelightSettingsStore
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.domain.ReminderSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Los recordatorios viajan en la exportación como campo opcional, sin subir la versión del formato. */
class RemindersTransferTest {
    private class Device {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { EasyFoodDatabase.Schema.create(it) }
        val db = EasyFoodDatabase(driver)
        val settings = SqlDelightSettingsStore(db.settingsQueries)
        val target = SqlDelightImportTarget(
            db.productQueries, db.diaryEntryQueries, db.customFoodQueries, db.weightEntryQueries,
            db.recipeQueries, db.settingsQueries, db.favoriteFoodQueries,
        )
        val exporter = DataExporter(target) { 5_000L }
        val importer = DataImporter(target)
    }

    @Test
    fun exportAndImportCarryTheSettingsToAFreshDevice() = runTest {
        val src = Device()
        val config = ReminderSettings(enabled = true, mealDays = setOf(1, 2, 3), water = true)
        src.settings.setReminders(config)
        val json = src.exporter.exportToJson()
        assertEquals(config, ExportCodec.decode(json).reminders)

        val dst = Device()
        dst.importer.import(json)
        assertEquals(config, dst.settings.reminders())
        src.driver.close()
        dst.driver.close()
    }

    @Test
    fun importKeepsLocalSettingsWhenTheDeviceAlreadyHasThem() = runTest {
        val src = Device()
        src.settings.setReminders(ReminderSettings(enabled = true, water = true))
        val dst = Device()
        val mine = ReminderSettings(enabled = false)
        dst.settings.setReminders(mine)
        dst.importer.import(src.exporter.exportToJson())
        assertEquals(mine, dst.settings.reminders())
        src.driver.close()
        dst.driver.close()
    }

    @Test
    fun oldFilesWithoutRemindersChangeNothingAndImportedOnesAreSanitized() = runTest {
        val old = """{"schemaVersion":1,"exportedAtMillis":1,"products":[],"diaryEntries":[]}"""
        assertNull(ExportCodec.decode(old).reminders)
        val dst = Device()
        dst.importer.import(old)
        assertNull(dst.settings.reminders())

        val tampered = ExportCodec.encode(
            ExportFile(
                ExportCodec.CURRENT_VERSION, 1L,
                reminders = ReminderSettings(enabled = true, mealDays = setOf(0, 3, 12), weightMinutes = 99999),
            ),
        )
        dst.importer.import(tampered)
        val saved = dst.settings.reminders()!!
        assertEquals(setOf(3), saved.mealDays)
        assertEquals(ReminderSettings.MAX_MINUTE, saved.weightMinutes)
        dst.driver.close()
    }
}
