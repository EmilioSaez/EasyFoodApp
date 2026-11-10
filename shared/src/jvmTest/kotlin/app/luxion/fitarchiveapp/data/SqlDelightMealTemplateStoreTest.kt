package app.luxion.fitarchiveapp.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.NewDiaryEntry
import app.luxion.fitarchiveapp.model.NewMealTemplate
import app.luxion.fitarchiveapp.model.NewRecipe
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Product
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import app.luxion.fitarchiveapp.model.RecipeIngredient
import app.luxion.fitarchiveapp.model.TemplateItem
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightMealTemplateStoreTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: EasyFoodDatabase
    private lateinit var store: SqlDelightMealTemplateStore

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(driver)
        db = EasyFoodDatabase(driver)
        store = SqlDelightMealTemplateStore(db.mealTemplateQueries)
    }

    @AfterTest
    fun tearDown() = driver.close()

    private val oats = Nutrients(380.0, 13.0, 60.0, 7.0)
    private val milk = Nutrients(46.0, 3.0, 4.8, 1.6)
    private fun grams(name: String, g: Double, n: Nutrients = oats, barcode: String? = "111") =
        TemplateItem(barcode, name, Quantity(g, QuantityUnit.GRAMS), null, n)

    private fun count(table: String): Long = driver.executeQuery(
        null, "SELECT COUNT(*) FROM $table", { c -> QueryResult.Value(if (c.next().value) c.getLong(0) else null) }, 0,
    ).value ?: 0L

    @Test
    fun startsEmpty() = runTest {
        assertEquals(emptyList(), store.all())
        assertNull(store.find(1))
    }

    @Test
    fun createsAndReadsBackItemsInOrderKeepingQuantityUnitAndNutrients() = runTest {
        val items = listOf(
            grams("Avena", 40.0),
            TemplateItem(null, "Yogur propio", Quantity(2.0, QuantityUnit.SERVINGS), 125.0, milk),
            TemplateItem("generic:7", "Plátano", Quantity(100.0, QuantityUnit.GRAMS), null, Nutrients(null, null, null, null)),
        )
        val id = store.create(NewMealTemplate("  Mi desayuno  ", items), 10L)

        val read = store.find(id)!!
        assertEquals("Mi desayuno", read.name)
        assertEquals(items, read.items)
        assertEquals(10L, read.createdAtMillis)
        assertEquals(listOf(read), store.all())
    }

    @Test
    fun allIsSortedByNameIgnoringCase() = runTest {
        store.create(NewMealTemplate("cena ligera", listOf(grams("a", 1.0))), 1L)
        store.create(NewMealTemplate("Almuerzo", listOf(grams("a", 1.0))), 2L)
        store.create(NewMealTemplate("Desayuno", listOf(grams("a", 1.0))), 3L)
        assertEquals(listOf("Almuerzo", "cena ligera", "Desayuno"), store.all().map { it.name })
    }

    @Test
    fun updateReplacesNameAndItemsAndKeepsCreationDate() = runTest {
        val id = store.create(NewMealTemplate("Desayuno", listOf(grams("Avena", 40.0), grams("Leche", 200.0, milk, "222"))), 10L)
        assertTrue(store.update(id, NewMealTemplate("Desayuno 2", listOf(grams("Pan", 60.0))), 20L))
        val read = store.find(id)!!
        assertEquals("Desayuno 2", read.name)
        assertEquals(listOf("Pan"), read.items.map { it.name })
        assertEquals(10L, read.createdAtMillis)
        assertEquals(20L, read.updatedAtMillis)
        assertEquals(1L, count("MealTemplateItemEntity"))
        assertFalse(store.update(999, NewMealTemplate("X", listOf(grams("a", 1.0))), 30L))
    }

    @Test
    fun renameKeepsTheItems() = runTest {
        val id = store.create(NewMealTemplate("Desayuno", listOf(grams("Avena", 40.0))), 10L)
        assertTrue(store.rename(id, "  Mi desayuno de siempre ", 20L))
        val read = store.find(id)!!
        assertEquals("Mi desayuno de siempre", read.name)
        assertEquals(listOf("Avena"), read.items.map { it.name })
        assertEquals(20L, read.updatedAtMillis)
        assertFalse(store.rename(999, "X", 30L))
        assertFailsWith<IllegalArgumentException> { store.rename(id, "   ", 40L) }
    }

    @Test
    fun deleteRemovesTheTemplateAndItsItemsOnly() = runTest {
        val a = store.create(NewMealTemplate("A", listOf(grams("x", 1.0), grams("y", 2.0))), 1L)
        val b = store.create(NewMealTemplate("B", listOf(grams("z", 3.0))), 2L)
        assertTrue(store.delete(a))
        assertFalse(store.delete(a))
        assertNull(store.find(a))
        assertEquals(listOf(b), store.all().map { it.id })
        assertEquals(1L, count("MealTemplateItemEntity"))
    }

    @Test
    fun invalidTemplatesAreRejectedByTheModel() {
        assertFailsWith<IllegalArgumentException> { NewMealTemplate("  ", listOf(grams("a", 1.0))) }
        assertFailsWith<IllegalArgumentException> { NewMealTemplate("Vacía", emptyList()) }
    }

    @Test
    fun addTemplateWritesOneDiaryEntryPerItemInOrderWithTheirQuantities() = runTest {
        val diary = SqlDelightDiaryStore(db.diaryEntryQueries)
        val servings = TemplateItem(null, "Yogur", Quantity(2.0, QuantityUnit.SERVINGS), 125.0, milk)
        val id = store.create(NewMealTemplate("Desayuno", listOf(grams("Avena", 40.0), servings)), 1L)

        val n = diary.addTemplate(store.find(id)!!, "2026-11-04", MealType.BREAKFAST, 1_000L)

        assertEquals(2, n)
        val rows = diary.entriesForDate("2026-11-04")
        assertEquals(listOf("Avena", "Yogur"), rows.map { it.name })
        assertEquals(listOf(MealType.BREAKFAST, MealType.BREAKFAST), rows.map { it.meal })
        assertEquals(listOf(Quantity(40.0, QuantityUnit.GRAMS), Quantity(2.0, QuantityUnit.SERVINGS)), rows.map { it.quantity })
        assertEquals(125.0, rows[1].servingSizeG)
        assertEquals(oats, rows[0].nutrients)
        // La plantilla no cambia al añadirla, y se puede añadir otra vez a otro día.
        assertEquals(2, store.find(id)!!.items.size)
        diary.addTemplate(store.find(id)!!, "2026-11-05", MealType.SNACK, 2_000L)
        assertEquals(2, diary.entriesForDate("2026-11-05").size)
    }

    // --- Migración v11 -> v12 (11.sqm) ---

    @Test
    fun migrationFromV11CreatesTheTablesIdempotentlyAndKeepsExistingData() = runTest {
        // Estado v11: con todos los datos de usuario, pero sin las tablas de plantillas.
        SqlDelightProductStore(db.productQueries).save(Product("2", "Leche", null, null, milk), 1L)
        val diary = SqlDelightDiaryStore(db.diaryEntryQueries)
        diary.add(NewDiaryEntry("2026-11-04", MealType.LUNCH, "2", "Leche", Quantity(200.0, QuantityUnit.GRAMS), null, milk), 5L)
        SqlDelightRecipeStore(db.recipeQueries).create(
            NewRecipe("Gachas", 2, listOf(RecipeIngredient("2", "Leche", 200.0, milk))), 6L,
        )
        SqlDelightFavoriteStore(db.favoriteFoodQueries).setFavorite("2", true, 7L)
        driver.execute(null, "DROP TABLE MealTemplateItemEntity", 0)
        driver.execute(null, "DROP TABLE MealTemplateEntity", 0)

        EasyFoodDatabase.Schema.migrate(driver, 11, EasyFoodDatabase.Schema.version)
        EasyFoodDatabase.Schema.migrate(driver, 11, EasyFoodDatabase.Schema.version) // idempotente

        assertTrue(EasyFoodDatabase.Schema.version >= 12)
        val after = EasyFoodDatabase(driver)
        assertEquals(listOf("Leche"), SqlDelightDiaryStore(after.diaryEntryQueries).entriesForDate("2026-11-04").map { it.name })
        assertEquals(listOf("Gachas"), SqlDelightRecipeStore(after.recipeQueries).all().map { it.name })
        assertEquals(listOf("2"), SqlDelightFavoriteStore(after.favoriteFoodQueries).keys())
        assertEquals(1L, count("ProductEntity"))
        val migrated = SqlDelightMealTemplateStore(after.mealTemplateQueries)
        assertEquals(emptyList(), migrated.all())
        val id = migrated.create(NewMealTemplate("Desayuno", listOf(grams("Leche", 200.0, milk, "2"))), 8L)
        assertEquals(listOf("Leche"), migrated.find(id)!!.items.map { it.name })
    }
}
