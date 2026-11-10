package app.luxion.fitarchiveapp.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.NewDiaryEntry
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import app.luxion.fitarchiveapp.model.QuickEntry
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Entradas rápidas del diario (kcal y macros sin alimento). */
class QuickEntryStoreTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var store: SqlDelightDiaryStore

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(driver)
        store = SqlDelightDiaryStore(EasyFoodDatabase(driver).diaryEntryQueries)
    }

    @AfterTest
    fun tearDown() = driver.close()

    private val food = NewDiaryEntry(
        "2026-11-04", MealType.BREAKFAST, "123", "Avena", Quantity(40.0, QuantityUnit.GRAMS), null,
        Nutrients(370.0, 13.0, 60.0, 7.0),
    )

    @Test
    fun roundTripsAQuickEntryWithOptionalMacros() = runTest {
        val id = store.add(QuickEntry.new("2026-11-04", MealType.LUNCH, "Menú", 850.0, 30.0, null, 40.0), 7L)
        val e = store.find(id)!!
        assertTrue(e.quick)
        assertEquals("Menú", e.name)
        assertNull(e.barcode)
        assertEquals(Quantity(100.0, QuantityUnit.GRAMS), e.quantity)
        assertEquals(Nutrients(850.0, 30.0, null, 40.0), e.nutrients)
        assertEquals(850.0, e.totals.kcal)
        assertNull(e.totals.carbsG)
        assertFalse(store.find(store.add(food, 1L))!!.quick)
    }

    @Test
    fun quickEntriesCountInTheDailyKcal() = runTest {
        store.add(food, 1L)
        store.add(QuickEntry.new("2026-11-04", MealType.DINNER, "", 500.0, null, null, null), 2L)
        assertEquals(148.0 + 500.0, store.daySummaries("2026-11-04", "2026-11-04").single().kcal, 1e-9)
    }

    @Test
    fun quickEntriesAreNotRecentFoods() = runTest {
        store.add(food, 1L)
        store.add(QuickEntry.new("2026-11-04", MealType.DINNER, "Cena fuera", 900.0, null, null, null), 2L)
        assertEquals(listOf("Avena"), store.recentFoods(10).map { it.name })
    }

    @Test
    fun updateQuickChangesMealNameAndTotalsOnlyForQuickEntries() = runTest {
        val quick = store.add(QuickEntry.new("2026-11-04", MealType.LUNCH, "Menú", 850.0, 30.0, 80.0, 40.0), 1L)
        val normal = store.add(food, 2L)

        assertTrue(store.updateQuick(quick, MealType.DINNER, "Cena", Nutrients(700.0, null, 50.0, null)))
        val e = store.find(quick)!!
        assertEquals(MealType.DINNER, e.meal)
        assertEquals("Cena", e.name)
        assertEquals(Nutrients(700.0, null, 50.0, null), e.nutrients)
        assertEquals("2026-11-04", e.date)
        assertEquals(1L, e.createdAtMillis)

        assertFalse(store.updateQuick(normal, MealType.DINNER, "X", Nutrients(1.0, 1.0, 1.0, 1.0)))
        assertEquals(food.nutrients, store.find(normal)!!.nutrients)
        assertFalse(store.updateQuick(999L, MealType.DINNER, "X", Nutrients(1.0, 1.0, 1.0, 1.0)))
    }

    @Test
    fun quickEntriesCanBeDeleted() = runTest {
        val id = store.add(QuickEntry.new("2026-11-04", MealType.LUNCH, "Menú", 850.0, null, null, null), 1L)
        assertTrue(store.delete(id))
        assertNull(store.find(id))
    }
}
