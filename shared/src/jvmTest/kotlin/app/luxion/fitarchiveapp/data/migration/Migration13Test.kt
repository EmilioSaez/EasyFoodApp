package app.luxion.fitarchiveapp.data.migration

import app.luxion.fitarchiveapp.data.SqlDelightExerciseStore
import app.luxion.fitarchiveapp.data.migration.MigrationTestKit.use
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.model.ExerciseRules
import app.luxion.fitarchiveapp.model.NewExercise
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 13.sqm crea `ExerciseEntity` y `ExerciseSettingsEntity` (ejercicio que suma al objetivo). Solo añade tablas
 * (`CREATE ... IF NOT EXISTS`): no toca los datos existentes y es idempotente.
 */
class Migration13Test {
    @Test
    fun v13HasNoExerciseTablesAndMigrationKeepsEveryExistingRow() = MigrationTestKit.legacyDatabase(13).use { d ->
        assertFalse(MigrationTestKit.hasTable(d, "ExerciseEntity"))
        assertFalse(MigrationTestKit.hasTable(d, "ExerciseSettingsEntity"))
        RepresentativeUserData.seed(d, 13)
        val before = MigrationTestKit.snapshot(d)
        assertTrue(before.getValue("DiaryEntryEntity").isNotEmpty())

        MigrationTestKit.migrateToLatest(d, from = 13)

        MigrationTestKit.assertPreserved(before, MigrationTestKit.snapshot(d))
        MigrationTestKit.assertSameSchemaAsFresh(d)
        assertEquals(0, MigrationTestKit.scalar(d, "SELECT COUNT(*) FROM ExerciseEntity"))
        assertEquals(0, MigrationTestKit.scalar(d, "SELECT COUNT(*) FROM ExerciseSettingsEntity"))
    }

    @Test
    fun migratedDatabaseStoresExerciseAndTheDefaultPercentApplies() = runTest {
        MigrationTestKit.legacyDatabase(13).use { d ->
            RepresentativeUserData.seed(d, 13)
            MigrationTestKit.migrateToLatest(d, from = 13)

            val store = SqlDelightExerciseStore(EasyFoodDatabase(d).exerciseQueries)
            assertEquals(ExerciseRules.DEFAULT_PERCENT, store.percent())
            val id = store.add(NewExercise("2026-11-09", "Correr", 400, 40), 1L)
            assertEquals(listOf(id), store.entriesForDate("2026-11-09").map { it.id })
        }
    }

    @Test
    fun anOlderDatabaseReachesTheExerciseTablesToo() = MigrationTestKit.legacyDatabase(11).use { d ->
        RepresentativeUserData.seed(d, 11)
        val before = MigrationTestKit.snapshot(d)

        MigrationTestKit.migrateToLatest(d, from = 11)

        MigrationTestKit.assertPreserved(before, MigrationTestKit.snapshot(d))
        MigrationTestKit.assertSameSchemaAsFresh(d)
    }

    @Test
    fun reapplyingTheMigrationKeepsExerciseData() = MigrationTestKit.legacyDatabase(13).use { d ->
        MigrationTestKit.migrateToLatest(d, from = 13)
        RepresentativeUserData.seed(d, MigrationTestKit.latestVersion)
        // 16.sqm (ALTER TABLE ADD COLUMN) no es idempotente: se quitan sus columnas para repetir solo esta migración.
        for (c in listOf("saturated_fat_100g", "sugars_100g", "fiber_100g", "salt_100g")) d.execute(null, "ALTER TABLE DiaryEntryEntity DROP COLUMN $c", 0)
        val before = MigrationTestKit.snapshot(d)
        assertEquals(3, before.getValue("ExerciseEntity").size)

        MigrationTestKit.migrateToLatest(d, from = 13) // 13.sqm es idempotente

        MigrationTestKit.assertPreserved(before, MigrationTestKit.snapshot(d))
        MigrationTestKit.assertSameSchemaAsFresh(d)
    }
}
