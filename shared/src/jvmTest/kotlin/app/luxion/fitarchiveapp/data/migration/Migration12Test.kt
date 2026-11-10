package app.luxion.fitarchiveapp.data.migration

import app.luxion.fitarchiveapp.data.SqlDelightDiaryStore
import app.luxion.fitarchiveapp.data.migration.MigrationTestKit.use
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.QuickEntry
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 12.sqm añade la columna `quick` (entradas rápidas) a `DiaryEntryEntity`. No es idempotente (`ALTER TABLE ADD COLUMN`),
 * así que cada base se migra una sola vez. Las filas existentes deben quedar como alimentos normales (`quick = 0`).
 */
class Migration12Test {
    @Test
    fun v12HasNoQuickColumnAndMigrationKeepsEveryRowAsARegularEntry() = MigrationTestKit.legacyDatabase(12).use { d ->
        assertFalse("quick" in MigrationTestKit.columns(d, "DiaryEntryEntity"))
        RepresentativeUserData.seed(d, 12)
        val before = MigrationTestKit.snapshot(d)
        assertTrue(before.getValue("DiaryEntryEntity").isNotEmpty())

        MigrationTestKit.migrateToLatest(d, from = 12)

        MigrationTestKit.assertPreserved(before, MigrationTestKit.snapshot(d))
        MigrationTestKit.assertSameSchemaAsFresh(d)
        assertEquals(0, MigrationTestKit.scalar(d, "SELECT COUNT(*) FROM DiaryEntryEntity WHERE quick <> 0"))
    }

    @Test
    fun migratedDatabaseAcceptsQuickEntriesAndKeepsIds() = runTest {
        MigrationTestKit.legacyDatabase(12).use { d ->
            RepresentativeUserData.seed(d, 12)
            val maxId = MigrationTestKit.scalar(d, "SELECT MAX(id) FROM DiaryEntryEntity")
            MigrationTestKit.migrateToLatest(d, from = 12)

            val store = SqlDelightDiaryStore(EasyFoodDatabase(d).diaryEntryQueries)
            val id = store.add(QuickEntry.new("2026-11-03", MealType.LUNCH, "Menú", 850.0, null, null, null), 400L)

            assertTrue(id > maxId)
            assertTrue(store.find(id)!!.quick)
            assertFalse(store.entriesForDate("2026-11-01").any { it.quick })
            assertFalse(store.recentFoods(50).any { it.quick })
        }
    }

    @Test
    fun aDatabaseStillAtV11ReachesTheQuickColumnToo() = MigrationTestKit.legacyDatabase(11).use { d ->
        RepresentativeUserData.seed(d, 11)
        val before = MigrationTestKit.snapshot(d)

        MigrationTestKit.migrateToLatest(d, from = 11)

        MigrationTestKit.assertPreserved(before, MigrationTestKit.snapshot(d))
        MigrationTestKit.assertSameSchemaAsFresh(d)
    }
}
