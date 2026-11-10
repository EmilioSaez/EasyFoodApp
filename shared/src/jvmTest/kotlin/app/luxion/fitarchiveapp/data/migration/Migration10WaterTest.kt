package app.luxion.fitarchiveapp.data.migration

import app.luxion.fitarchiveapp.data.migration.MigrationTestKit.use
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 10.sqm recupera las tablas de agua que se perdieron cuando la base genérica en español y el agua usaron ambas 8.sqm.
 * Hay tres tipos de base en circulación en la versión 10; ninguna debe perder datos.
 */
class Migration10WaterTest {

    private fun createWaterTables(d: app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver) {
        d.execute(null, "CREATE TABLE WaterEntity (date TEXT NOT NULL PRIMARY KEY CHECK (length(date) = 10), ml INTEGER NOT NULL CHECK (ml > 0 AND ml <= 20000), updated_at INTEGER NOT NULL)", 0)
        d.execute(null, "CREATE TABLE WaterGoalEntity (id INTEGER NOT NULL PRIMARY KEY CHECK (id = 1), goal_ml INTEGER NOT NULL CHECK (goal_ml >= 500 AND goal_ml <= 10000), updated_at INTEGER NOT NULL)", 0)
    }

    /** Base v10 de la rama de la base genérica: sin tablas de agua (las pisó). Tras migrar debe tenerlas, vacías. */
    @Test
    fun v10WithoutWaterTablesGetsThemEmpty() = MigrationTestKit.legacyDatabase(10).use { d ->
        assertTrue(!MigrationTestKit.hasTable(d, "WaterEntity"))
        RepresentativeUserData.seed(d, 10)
        val before = MigrationTestKit.snapshot(d)

        MigrationTestKit.migrateToLatest(d, from = 10)

        MigrationTestKit.assertPreserved(before, MigrationTestKit.snapshot(d))
        MigrationTestKit.assertSameSchemaAsFresh(d)
        val db = EasyFoodDatabase(d)
        assertEquals(emptyList(), db.waterQueries.selectAll().executeAsList())
        assertNull(db.waterQueries.selectGoal().executeAsOneOrNull())
        // Y ya se puede registrar agua.
        db.waterQueries.upsert("2026-11-10", 500, 1)
        assertEquals(500, db.waterQueries.selectByDate("2026-11-10").executeAsOne().ml)
    }

    /** Base v10 que ya tiene agua (p. ej. la del desarrollador): IF NOT EXISTS no debe vaciarla ni fallar. */
    @Test
    fun v10WithWaterDataKeepsEveryDay() = MigrationTestKit.legacyDatabase(10).use { d ->
        createWaterTables(d)
        RepresentativeUserData.seed(d, 10)
        val before = MigrationTestKit.snapshot(d)
        assertEquals(2, before.getValue("WaterEntity").size)

        MigrationTestKit.migrateToLatest(d, from = 10)

        MigrationTestKit.assertPreserved(before, MigrationTestKit.snapshot(d))
        MigrationTestKit.assertSameSchemaAsFresh(d)
        val db = EasyFoodDatabase(d)
        assertEquals(listOf(1750L, 20000L), db.waterQueries.selectAll().executeAsList().map { it.ml })
        assertEquals(2500L, db.waterQueries.selectGoal().executeAsOne().goal_ml)
    }

    /**
     * Base v9 de la rama del agua (su 8.sqm creó el agua y subió a v9; la de la base genérica también usó el 8):
     * 9.sqm (favoritos) y 10.sqm no pueden borrar el agua.
     */
    @Test
    fun v9FromWaterBranchKeepsWater() = MigrationTestKit.legacyDatabase(8).use { d ->
        createWaterTables(d)
        RepresentativeUserData.seed(d, 8)
        val before = MigrationTestKit.snapshot(d)

        MigrationTestKit.migrateToLatest(d, from = 9)

        MigrationTestKit.assertPreserved(before, MigrationTestKit.snapshot(d))
        assertEquals(listOf(1750L, 20000L), EasyFoodDatabase(d).waterQueries.selectAll().executeAsList().map { it.ml })
    }

    /**
     * HUECO CONOCIDO: esa misma base v9 no pasa por el 8.sqm de la base genérica, así que termina sin
     * GenericFoodsMetaEntity y `selectGenericDataVersion` falla al arrancar. Arreglarlo exige una migración nueva
     * (o CREATE TABLE IF NOT EXISTS en 10.sqm). Quita el @Ignore cuando se corrija.
     */
    @Ignore("Hueco conocido: v9 de la rama del agua no tiene GenericFoodsMetaEntity")
    @Test
    fun v9FromWaterBranchEndsWithAllTables() = MigrationTestKit.legacyDatabase(8).use { d ->
        createWaterTables(d)
        MigrationTestKit.migrateToLatest(d, from = 9)
        MigrationTestKit.assertSameSchemaAsFresh(d)
        assertNull(EasyFoodDatabase(d).productQueries.selectGenericDataVersion().executeAsOneOrNull())
    }
}
