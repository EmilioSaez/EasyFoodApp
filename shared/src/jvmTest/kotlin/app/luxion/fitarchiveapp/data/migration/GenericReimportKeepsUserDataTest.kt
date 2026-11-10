package app.luxion.fitarchiveapp.data.migration

import app.luxion.fitarchiveapp.data.generic.GenericFoodFormat
import app.luxion.fitarchiveapp.data.generic.GenericFoodImporter
import app.luxion.fitarchiveapp.data.generic.GenericFoodSeeder
import app.luxion.fitarchiveapp.data.generic.GenericSeedState
import app.luxion.fitarchiveapp.data.migration.MigrationTestKit.use
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * La base genérica se reimporta al arrancar cuando [GenericFoodFormat.DATA_VERSION] sube (p. ej. nombres en español).
 * Eso reescribe solo las filas GENERIC de ProductEntity: nada del usuario (diario, favoritos, recetas, alimentos
 * propios, productos de Open Food Facts, pesos, agua, ajustes…) puede cambiar.
 */
class GenericReimportKeepsUserDataTest {
    private val foods = listOf(
        GenericFoodFormat.MAGIC,
        GenericFoodFormat.COLUMNS.joinToString("\t"),
        "1\tApples, raw, with skin\t52\t0.26\t13.8\t0.17\t0.03\t10.4\t2.4\t0",
        "2\tBread, wheat\t266\t10.7\t49.2\t3.3\t\t5.7\t2.7\t1.3",
    ).joinToString("\n")

    private val translations = listOf(
        GenericFoodFormat.TRANSLATION_MAGIC,
        GenericFoodFormat.TRANSLATION_COLUMNS.joinToString("\t"),
        "1\tManzana, cruda, con piel",
        "2\tPan de trigo",
    ).joinToString("\n")

    private fun seeder(db: EasyFoodDatabase) = GenericFoodSeeder(
        importer = GenericFoodImporter(db.productQueries),
        nowMillis = { 2_000_000_000_000 },
        readResource = { foods },
        readTranslations = { translations },
    )

    /** Cualquier base antigua (la genérica estaba en v1 = inglés) migra y se reimporta sin tocar datos del usuario. */
    @Test
    fun reimportAfterMigrationLeavesEveryUserTableUntouched() = runTest {
        for (version in MigrationTestKit.legacyVersions) {
            MigrationTestKit.legacyDatabase(version).use { d ->
                RepresentativeUserData.seed(d, version)
                MigrationTestKit.migrateToLatest(d, from = version)
                val db = EasyFoodDatabase(d)
                val before = MigrationTestKit.snapshot(d)
                    .filterKeys { it !in setOf("ProductEntity", "ProductFts", "GenericFoodsMetaEntity") }
                val offBefore = db.productQueries.selectAll().executeAsList().filter { it.source != "GENERIC" }

                val seeder = seeder(db)
                seeder.ensureSeeded()
                assertEquals(GenericSeedState.Done, seeder.state.value, "desde v$version")

                val after = MigrationTestKit.snapshot(d)
                MigrationTestKit.assertPreserved(before, after)
                // Productos que no son genéricos (caché de Open Food Facts): idénticos.
                assertEquals(offBefore, db.productQueries.selectAll().executeAsList().filter { it.source != "GENERIC" })
                // Los genéricos se han actualizado: nombre en español, versión al día, índice de búsqueda coherente.
                assertEquals("Manzana, cruda, con piel", db.productQueries.selectByBarcode("generic:1").executeAsOne().name)
                assertEquals(GenericFoodFormat.DATA_VERSION.toLong(), db.productQueries.selectGenericDataVersion().executeAsOne())
                assertEquals(1L, MigrationTestKit.scalar(d, "SELECT count(*) FROM ProductFts WHERE ProductFts MATCH 'manzana'"))
                assertEquals(0L, MigrationTestKit.scalar(d, "SELECT count(*) FROM ProductFts WHERE ProductFts MATCH 'apples'"))
                assertEquals(
                    MigrationTestKit.scalar(d, "SELECT count(*) FROM ProductEntity"),
                    MigrationTestKit.scalar(d, "SELECT count(*) FROM ProductFts"),
                )
            }
        }
    }

    /** Los favoritos y el diario guardan la clave `generic:<id>`: la reimportación no debe romper ese enlace. */
    @Test
    fun favoritesAndDiaryStillPointToTheReimportedGenericFood() = runTest {
        MigrationTestKit.legacyDatabase(MigrationTestKit.latestVersion).use { d ->
            RepresentativeUserData.seed(d, MigrationTestKit.latestVersion)
            val db = EasyFoodDatabase(d)
            seeder(db).ensureSeeded()

            val favorite = db.favoriteFoodQueries.selectProducts().executeAsList().single { it.barcode == "generic:1" }
            assertEquals("Manzana, cruda, con piel", favorite.name)
            assertTrue(db.diaryEntryQueries.selectAll().executeAsList().any { it.barcode == "generic:1" })
        }
    }

    /** Segunda vez: la versión ya está al día y no se vuelve a importar (no pisa nombres tocados a mano, ni coste). */
    @Test
    fun secondRunWithCurrentVersionDoesNothing() = runTest {
        MigrationTestKit.legacyDatabase(MigrationTestKit.latestVersion).use { d ->
            val db = EasyFoodDatabase(d)
            seeder(db).ensureSeeded()
            db.productQueries.upsert(
                "generic:1", "Nombre editado", null, null, 1.0, null, null, null, null, null, null, null, null, null, "GENERIC", 5,
            )
            seeder(db).ensureSeeded()
            assertEquals("Nombre editado", db.productQueries.selectByBarcode("generic:1").executeAsOne().name)
        }
    }
}
