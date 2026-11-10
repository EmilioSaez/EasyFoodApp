package app.luxion.fitarchiveapp.data.migration

import app.luxion.fitarchiveapp.data.SqlDelightDiaryStore
import app.luxion.fitarchiveapp.data.migration.MigrationTestKit.use
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 16.sqm añade a `DiaryEntryEntity` cuatro columnas nullable (saturadas, azúcares, fibra y sal por 100 g). Las
 * entradas existentes se conservan intactas con las columnas nuevas en NULL. `ADD COLUMN` no es idempotente
 * (SQLite no tiene `IF NOT EXISTS` para columnas), así que la migración se aplica una sola vez por base.
 */
class Migration16Test {
    private val extra = listOf("saturated_fat_100g", "sugars_100g", "fiber_100g", "salt_100g")

    @Test
    fun v16HasNoExtraColumnsAndMigrationKeepsEveryExistingRow() = MigrationTestKit.legacyDatabase(16).use { d ->
        assertTrue(extra.none { it in MigrationTestKit.columns(d, "DiaryEntryEntity") })
        RepresentativeUserData.seed(d, 16)
        val before = MigrationTestKit.snapshot(d)
        assertTrue(before.getValue("DiaryEntryEntity").isNotEmpty())

        MigrationTestKit.migrateToLatest(d, from = 16)

        MigrationTestKit.assertPreserved(before, MigrationTestKit.snapshot(d))
        MigrationTestKit.assertSameSchemaAsFresh(d)
        assertTrue(MigrationTestKit.columns(d, "DiaryEntryEntity").containsAll(extra))
        assertEquals(0, MigrationTestKit.scalar(d, "SELECT COUNT(*) FROM DiaryEntryEntity WHERE ${extra.joinToString(" OR ") { "$it IS NOT NULL" }}"))
    }

    @Test
    fun existingEntriesReadBackWithNullExtras() = runTest {
        MigrationTestKit.legacyDatabase(16).use { d ->
            RepresentativeUserData.seed(d, 16)
            MigrationTestKit.migrateToLatest(d, from = 16)

            val e = SqlDelightDiaryStore(EasyFoodDatabase(d).diaryEntryQueries).find(2)!!
            assertEquals("Leche entera", e.name)
            assertEquals(64.0, e.nutrients.kcal)
            assertNull(e.nutrients.fiberG)
            assertNull(e.nutrients.saltG)
            assertNull(e.nutrients.sugarsG)
            assertNull(e.nutrients.saturatedFatG)
        }
    }

    @Test
    fun anOlderDatabaseReachesTheExtraColumnsToo() = MigrationTestKit.legacyDatabase(11).use { d ->
        RepresentativeUserData.seed(d, 11)
        val before = MigrationTestKit.snapshot(d)

        MigrationTestKit.migrateToLatest(d, from = 11)

        MigrationTestKit.assertPreserved(before, MigrationTestKit.snapshot(d))
        MigrationTestKit.assertSameSchemaAsFresh(d)
        assertFalse(MigrationTestKit.columns(d, "DiaryEntryEntity").none { it in extra })
    }

    @Test
    fun newColumnsStoreValuesAfterMigration() = MigrationTestKit.legacyDatabase(16).use { d ->
        MigrationTestKit.migrateToLatest(d, from = 16)
        RepresentativeUserData.seed(d, MigrationTestKit.latestVersion)
        assertEquals(1, MigrationTestKit.scalar(d, "SELECT COUNT(*) FROM DiaryEntryEntity WHERE id = 2 AND salt_100g = 0.1 AND fiber_100g = 0.0"))
        assertEquals(1, MigrationTestKit.scalar(d, "SELECT COUNT(*) FROM DiaryEntryEntity WHERE id = 5 AND sugars_100g = 10.4 AND salt_100g IS NULL"))
    }
}
