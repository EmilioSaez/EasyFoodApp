package app.luxion.fitarchiveapp.data.migration

import app.luxion.fitarchiveapp.data.SqlDelightSettingsStore
import app.luxion.fitarchiveapp.data.migration.MigrationTestKit.use
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.domain.ReminderSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 17.sqm añade `SettingsEntity.reminders` (JSON de los recordatorios locales, nulo = sin configurar). Solo añade una
 * columna: la fila de ajustes existente conserva `meals_per_day`. Es idempotente (reconstruye la tabla).
 */
class Migration17Test {
    @Test
    fun v17HasNoRemindersColumnAndMigrationKeepsEveryExistingRow() = MigrationTestKit.legacyDatabase(17).use { d ->
        assertFalse("reminders" in MigrationTestKit.columns(d, "SettingsEntity"))
        RepresentativeUserData.seed(d, 17)
        val before = MigrationTestKit.snapshot(d)
        assertEquals(1, before.getValue("SettingsEntity").size)

        MigrationTestKit.migrateToLatest(d, from = 17)

        MigrationTestKit.assertPreserved(before, MigrationTestKit.snapshot(d))
        MigrationTestKit.assertSameSchemaAsFresh(d)
        assertTrue("reminders" in MigrationTestKit.columns(d, "SettingsEntity"))
    }

    @Test
    fun theMealsPerDayChoiceSurvivesAndRemindersStartUnset() = runTest {
        MigrationTestKit.legacyDatabase(17).use { d ->
            RepresentativeUserData.seed(d, 17)
            MigrationTestKit.migrateToLatest(d, from = 17)

            val store = SqlDelightSettingsStore(EasyFoodDatabase(d).settingsQueries)
            assertEquals(5, store.mealsPerDay())
            assertNull(store.reminders())

            val config = ReminderSettings(enabled = true, water = true)
            store.setReminders(config)
            assertEquals(config, store.reminders())
            assertEquals(5, store.mealsPerDay())
            assertEquals(1, MigrationTestKit.scalar(d, "SELECT COUNT(*) FROM SettingsEntity").toInt())
        }
    }

    @Test
    fun withoutASettingsRowTheMigrationLeavesItEmpty() = runTest {
        MigrationTestKit.legacyDatabase(17).use { d ->
            MigrationTestKit.migrateToLatest(d, from = 17)

            val store = SqlDelightSettingsStore(EasyFoodDatabase(d).settingsQueries)
            assertNull(store.mealsPerDay()) // sin elegir: sigue saliendo la pantalla de primera vez
            assertNull(store.reminders())
            store.setReminders(ReminderSettings(enabled = true))
            assertNull(store.mealsPerDay()) // guardar recordatorios no se salta esa pantalla
        }
    }

    @Test
    fun anOlderDatabaseReachesTheRemindersColumnToo() = MigrationTestKit.legacyDatabase(11).use { d ->
        RepresentativeUserData.seed(d, 11)
        val before = MigrationTestKit.snapshot(d)

        MigrationTestKit.migrateToLatest(d, from = 11)

        MigrationTestKit.assertPreserved(before, MigrationTestKit.snapshot(d))
        MigrationTestKit.assertSameSchemaAsFresh(d)
    }
}
