package app.luxion.fitarchiveapp.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Evita que dos ramas con el mismo número de migración se pisen sin que nadie lo note:
 * una base migrada desde v8 debe tener las mismas tablas que una creada de cero.
 */
class MigrationsCompletenessTest {
    private fun tables(driver: JdbcSqliteDriver): Set<String> = driver.executeQuery(
        null, "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'",
        { c ->
            val names = mutableSetOf<String>()
            while (c.next().value) names += c.getString(0)!!
            QueryResult.Value(names)
        }, 0,
    ).value

    @Test
    fun migratingFromV8CreatesEveryTableOfAFreshDatabase() {
        val fresh = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(fresh)
        val migrated = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(migrated)
        for (t in listOf("WaterEntity", "WaterGoalEntity", "FavoriteFoodEntity", "GenericFoodsMetaEntity", "MealTemplateItemEntity", "MealTemplateEntity", "ExerciseEntity", "ExerciseSettingsEntity")) {
            migrated.execute(null, "DROP TABLE $t", 0)
        }
        migrated.execute(null, "ALTER TABLE DiaryEntryEntity DROP COLUMN quick", 0) // la añade 12.sqm (no es idempotente)

        EasyFoodDatabase.Schema.migrate(migrated, 8, EasyFoodDatabase.Schema.version)

        assertEquals(tables(fresh), tables(migrated))
        fresh.close()
        migrated.close()
    }
}
