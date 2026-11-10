package app.luxion.fitarchiveapp.data.migration

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guardián de la regla «ningún cambio de esquema sin .sqm y sin test»:
 *  - Las migraciones numeran 1..N sin huecos ni duplicados (dos ramas con el mismo número se pisan en silencio).
 *  - Cada migración a partir de la 11 tiene su `Migration<N>Test` en este paquete (las 1..10 las cubren los tests de
 *    todas las versiones y [Migration10WaterTest]).
 */
class EverySqmHasATestTest {
    private val sqldelight = File("src/commonMain/sqldelight")
    private val testDir = File("src/jvmTest/kotlin/app/luxion/fitarchiveapp/data/migration")
    private val firstMigrationNeedingOwnTest = 11

    private fun sqmNumbers(): List<Pair<Int, File>> =
        sqldelight.walkTopDown().filter { it.isFile && it.extension == "sqm" }
            // migrations/1.sqm es de otra rama que usó el mismo número: SQLDelight la aplica junto a db/1.sqm.
            .filterNot { it.parentFile.name == "migrations" }
            .map { it.nameWithoutExtension.toInt() to it }.sortedBy { it.first }.toList()

    @Test
    fun sourcesAreWhereTheTestExpects() {
        assertTrue(sqldelight.isDirectory && testDir.isDirectory, "ejecuta los tests desde el módulo shared (${File(".").absolutePath})")
    }

    @Test
    fun migrationNumbersAreContiguousAndUnique() {
        val numbers = sqmNumbers().map { it.first }
        assertEquals((1..numbers.max()).toList(), numbers, "migraciones duplicadas o con huecos: $numbers")
        assertEquals(numbers.max() + 1, MigrationTestKit.latestVersion)
    }

    @Test
    fun everyNewMigrationHasItsOwnTest() {
        val missing = sqmNumbers().map { it.first }.filter { it >= firstMigrationNeedingOwnTest }
            .filterNot { File(testDir, "Migration${it}Test.kt").isFile }
        assertTrue(
            missing.isEmpty(),
            "Falta Migration<N>Test.kt en data/migration para ${missing.map { "$it.sqm" }} " +
                "(regla: ningún cambio de esquema sin .sqm y sin test; usa MigrationTestKit).",
        )
    }
}
