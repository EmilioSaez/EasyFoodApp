package app.luxion.fitarchiveapp.data.migration

import app.luxion.fitarchiveapp.data.SqlDelightDiaryStore
import app.luxion.fitarchiveapp.data.SqlDelightMealTemplateStore
import app.luxion.fitarchiveapp.data.migration.MigrationTestKit.use
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.NewMealTemplate
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import app.luxion.fitarchiveapp.model.TemplateItem
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 11.sqm crea las tablas de plantillas de comida (`MealTemplateEntity` y `MealTemplateItemEntity`). Es una migración
 * puramente aditiva: no puede tocar ninguna fila de las tablas que ya existían.
 */
class Migration11Test {
    @Test
    fun v11HasNoTemplateTablesAndMigrationKeepsEveryExistingRow() = MigrationTestKit.legacyDatabase(11).use { d ->
        assertFalse(MigrationTestKit.hasTable(d, "MealTemplateEntity"))
        RepresentativeUserData.seed(d, 11)
        val before = MigrationTestKit.snapshot(d)
        assertTrue(before.getValue("DiaryEntryEntity").isNotEmpty())

        MigrationTestKit.migrateToLatest(d, from = 11)

        MigrationTestKit.assertPreserved(before, MigrationTestKit.snapshot(d))
        MigrationTestKit.assertSameSchemaAsFresh(d)
        assertEquals(0, MigrationTestKit.scalar(d, "SELECT COUNT(*) FROM MealTemplateEntity"))
        assertEquals(0, MigrationTestKit.scalar(d, "SELECT COUNT(*) FROM MealTemplateItemEntity"))
    }

    @Test
    fun runningTheMigrationTwiceIsHarmlessAndTemplatesStayUsable() = runTest {
        MigrationTestKit.legacyDatabase(11).use { d ->
            RepresentativeUserData.seed(d, 11)
            MigrationTestKit.migrateToLatest(d, from = 11)
            val store = SqlDelightMealTemplateStore(EasyFoodDatabase(d).mealTemplateQueries)
            val id = store.create(
                NewMealTemplate(
                    "Desayuno",
                    listOf(TemplateItem("8410000000017", "Leche entera", Quantity(2.0, QuantityUnit.SERVINGS), 250.0, Nutrients(64.0, 3.1, 4.7, 3.6))),
                ),
                1L,
            )
            val after = MigrationTestKit.snapshot(d)

            // 12.sqm (ALTER TABLE ADD COLUMN) no es idempotente: se quita su columna para repetir solo la 11.
            d.execute(null, "ALTER TABLE DiaryEntryEntity DROP COLUMN quick", 0)
            MigrationTestKit.migrateToLatest(d, from = 11) // idempotente: no vacía ni duplica nada

            MigrationTestKit.assertPreserved(after, MigrationTestKit.snapshot(d))
            assertEquals(listOf("Leche entera"), store.find(id)!!.items.map { it.name })
            // Los datos de siempre siguen ahí y se pueden leer con los almacenes de la app.
            assertTrue(SqlDelightDiaryStore(EasyFoodDatabase(d).diaryEntryQueries).entriesForDate("2026-11-01").isNotEmpty())
            assertEquals(MealType.BREAKFAST, SqlDelightDiaryStore(EasyFoodDatabase(d).diaryEntryQueries).entriesForDate("2026-11-01").first().meal)
        }
    }
}
