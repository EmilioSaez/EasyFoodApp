package app.luxion.fitarchiveapp.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.model.NewRecipe
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.RecipeIngredient
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightRecipeStoreTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var store: SqlDelightRecipeStore

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(driver)
        store = SqlDelightRecipeStore(EasyFoodDatabase(driver).recipeQueries)
    }

    @AfterTest
    fun tearDown() = driver.close()

    private fun ing(name: String, grams: Double, barcode: String? = null, kcal: Double? = 100.0) =
        RecipeIngredient(barcode, name, grams, Nutrients(kcal, 1.0, 2.0, 3.0), servingSizeG = 30.0)

    private fun count(table: String): Long = driver.executeQuery(
        null, "SELECT count(*) FROM $table", { c -> QueryResult.Value(if (c.next().value) c.getLong(0) else null) }, 0,
    ).value ?: 0L

    @Test
    fun emptyAtStart() = runTest { assertEquals(emptyList(), store.all()) }

    @Test
    fun createsAndFindsKeepingIngredientOrderAndValues() = runTest {
        val id = store.create(
            NewRecipe("Paella", 4, listOf(ing("Arroz", 200.0, "generic:1"), ing("Aceite", 10.0), ing("Sal", 2.0, kcal = null))),
            10L,
        )
        val r = store.find(id)!!
        assertEquals("Paella", r.name)
        assertEquals(4, r.servings)
        assertEquals(listOf("Arroz", "Aceite", "Sal"), r.ingredients.map { it.name })
        assertEquals("generic:1", r.ingredients[0].barcode)
        assertNull(r.ingredients[1].barcode)
        assertNull(r.ingredients[2].nutrients.kcal)
        assertEquals(Nutrients(100.0, 1.0, 2.0, 3.0), r.ingredients[0].nutrients)
        assertEquals(30.0, r.ingredients[0].servingSizeG)
        assertEquals(10L, r.createdAtMillis)
        assertEquals(10L, r.updatedAtMillis)
    }

    @Test
    fun servingsAreOptional() = runTest {
        val id = store.create(NewRecipe("Ensalada", null, listOf(ing("Lechuga", 100.0))), 1L)
        assertNull(store.find(id)!!.servings)
    }

    @Test
    fun listsSortedByNameIgnoringCaseWithTheirOwnIngredients() = runTest {
        store.create(NewRecipe("zumo", null, listOf(ing("Naranja", 300.0))), 1L)
        store.create(NewRecipe("Arroz con leche", 2, listOf(ing("Arroz", 100.0), ing("Leche", 500.0))), 2L)
        val all = store.all()
        assertEquals(listOf("Arroz con leche", "zumo"), all.map { it.name })
        assertEquals(listOf("Arroz", "Leche"), all[0].ingredients.map { it.name })
        assertEquals(listOf("Naranja"), all[1].ingredients.map { it.name })
    }

    @Test
    fun updateReplacesIngredientsKeepingCreationDate() = runTest {
        val id = store.create(NewRecipe("Sopa", 2, listOf(ing("Caldo", 400.0), ing("Fideos", 50.0))), 10L)
        assertTrue(store.update(id, NewRecipe("Sopa de fideos", null, listOf(ing("Fideos", 80.0))), 20L))
        val r = store.find(id)!!
        assertEquals("Sopa de fideos", r.name)
        assertNull(r.servings)
        assertEquals(listOf("Fideos"), r.ingredients.map { it.name })
        assertEquals(80.0, r.ingredients[0].grams)
        assertEquals(10L, r.createdAtMillis)
        assertEquals(20L, r.updatedAtMillis)
        assertEquals(1L, count("RecipeIngredientEntity"))
    }

    @Test
    fun updateOfMissingRecipeReturnsFalse() = runTest {
        assertFalse(store.update(99, NewRecipe("X", null, listOf(ing("a", 1.0))), 1L))
    }

    @Test
    fun deleteRemovesRecipeAndOnlyItsIngredients() = runTest {
        val a = store.create(NewRecipe("A", null, listOf(ing("a1", 1.0), ing("a2", 2.0))), 1L)
        val b = store.create(NewRecipe("B", null, listOf(ing("b1", 1.0))), 1L)
        assertTrue(store.delete(a))
        assertFalse(store.delete(a))
        assertNull(store.find(a))
        assertEquals(listOf(b), store.all().map { it.id })
        assertEquals(1L, count("RecipeIngredientEntity"))
    }

    @Test
    fun migrationFromV6CreatesTheRecipeTablesAndIsIdempotent() = runTest {
        // Estado v6: sin las tablas de recetas.
        driver.execute(null, "DROP TABLE RecipeIngredientEntity", 0)
        driver.execute(null, "DROP TABLE RecipeEntity", 0)

        assertTrue(EasyFoodDatabase.Schema.version >= 7)
        EasyFoodDatabase.Schema.migrate(driver, 6, EasyFoodDatabase.Schema.version)
        EasyFoodDatabase.Schema.migrate(driver, 6, EasyFoodDatabase.Schema.version)

        val migrated = SqlDelightRecipeStore(EasyFoodDatabase(driver).recipeQueries)
        val id = migrated.create(NewRecipe("Tortilla", 2, listOf(ing("Huevo", 120.0))), 1L)
        assertEquals(listOf("Huevo"), migrated.find(id)!!.ingredients.map { it.name })
    }

    @Test
    fun trimsTheName() = runTest {
        val id = store.create(NewRecipe("  Tortilla  ", null, listOf(ing("Huevo", 120.0))), 1L)
        assertEquals("Tortilla", store.find(id)!!.name)
    }
}
