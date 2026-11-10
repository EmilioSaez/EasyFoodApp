package app.luxion.fitarchiveapp.data.migration

import app.luxion.fitarchiveapp.data.ProductRefreshInfo
import app.luxion.fitarchiveapp.data.SqlDelightProductStore
import app.luxion.fitarchiveapp.data.migration.MigrationTestKit.use
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 14.sqm crea `ProductRefreshEntity` (seguimiento del refresco de productos de Open Food Facts) y su trigger de
 * limpieza, y rellena `refreshed_at` con `cached_at` de las filas de OFF existentes. No toca ninguna fila existente
 * y es idempotente.
 */
class Migration14Test {
    @Test
    fun v14HasNoRefreshTableAndMigrationKeepsEveryExistingRow() = MigrationTestKit.legacyDatabase(14).use { d ->
        assertFalse(MigrationTestKit.hasTable(d, "ProductRefreshEntity"))
        RepresentativeUserData.seed(d, 14)
        val before = MigrationTestKit.snapshot(d)
        assertTrue(before.getValue("ProductEntity").isNotEmpty())

        MigrationTestKit.migrateToLatest(d, from = 14)

        MigrationTestKit.assertPreserved(before, MigrationTestKit.snapshot(d))
        MigrationTestKit.assertSameSchemaAsFresh(d)
    }

    @Test
    fun backfillsOnlyProductsFromOpenFoodFactsWithTheirDownloadDate() = runTest {
        MigrationTestKit.legacyDatabase(14).use { d ->
            RepresentativeUserData.seed(d, 14)
            MigrationTestKit.migrateToLatest(d, from = 14)

            val store = SqlDelightProductStore(EasyFoodDatabase(d).productQueries)
            assertEquals(ProductRefreshInfo(1_700_000_200_000, null, null), store.refreshInfo("8410000000017"))
            assertEquals(ProductRefreshInfo(1_700_000_400_000, null, null), store.refreshInfo("5000000000002"))
            assertNull(store.refreshInfo("generic:1"))
            assertEquals(2, MigrationTestKit.scalar(d, "SELECT COUNT(*) FROM ProductRefreshEntity").toInt())
        }
    }

    @Test
    fun anOlderDatabaseReachesTheRefreshTableToo() = MigrationTestKit.legacyDatabase(11).use { d ->
        RepresentativeUserData.seed(d, 11)
        val before = MigrationTestKit.snapshot(d)

        MigrationTestKit.migrateToLatest(d, from = 11)

        MigrationTestKit.assertPreserved(before, MigrationTestKit.snapshot(d))
        MigrationTestKit.assertSameSchemaAsFresh(d)
    }

    @Test
    fun reapplyingTheMigrationKeepsRefreshData() = MigrationTestKit.legacyDatabase(14).use { d ->
        MigrationTestKit.migrateToLatest(d, from = 14)
        RepresentativeUserData.seed(d, MigrationTestKit.latestVersion)
        val before = MigrationTestKit.snapshot(d)
        assertEquals(2, before.getValue("ProductRefreshEntity").size)

        MigrationTestKit.migrateToLatest(d, from = 14) // 14.sqm es idempotente

        MigrationTestKit.assertPreserved(before, MigrationTestKit.snapshot(d))
        MigrationTestKit.assertSameSchemaAsFresh(d)
    }

    @Test
    fun theCleanupTriggerDropsTheRefreshRowWhenTheProductIsDeleted() = MigrationTestKit.legacyDatabase(14).use { d ->
        RepresentativeUserData.seed(d, 14)
        MigrationTestKit.migrateToLatest(d, from = 14)

        d.execute(null, "DELETE FROM ProductEntity WHERE barcode = '8410000000017'", 0)

        assertEquals(1, MigrationTestKit.scalar(d, "SELECT COUNT(*) FROM ProductRefreshEntity").toInt())
    }
}
