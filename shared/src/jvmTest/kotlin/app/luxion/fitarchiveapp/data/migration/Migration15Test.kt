package app.luxion.fitarchiveapp.data.migration

import app.luxion.fitarchiveapp.data.SqlDelightProductStore
import app.luxion.fitarchiveapp.data.migration.MigrationTestKit.use
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 15.sqm crea `ProductCorrectionEntity` (correcciones del usuario sobre los valores de un producto). Solo añade una
 * tabla: no toca ninguna fila existente y es idempotente.
 */
class Migration15Test {
    @Test
    fun v15HasNoCorrectionTableAndMigrationKeepsEveryExistingRow() = MigrationTestKit.legacyDatabase(15).use { d ->
        assertFalse(MigrationTestKit.hasTable(d, "ProductCorrectionEntity"))
        RepresentativeUserData.seed(d, 15)
        val before = MigrationTestKit.snapshot(d)
        assertTrue(before.getValue("ProductEntity").isNotEmpty())

        MigrationTestKit.migrateToLatest(d, from = 15)

        assertTrue(MigrationTestKit.hasTable(d, "ProductCorrectionEntity"))
        MigrationTestKit.assertPreserved(before, MigrationTestKit.snapshot(d))
        MigrationTestKit.assertSameSchemaAsFresh(d)
        assertEquals(0, MigrationTestKit.scalar(d, "SELECT COUNT(*) FROM ProductCorrectionEntity").toInt())
        // el índice de búsqueda sigue sincronizado con los productos
        assertEquals(
            MigrationTestKit.scalar(d, "SELECT COUNT(*) FROM ProductEntity"),
            MigrationTestKit.scalar(d, "SELECT COUNT(*) FROM ProductFts"),
        )
    }

    @Test
    fun anOlderDatabaseReachesTheCorrectionTableToo() = MigrationTestKit.legacyDatabase(11).use { d ->
        RepresentativeUserData.seed(d, 11)
        val before = MigrationTestKit.snapshot(d)

        MigrationTestKit.migrateToLatest(d, from = 11)

        MigrationTestKit.assertPreserved(before, MigrationTestKit.snapshot(d))
        MigrationTestKit.assertSameSchemaAsFresh(d)
    }

    @Test
    fun reapplyingTheMigrationKeepsExistingCorrections() = MigrationTestKit.legacyDatabase(15).use { d ->
        MigrationTestKit.migrateToLatest(d, from = 15)
        RepresentativeUserData.seed(d, MigrationTestKit.latestVersion)
        val before = MigrationTestKit.snapshot(d)
        assertEquals(2, before.getValue("ProductCorrectionEntity").size)

        EasyFoodDatabase.Schema.migrate(d, 15, 16) // solo la 15, que es idempotente

        MigrationTestKit.assertPreserved(before, MigrationTestKit.snapshot(d))
        MigrationTestKit.assertSameSchemaAsFresh(d)
    }

    @Test
    fun correctionsSurviveAProductRefresh() = runTest {
        MigrationTestKit.legacyDatabase(15).use { d ->
            MigrationTestKit.migrateToLatest(d, from = 15)
            RepresentativeUserData.seed(d, MigrationTestKit.latestVersion)
            val store = SqlDelightProductStore(EasyFoodDatabase(d).productQueries)

            val found = store.find("8410000000017")!!.product
            assertTrue(found.isCorrected)
            assertEquals(62.5, found.nutrients.kcal)
            assertEquals(64.0, found.originalNutrients?.kcal)
        }
    }
}
