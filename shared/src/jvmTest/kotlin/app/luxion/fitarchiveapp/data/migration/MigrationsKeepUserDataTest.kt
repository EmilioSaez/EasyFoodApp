package app.luxion.fitarchiveapp.data.migration

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.data.migration.MigrationTestKit.use
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Para cada versión antigua (v1 … última-1): crea la base con el esquema de esa versión, la rellena con datos de todas
 * las tablas ([RepresentativeUserData]), la migra hasta la última versión y comprueba que no se ha perdido nada, que
 * la estructura es la de una base nueva y que la app puede leer y seguir escribiendo.
 */
class MigrationsKeepUserDataTest {
    @Test
    fun everyOldVersionMigratesWithoutLosingData() {
        val failures = mutableListOf<String>()
        for (version in MigrationTestKit.legacyVersions) {
            val problems = runCatching { checkVersion(version) }
                .getOrElse { listOf("excepción: $it") }
            problems.forEach { failures += "[desde v$version] $it" }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun legacyVersionsCoverTheWholeMigrationChain() {
        // Si alguien añade 11.sqm, latestVersion sube y esta cadena crece sola.
        assertEquals((1 until MigrationTestKit.latestVersion).toList(), MigrationTestKit.legacyVersions.toList())
        assertTrue(MigrationTestKit.latestVersion >= 11)
    }

    private fun checkVersion(version: Int): List<String> = MigrationTestKit.legacyDatabase(version).use { driver ->
        RepresentativeUserData.seed(driver, version)
        val before = MigrationTestKit.snapshot(driver)
        val problems = mutableListOf<String>()
        problems += check(before.values.sumOf { it.size } > 0) { "la siembra no insertó ninguna fila" }

        MigrationTestKit.migrateToLatest(driver, from = version)

        problems += MigrationTestKit.differences(before, MigrationTestKit.snapshot(driver))
        runCatching { MigrationTestKit.assertSameSchemaAsFresh(driver) }
            .onFailure { problems += "estructura distinta a una base nueva: ${it.message}" }
        problems += appCanReadAndWrite(driver, before)
        problems
    }

    private inline fun check(ok: Boolean, message: () -> String): List<String> = if (ok) emptyList() else listOf(message())

    /** Lo que hace la app tras migrar: leer con las consultas generadas, buscar por texto y seguir insertando. */
    private fun appCanReadAndWrite(
        driver: JdbcSqliteDriver,
        before: Map<String, List<Map<String, String>>>,
    ): List<String> {
        val db = EasyFoodDatabase(driver)
        val problems = mutableListOf<String>()
        fun count(table: String) = before[table]?.size ?: 0

        fun expect(what: String, expected: Int, actual: Int) {
            if (expected != actual) problems += "$what: esperadas $expected y hay $actual"
        }
        expect("alimentos propios", count("CustomFoodEntity"), db.customFoodQueries.selectAll().executeAsList().size)
        expect("diario", count("DiaryEntryEntity"), db.diaryEntryQueries.selectAll().executeAsList().size)
        expect("favoritos", count("FavoriteFoodEntity"), db.favoriteFoodQueries.selectAll().executeAsList().size)
        expect("pesos", count("WeightEntryEntity"), db.weightEntryQueries.selectAll().executeAsList().size)
        expect("recetas", count("RecipeEntity"), db.recipeQueries.selectAll().executeAsList().size)
        expect("ingredientes", count("RecipeIngredientEntity"), db.recipeQueries.selectAllIngredients().executeAsList().size)
        expect("agua", count("WaterEntity"), db.waterQueries.selectAll().executeAsList().size)
        expect("productos", count("ProductEntity"), db.productQueries.selectAll().executeAsList().size)
        expect("objetivo diario", count("DailyGoalEntity"), if (db.dailyGoalQueries.select().executeAsOneOrNull() != null) 1 else 0)
        expect("objetivo de peso", count("WeightGoalEntity"), if (db.weightEntryQueries.selectGoal().executeAsOneOrNull() != null) 1 else 0)
        expect("objetivo de agua", count("WaterGoalEntity"), if (db.waterQueries.selectGoal().executeAsOneOrNull() != null) 1 else 0)
        // meals_per_day: la fila sembrada existe desde la v8; antes no hay nada que conservar.
        expect("ajustes", count("SettingsEntity"), if (db.settingsQueries.selectMealsPerDay().executeAsOneOrNull() != null) 1 else 0)

        // Búsqueda de texto: el índice FTS5 debe cubrir exactamente la caché de productos (también la sembrada en v1).
        val indexed = MigrationTestKit.scalar(driver, "SELECT count(*) FROM ProductFts")
        val cached = MigrationTestKit.scalar(driver, "SELECT count(*) FROM ProductEntity")
        if (indexed != cached) problems += "FTS: $indexed filas indexadas para $cached productos"
        val hits = MigrationTestKit.scalar(driver, "SELECT count(*) FROM ProductFts WHERE ProductFts MATCH 'leche'")
        if (hits != 1L) problems += "FTS: buscar 'leche' da $hits resultados, esperado 1"

        // Los ids autoincrementales no se reutilizan tras migrar (el diario se reconstruye en 7.sqm).
        val maxDiary = MigrationTestKit.scalar(driver, "SELECT max(id) FROM DiaryEntryEntity")
        db.diaryEntryQueries.insert("2026-11-10", "LUNCH", null, "Nuevo", 1.0, "GRAMS", null, null, null, null, null, 1, 0)
        val newId = db.diaryEntryQueries.lastInsertedId().executeAsOne()
        if (newId <= maxDiary) problems += "diario: el id nuevo $newId no supera el máximo previo $maxDiary"
        db.diaryEntryQueries.insert("2026-11-10", "LATE_SNACK", null, "Recena", 1.0, "GRAMS", null, null, null, null, null, 2, 0)
        return problems
    }
}
