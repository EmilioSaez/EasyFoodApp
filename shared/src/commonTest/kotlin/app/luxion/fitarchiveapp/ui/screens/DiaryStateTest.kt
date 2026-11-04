package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.data.DiaryStore
import app.luxion.fitarchiveapp.data.GoalStore
import app.luxion.fitarchiveapp.model.DailyGoals
import app.luxion.fitarchiveapp.model.DiaryEntry
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.NewDiaryEntry
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import app.luxion.fitarchiveapp.ui.components.DataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val DAY = "2026-11-04"

private val oats = Nutrients(kcal = 370.0, proteinG = 13.0, carbsG = 60.0, fatG = 7.0)

private fun entry(
    id: Long,
    meal: MealType = MealType.BREAKFAST,
    barcode: String? = "8410000000001",
    quantity: Quantity = Quantity(100.0, QuantityUnit.GRAMS),
    nutrients: Nutrients = oats,
    servingSizeG: Double? = null,
    date: String = DAY,
) = DiaryEntry(id, date, meal, barcode, "Alimento $id", quantity, servingSizeG, nutrients, createdAtMillis = id)

private class FakeDiary(initial: List<DiaryEntry> = emptyList()) : DiaryStore {
    val rows = initial.toMutableList()
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    override suspend fun entriesForDate(date: String) = rows.filter { it.date == date }
    override suspend fun find(id: Long) = rows.firstOrNull { it.id == id }
    override suspend fun add(entry: NewDiaryEntry, createdAtMillis: Long): Long {
        val id = nextId++
        rows += DiaryEntry(
            id, entry.date, entry.meal, entry.barcode, entry.name, entry.quantity,
            entry.servingSizeG, entry.nutrients, createdAtMillis,
        )
        return id
    }

    override suspend fun update(id: Long, meal: MealType, quantity: Quantity): Boolean {
        val i = rows.indexOfFirst { it.id == id }
        if (i < 0) return false
        rows[i] = rows[i].copy(meal = meal, quantity = quantity)
        return true
    }

    override suspend fun delete(id: Long) = rows.removeAll { it.id == id }
}

private class FakeGoals(var goals: DailyGoals = DailyGoals()) : GoalStore {
    override suspend fun get() = goals
    override suspend fun save(goals: DailyGoals) { this.goals = goals }
    override suspend fun clear() { goals = DailyGoals() }
}

class DiaryStateTest {
    @Test
    fun emptyDayHasFourEmptyMealsAndZeroTotals() {
        val state = buildDiaryUiState(DAY, emptyList(), DailyGoals())
        assertTrue(state.isEmpty)
        assertEquals(MealType.entries, state.meals.map { it.meal })
        assertEquals(0.0, state.consumedKcal)
        assertNull(state.goalKcal)
    }

    @Test
    fun totalsAreSplitByMealAndAddedUp() {
        val state = buildDiaryUiState(
            DAY,
            listOf(
                entry(1, MealType.BREAKFAST, quantity = Quantity(50.0, QuantityUnit.GRAMS)),
                entry(2, MealType.BREAKFAST, quantity = Quantity(100.0, QuantityUnit.GRAMS)),
                entry(3, MealType.DINNER, quantity = Quantity(200.0, QuantityUnit.GRAMS)),
            ),
            DailyGoals(),
        )
        val breakfast = state.meals.first { it.meal == MealType.BREAKFAST }
        assertEquals(555.0, breakfast.kcal, 1e-9)
        assertEquals(19.5, breakfast.proteinG, 1e-9)
        assertEquals(2, breakfast.entries.size)
        assertTrue(state.meals.first { it.meal == MealType.LUNCH }.entries.isEmpty())
        assertEquals(555.0 + 740.0, state.consumedKcal, 1e-9)
        assertFalse(state.isEmpty)
    }

    @Test
    fun macroProgressCarriesGoalsOrNull() {
        val state = buildDiaryUiState(
            DAY, listOf(entry(1)), DailyGoals(kcal = 2000, proteinG = 130, carbsG = null, fatG = 60),
        )
        assertEquals(2000, state.goalKcal)
        val byLabel = state.macros.associateBy { it.label }
        assertEquals(13.0, byLabel.getValue("Proteína").consumedG, 1e-9)
        assertEquals(130.0, byLabel.getValue("Proteína").goalG)
        assertNull(byLabel.getValue("Hidratos").goalG)
        assertEquals(60.0, byLabel.getValue("Grasa").goalG)
    }

    @Test
    fun entriesWithoutDataDoNotAddButStayListed() {
        val servingsNoSize = entry(1, quantity = Quantity(2.0, QuantityUnit.SERVINGS), servingSizeG = null)
        val state = buildDiaryUiState(DAY, listOf(servingsNoSize, entry(2)), DailyGoals())
        val row = state.meals.first().entries.first()
        assertNull(row.kcal)
        assertEquals(370.0, state.consumedKcal, 1e-9)
        assertEquals(2, state.meals.first().entries.size)
    }

    @Test
    fun implausibleEnergyIsFlaggedAsDoubtful() {
        val doubtful = entry(1, nutrients = Nutrients(kcal = 1500.0, proteinG = 1.0, carbsG = 1.0, fatG = 1.0))
        val missing = entry(2, nutrients = Nutrients(kcal = null, proteinG = null, carbsG = null, fatG = null))
        val state = buildDiaryUiState(DAY, listOf(doubtful, missing, entry(3)), DailyGoals())
        assertEquals(listOf(true, true, false), state.meals.first().entries.map { it.doubtful })
    }

    @Test
    fun sourceIsDerivedFromTheKey() {
        assertEquals(DataSource.Custom, entry(1, barcode = null).dataSource())
        assertEquals(DataSource.Generic, entry(2, barcode = "generic:arroz").dataSource())
        assertEquals(DataSource.OpenFoodFacts, entry(3, barcode = "8410000000001").dataSource())
    }

    @Test
    fun quantityFormatting() {
        assertEquals("30 g", Quantity(30.0, QuantityUnit.GRAMS).format())
        assertEquals("1 porción", Quantity(1.0, QuantityUnit.SERVINGS).format())
        assertEquals("2 porciones", Quantity(2.0, QuantityUnit.SERVINGS).format())
    }

    @Test
    fun isoDateArithmetic() {
        assertEquals("2026-11-05", IsoDate.plusDays("2026-11-04", 1))
        assertEquals("2026-12-01", IsoDate.plusDays("2026-11-30", 1))
        assertEquals("2027-01-01", IsoDate.plusDays("2026-12-31", 1))
        assertEquals("2025-12-31", IsoDate.plusDays("2026-01-01", -1))
        assertEquals("2024-02-29", IsoDate.plusDays("2024-03-01", -1))
        assertEquals("2025-03-01", IsoDate.plusDays("2025-02-28", 1))
        assertEquals("2026-11-04", IsoDate.plusDays("2026-11-04", 0))
        assertEquals("2026-10-05", IsoDate.plusDays("2026-11-04", -30))
        assertEquals("4 de noviembre de 2026", IsoDate.long("2026-11-04"))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryPresenterTest {
    private fun TestScope.presenter(diary: FakeDiary, goals: FakeGoals = FakeGoals()) =
        DiaryPresenter(diary, goals, backgroundScope + UnconfinedTestDispatcher(testScheduler), DAY)

    @Test
    fun loadsTheDayWithGoals() = runTest {
        val p = presenter(FakeDiary(listOf(entry(1))), FakeGoals(DailyGoals(kcal = 2000)))
        assertTrue(p.state.value.loading)
        p.load()
        assertFalse(p.state.value.loading)
        assertEquals(370.0, p.state.value.consumedKcal, 1e-9)
        assertEquals(2000, p.state.value.goalKcal)
    }

    @Test
    fun shiftDayLoadsAnotherDate() = runTest {
        val diary = FakeDiary(listOf(entry(1), entry(2, date = "2026-11-03")))
        val p = presenter(diary)
        p.load()
        p.shiftDay(-1)
        assertEquals("2026-11-03", p.state.value.date)
        assertEquals(listOf(2L), p.state.value.meals.flatMap { it.entries }.map { it.id })
        p.shiftDay(1)
        assertEquals(DAY, p.state.value.date)
    }

    @Test
    fun updateQuantityRecomputesTotals() = runTest {
        val p = presenter(FakeDiary(listOf(entry(1))))
        p.load()
        p.updateQuantity(1, Quantity(200.0, QuantityUnit.GRAMS))
        assertEquals(740.0, p.state.value.consumedKcal, 1e-9)
    }

    @Test
    fun deleteAndUndoRestoreTheEntry() = runTest {
        val diary = FakeDiary(listOf(entry(1, MealType.LUNCH), entry(2, MealType.LUNCH)))
        val p = presenter(diary)
        p.load()
        p.delete(1)
        assertEquals(listOf(2L), diary.rows.map { it.id })
        assertEquals(370.0, p.state.value.consumedKcal, 1e-9)

        p.undoDelete()
        assertEquals(2, diary.rows.size)
        val restored = diary.rows.first { it.name == "Alimento 1" }
        assertEquals(MealType.LUNCH, restored.meal)
        assertEquals(1L, restored.createdAtMillis)
        assertEquals(740.0, p.state.value.consumedKcal, 1e-9)

        p.undoDelete() // sin nada que deshacer: no duplica
        assertEquals(2, diary.rows.size)
    }

    @Test
    fun deletingTheLastEntryLeavesTheDayEmpty() = runTest {
        val p = presenter(FakeDiary(listOf(entry(1))))
        p.load()
        p.delete(1)
        assertTrue(p.state.value.isEmpty)
    }
}
