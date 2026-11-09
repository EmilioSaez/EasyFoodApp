package app.luxion.fitarchiveapp.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.NewDiaryEntry
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightDiaryStoreTest {
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

    private val nutella = NewDiaryEntry(
        date = "2026-11-04", meal = MealType.BREAKFAST, barcode = "3017620422003", name = "Nutella",
        quantity = Quantity(30.0, QuantityUnit.GRAMS), servingSizeG = 15.0,
        nutrients = Nutrients(kcal = 539.0, proteinG = 6.3, carbsG = 57.5, fatG = 30.9),
    )

    @Test
    fun roundTripsEntry() = runTest {
        val id = store.add(nutella, 100L)
        val e = store.find(id)!!
        assertEquals(id, e.id)
        assertEquals("2026-11-04", e.date)
        assertEquals(MealType.BREAKFAST, e.meal)
        assertEquals("Nutella", e.name)
        assertEquals(Quantity(30.0, QuantityUnit.GRAMS), e.quantity)
        assertEquals(nutella.nutrients, e.nutrients)
        assertEquals(100L, e.createdAtMillis)
    }

    @Test
    fun roundTripsNullsAndManualEntryWithoutBarcode() = runTest {
        val manual = nutella.copy(barcode = null, servingSizeG = null, nutrients = Nutrients(null, null, null, null))
        val e = store.find(store.add(manual, 1L))!!
        assertNull(e.barcode)
        assertNull(e.servingSizeG)
        assertEquals(Nutrients(null, null, null, null), e.nutrients)
    }

    @Test
    fun missingEntryIsNull() = runTest {
        assertNull(store.find(99L))
    }

    @Test
    fun entriesAreFilteredByDayAndOrderedByMealThenCreation() = runTest {
        store.add(nutella.copy(meal = MealType.SNACK, name = "Snack"), 1L)
        store.add(nutella.copy(meal = MealType.BREAKFAST, name = "B2"), 3L)
        store.add(nutella.copy(meal = MealType.BREAKFAST, name = "B1"), 2L)
        store.add(nutella.copy(date = "2026-11-05", name = "Otro dia"), 4L)

        assertEquals(listOf("B1", "B2", "Snack"), store.entriesForDate("2026-11-04").map { it.name })
        assertEquals(listOf("Otro dia"), store.entriesForDate("2026-11-05").map { it.name })
        assertTrue(store.entriesForDate("2026-11-06").isEmpty())
    }

    @Test
    fun updateChangesMealAndQuantity() = runTest {
        val id = store.add(nutella, 1L)
        assertTrue(store.update(id, MealType.DINNER, Quantity(2.0, QuantityUnit.SERVINGS)))
        val e = store.find(id)!!
        assertEquals(MealType.DINNER, e.meal)
        assertEquals(Quantity(2.0, QuantityUnit.SERVINGS), e.quantity)
        assertEquals("Nutella", e.name)
    }

    @Test
    fun updateAndDeleteReportMissingEntries() = runTest {
        assertFalse(store.update(7L, MealType.LUNCH, Quantity(1.0, QuantityUnit.GRAMS)))
        assertFalse(store.delete(7L))
    }

    @Test
    fun deleteRemovesOnlyThatEntry() = runTest {
        val a = store.add(nutella, 1L)
        val b = store.add(nutella.copy(name = "B"), 2L)
        assertTrue(store.delete(a))
        assertNull(store.find(a))
        assertEquals(listOf(b), store.entriesForDate("2026-11-04").map { it.id })
    }

    @Test
    fun totalsScaleGramsAndServings() = runTest {
        val grams = store.find(store.add(nutella, 1L))!!
        assertEquals(161.7, grams.totals.kcal!!, 1e-9)
        assertEquals(9.27, grams.totals.fatG!!, 1e-9)

        val servings = store.find(store.add(nutella.copy(quantity = Quantity(2.0, QuantityUnit.SERVINGS)), 2L))!!
        assertEquals(30.0, servings.grams)
        assertEquals(161.7, servings.totals.kcal!!, 1e-9)
    }

    @Test
    fun totalsAreNullWhenServingSizeOrNutrientIsUnknown() = runTest {
        val noServing = store.find(
            store.add(nutella.copy(quantity = Quantity(1.0, QuantityUnit.SERVINGS), servingSizeG = null), 1L),
        )!!
        assertNull(noServing.grams)
        assertNull(noServing.totals.kcal)

        val noKcal = store.find(store.add(nutella.copy(nutrients = Nutrients(null, 1.0, 1.0, 1.0)), 2L))!!
        assertNull(noKcal.totals.kcal)
        assertEquals(0.3, noKcal.totals.proteinG!!, 1e-9)
    }

    @Test
    fun rejectsInvalidQuantity() {
        assertFailsWith<IllegalArgumentException> { Quantity(0.0, QuantityUnit.GRAMS) }
        assertFailsWith<IllegalArgumentException> { Quantity(-1.0, QuantityUnit.SERVINGS) }
        assertFailsWith<IllegalArgumentException> { Quantity(Double.NaN, QuantityUnit.GRAMS) }
    }

    @Test
    fun recentFoodsReturnsLatestEntryOfEachDistinctFoodNewestFirst() = runTest {
        store.add(nutella.copy(name = "Nutella vieja"), 100L)
        store.add(nutella.copy(barcode = "8410000000001", name = "Galletas"), 200L)
        store.add(nutella.copy(barcode = null, name = "Mi pan"), 300L)
        store.add(nutella.copy(barcode = null, name = "MI PAN"), 350L)
        store.add(nutella, 400L)

        val recent = store.recentFoods(10)
        assertEquals(listOf("Nutella", "MI PAN", "Galletas"), recent.map { it.name })
        assertEquals(listOf("Nutella", "MI PAN"), store.recentFoods(2).map { it.name })
    }

    @Test
    fun daySummariesAddKcalPerDayInRangeAndIgnoreUnknownData() = runTest {
        val per100 = Nutrients(kcal = 200.0, proteinG = 1.0, carbsG = 1.0, fatG = 1.0)
        fun entry(date: String, qty: Quantity, nutrients: Nutrients = per100, serving: Double? = null) =
            nutella.copy(date = date, quantity = qty, nutrients = nutrients, servingSizeG = serving)
        store.add(entry("2026-11-03", Quantity(100.0, QuantityUnit.GRAMS)), 1)
        store.add(entry("2026-11-04", Quantity(100.0, QuantityUnit.GRAMS)), 2) // 200
        store.add(entry("2026-11-04", Quantity(2.0, QuantityUnit.SERVINGS), serving = 50.0), 3) // 200
        store.add(entry("2026-11-04", Quantity(1.0, QuantityUnit.SERVINGS)), 4) // porción sin tamaño: no suma
        store.add(entry("2026-11-05", Quantity(100.0, QuantityUnit.GRAMS), Nutrients(null, null, null, null)), 5) // sin kcal
        store.add(entry("2026-11-09", Quantity(100.0, QuantityUnit.GRAMS)), 6)

        val summaries = store.daySummaries("2026-11-04", "2026-11-08")

        assertEquals(listOf("2026-11-04", "2026-11-05"), summaries.map { it.date })
        assertEquals(400.0, summaries[0].kcal, 1e-9)
        assertEquals(0.0, summaries[1].kcal, 1e-9) // hay registro aunque no sume kcal
        assertEquals(emptyList(), store.daySummaries("2026-12-01", "2026-12-31"))
    }
}
