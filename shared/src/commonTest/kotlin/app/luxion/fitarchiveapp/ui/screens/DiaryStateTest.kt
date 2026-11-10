package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.data.DiaryStore
import app.luxion.fitarchiveapp.data.GoalStore
import app.luxion.fitarchiveapp.data.MealTemplateStore
import app.luxion.fitarchiveapp.domain.MealPlan
import app.luxion.fitarchiveapp.model.DailyGoals
import app.luxion.fitarchiveapp.model.DiaryEntry
import app.luxion.fitarchiveapp.model.MealTemplate
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.NewMealTemplate
import app.luxion.fitarchiveapp.model.NewDiaryEntry
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuickEntry
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
    var failing = false
    private fun check() { if (failing) throw IllegalStateException("db") }
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    override suspend fun entriesForDate(date: String): List<DiaryEntry> { check(); return rows.filter { it.date == date } }
    override suspend fun find(id: Long) = rows.firstOrNull { it.id == id }
    override suspend fun add(entry: NewDiaryEntry, createdAtMillis: Long): Long {
        val id = nextId++
        rows += DiaryEntry(
            id, entry.date, entry.meal, entry.barcode, entry.name, entry.quantity,
            entry.servingSizeG, entry.nutrients, createdAtMillis, entry.quick,
        )
        return id
    }

    override suspend fun update(id: Long, meal: MealType, quantity: Quantity): Boolean {
        check()
        val i = rows.indexOfFirst { it.id == id }
        if (i < 0) return false
        rows[i] = rows[i].copy(meal = meal, quantity = quantity)
        return true
    }

    override suspend fun updateQuick(id: Long, meal: MealType, name: String, totals: Nutrients): Boolean {
        val i = rows.indexOfFirst { it.id == id && it.quick }
        if (i < 0) return false
        rows[i] = rows[i].copy(meal = meal, name = name, nutrients = totals)
        return true
    }

    override suspend fun delete(id: Long): Boolean { check(); return rows.removeAll { it.id == id } }
}

private class FakeGoals(var goals: DailyGoals = DailyGoals()) : GoalStore {
    override suspend fun get() = goals
    override suspend fun save(goals: DailyGoals) { this.goals = goals }
    override suspend fun clear() { goals = DailyGoals() }
}

class DiaryStateTest {
    @Test
    fun emptyDayHasTheDefaultEmptyMealsAndZeroTotals() {
        val state = buildDiaryUiState(DAY, emptyList(), DailyGoals())
        assertTrue(state.isEmpty)
        assertEquals(MealPlan.preset(null), state.meals.map { it.meal })
        assertEquals(0.0, state.consumedKcal)
        assertNull(state.goalKcal)
    }

    @Test
    fun sectionsFollowTheMealsPerDayPreset() {
        for (n in MealPlan.options) {
            assertEquals(MealPlan.preset(n), buildDiaryUiState(DAY, emptyList(), DailyGoals(), n).meals.map { it.meal })
        }
    }

    @Test
    fun aMealOutsideThePresetWithEntriesKeepsItsSectionAndTotals() {
        val state = buildDiaryUiState(
            DAY,
            listOf(entry(1, MealType.SNACK, quantity = Quantity(100.0, QuantityUnit.GRAMS))),
            DailyGoals(),
            mealsPerDay = 3,
        )
        assertEquals(
            listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.SNACK, MealType.DINNER),
            state.meals.map { it.meal },
        )
        assertEquals(1, state.meals.first { it.meal == MealType.SNACK }.entries.size)
        assertEquals(state.meals.sumOf { it.kcal }, state.consumedKcal, 1e-9)
    }

    @Test
    fun emptyMealsOutsideThePresetAreHidden() {
        val state = buildDiaryUiState(DAY, emptyList(), DailyGoals(), mealsPerDay = 3)
        assertFalse(state.meals.any { it.meal == MealType.SNACK || it.meal == MealType.LATE_SNACK })
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
    fun goalIsTheSameOnPastTodayAndFutureDays() = runTest {
        val goals = DailyGoals(kcal = 2000, proteinG = 130, carbsG = 220, fatG = 60)
        val p = presenter(FakeDiary(listOf(entry(1))), FakeGoals(goals))
        val days = listOf(-400, -30, -1, 0, 1, 30, 400)
        for (offset in days) {
            p.load(IsoDate.plusDays(DAY, offset))
            val s = p.state.value
            assertEquals(2000, s.goalKcal, "kcal en el día $offset")
            assertEquals(
                listOf(130.0, 220.0, 60.0), s.macros.map { it.goalG }, "macros en el día $offset",
            )
        }
    }

    @Test
    fun changingTheGoalUpdatesEveryDayAndEmptyDaysToo() = runTest {
        val goals = FakeGoals(DailyGoals(kcal = 2000))
        val p = presenter(FakeDiary(listOf(entry(1), entry(2, date = "2026-11-10"))), goals)
        p.load()
        assertEquals(2000, p.state.value.goalKcal)

        goals.save(DailyGoals(kcal = 2300, proteinG = 150))
        for (date in listOf("2026-10-01", "2026-11-03", DAY, "2026-11-10", "2027-03-01")) {
            p.load(date)
            assertEquals(2300, p.state.value.goalKcal, "kcal el $date")
            assertEquals(150.0, p.state.value.macros.first().goalG, "proteína el $date")
        }
    }

    @Test
    fun updatingAnEntryKeepsTheGoalOfThatDay() = runTest {
        val p = presenter(FakeDiary(listOf(entry(1, date = "2026-11-03"))), FakeGoals(DailyGoals(kcal = 1800)))
        p.load("2026-11-03")
        p.updateQuantity(1, Quantity(200.0, QuantityUnit.GRAMS))
        assertEquals(1800, p.state.value.goalKcal)
        assertEquals("2026-11-03", p.state.value.date)
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

    @Test
    fun failedLoadShowsErrorStateAndRecoversOnRetry() = runTest {
        val diary = FakeDiary(listOf(entry(1)))
        val p = presenter(diary)
        diary.failing = true
        p.load()
        assertTrue(p.state.value.loadFailed)
        assertFalse(p.state.value.loading)
        diary.failing = false
        p.load()
        assertFalse(p.state.value.loadFailed)
        assertEquals(1, p.state.value.meals.sumOf { it.entries.size })
    }

    @Test
    fun failedDeleteReportsMessageAndKeepsTheDay() = runTest {
        val diary = FakeDiary(listOf(entry(1)))
        val p = presenter(diary)
        p.load()
        diary.failing = true
        p.delete(1)
        assertEquals(DiaryPresenter.DELETE_ERROR, p.state.value.message)
        assertFalse(p.state.value.loadFailed)
        p.consumeMessage()
        assertNull(p.state.value.message)
    }

    @Test
    fun failedUpdateReportsMessage() = runTest {
        val diary = FakeDiary(listOf(entry(1)))
        val p = presenter(diary)
        p.load()
        diary.failing = true
        p.updateQuantity(1, Quantity(50.0, QuantityUnit.GRAMS))
        assertEquals(DiaryPresenter.SAVE_ERROR, p.state.value.message)
    }

    private val prev = "2026-11-03"

    @Test
    fun copyMealDuplicatesItsEntriesWithTheirQuantitiesIntoTheTargetDay() = runTest {
        val grams = Quantity(35.0, QuantityUnit.GRAMS)
        val servings = Quantity(2.0, QuantityUnit.SERVINGS)
        val diary = FakeDiary(
            listOf(
                entry(1, MealType.LUNCH, quantity = grams),
                entry(2, MealType.LUNCH, quantity = servings, servingSizeG = 30.0),
                entry(3, MealType.DINNER),
            ),
        )
        val p = presenter(diary)
        p.load()
        p.copyMeal(MealType.LUNCH, prev, null)
        val copied = diary.rows.filter { it.date == prev }
        assertEquals(listOf(MealType.LUNCH, MealType.LUNCH), copied.map { it.meal })
        assertEquals(listOf(grams, servings), copied.map { it.quantity })
        assertEquals(30.0, copied[1].servingSizeG)
        assertTrue(copied[0].createdAtMillis < copied[1].createdAtMillis)
        assertEquals(3, diary.rows.count { it.date == DAY }) // el origen no cambia
        assertEquals(DAY, p.state.value.date)
        assertEquals("Copiado a 3 de noviembre de 2026: 2 alimentos", p.state.value.message)
    }

    @Test
    fun copyMealCanTargetAnotherMealOfTheSameDay() = runTest {
        val diary = FakeDiary(listOf(entry(1, MealType.BREAKFAST)))
        val p = presenter(diary)
        p.load()
        p.copyMeal(MealType.BREAKFAST, DAY, MealType.SNACK)
        assertEquals(listOf(MealType.BREAKFAST, MealType.SNACK), diary.rows.map { it.meal })
    }

    @Test
    fun copyingAMealOntoItselfIsRejected() = runTest {
        val diary = FakeDiary(listOf(entry(1, MealType.BREAKFAST)))
        val p = presenter(diary)
        p.load()
        p.copyMeal(MealType.BREAKFAST, DAY, null)
        p.copyMeal(MealType.BREAKFAST, DAY, MealType.BREAKFAST)
        p.copyDay(DAY)
        assertEquals(1, diary.rows.size)
        assertEquals(DiaryPresenter.COPY_SAME_ERROR, p.state.value.message)
    }

    @Test
    fun copyTargetErrorFlagsOnlyCopiesOntoThemselves() {
        val other = "2026-11-03"
        assertEquals(DiaryPresenter.COPY_SAME_ERROR, copyTargetError(MealType.LUNCH, DAY, DAY, null))
        assertEquals(DiaryPresenter.COPY_SAME_ERROR, copyTargetError(MealType.LUNCH, DAY, DAY, MealType.LUNCH))
        assertEquals(DiaryPresenter.COPY_SAME_ERROR, copyTargetError(null, DAY, DAY, null))
        assertEquals(null, copyTargetError(MealType.LUNCH, DAY, DAY, MealType.SNACK))
        assertEquals(null, copyTargetError(MealType.LUNCH, DAY, other, null))
        assertEquals(null, copyTargetError(null, DAY, other, null))
    }

    @Test
    fun copyDayCopiesEveryMealAndMapsMealsMissingFromThePreset() = runTest {
        val diary = FakeDiary(
            listOf(entry(1, MealType.BREAKFAST), entry(2, MealType.SNACK), entry(3, MealType.LATE_SNACK)),
        )
        val p = DiaryPresenter(diary, FakeGoals(), backgroundScope + UnconfinedTestDispatcher(testScheduler), DAY, mealsPerDay = 3)
        p.load()
        p.copyDay(prev)
        // Preset de 3: Desayuno · Comida · Cena. La Merienda empata entre Comida y Cena (gana la primera) y la Recena va a la Cena.
        assertEquals(
            listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER),
            diary.rows.filter { it.date == prev }.map { it.meal },
        )
        assertTrue(p.state.value.message!!.contains("más cercana"))
    }

    @Test
    fun copyingAnEmptyMealOrFailingReportsAMessageAndWritesNothing() = runTest {
        val diary = FakeDiary(listOf(entry(1, MealType.BREAKFAST)))
        val p = presenter(diary)
        p.load()
        p.copyMeal(MealType.LUNCH, prev, null)
        assertEquals(DiaryPresenter.COPY_EMPTY, p.state.value.message)
        diary.failing = true
        p.copyDay(prev)
        assertEquals(DiaryPresenter.COPY_ERROR, p.state.value.message)
        assertEquals(1, diary.rows.size)
    }
}

private class FakeTemplates : MealTemplateStore {
    val rows = mutableListOf<MealTemplate>()
    var failing = false
    override suspend fun all(): List<MealTemplate> { if (failing) throw IllegalStateException("db"); return rows.toList() }
    override suspend fun find(id: Long) = rows.firstOrNull { it.id == id }
    override suspend fun create(template: NewMealTemplate, nowMillis: Long): Long {
        if (failing) throw IllegalStateException("db")
        val id = rows.size + 1L
        rows += MealTemplate(id, template.name.trim(), template.items, nowMillis, nowMillis)
        return id
    }
    override suspend fun update(id: Long, template: NewMealTemplate, nowMillis: Long) = false
    override suspend fun rename(id: Long, name: String, nowMillis: Long) = false
    override suspend fun delete(id: Long) = rows.removeAll { it.id == id }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryPresenterTemplatesTest {
    private fun TestScope.presenter(diary: FakeDiary, templates: FakeTemplates?) = DiaryPresenter(
        diary, FakeGoals(), backgroundScope + UnconfinedTestDispatcher(testScheduler), DAY, nowMillis = { 777L }, templates = templates,
    )

    private fun DiaryPresenter.save(meal: MealType, name: String): String? {
        var result: String? = "pending"
        saveMealAsTemplate(meal, name) { result = it }
        return result
    }

    @Test
    fun savesAMealAsATemplateKeepingQuantitiesUnitsAndOrder() = runTest {
        val grams = Quantity(35.0, QuantityUnit.GRAMS)
        val servings = Quantity(2.0, QuantityUnit.SERVINGS)
        val diary = FakeDiary(
            listOf(
                entry(1, MealType.BREAKFAST, quantity = grams),
                entry(2, MealType.BREAKFAST, quantity = servings, servingSizeG = 30.0, barcode = null),
                entry(3, MealType.LUNCH),
            ),
        )
        val templates = FakeTemplates()
        val p = presenter(diary, templates)
        p.load()

        assertNull(p.save(MealType.BREAKFAST, "  Mi desayuno "))

        val t = templates.rows.single()
        assertEquals("Mi desayuno", t.name)
        assertEquals(listOf("Alimento 1", "Alimento 2"), t.items.map { it.name })
        assertEquals(listOf(grams, servings), t.items.map { it.quantity })
        assertEquals(30.0, t.items[1].servingSizeG)
        assertNull(t.items[1].barcode)
        assertEquals(oats, t.items[0].nutrients)
        assertEquals(777L, t.createdAtMillis)
        assertEquals("Plantilla «Mi desayuno» guardada", p.state.value.message)
        assertEquals(3, diary.rows.size) // el diario no cambia
    }

    @Test
    fun refusesRepeatedBlankAndEmptyMealWithoutWritingAnything() = runTest {
        val templates = FakeTemplates()
        val p = presenter(FakeDiary(listOf(entry(1, MealType.BREAKFAST))), templates)
        p.load()
        assertNull(p.save(MealType.BREAKFAST, "Desayuno"))
        assertEquals(TemplateRules.NAME_TAKEN, p.save(MealType.BREAKFAST, " desayuno "))
        assertEquals("Escribe un nombre", p.save(MealType.BREAKFAST, "   "))
        assertEquals(DiaryPresenter.TEMPLATE_EMPTY, p.save(MealType.LUNCH, "Comida"))
        assertEquals(1, templates.rows.size)
    }

    @Test
    fun reportsAFailureOrMissingStoreAsAnError() = runTest {
        val templates = FakeTemplates()
        val p = presenter(FakeDiary(listOf(entry(1, MealType.BREAKFAST))), templates)
        p.load()
        templates.failing = true
        assertEquals(DiaryPresenter.TEMPLATE_ERROR, p.save(MealType.BREAKFAST, "Desayuno"))
        assertEquals(DiaryPresenter.TEMPLATE_ERROR, presenter(FakeDiary(), null).save(MealType.BREAKFAST, "X"))
    }
}

/** Entradas rápidas: kcal y macros sin alimento (docs/design.md §2.16). */
@OptIn(ExperimentalCoroutinesApi::class)
class QuickEntryDiaryTest {
    private val lunch = QuickEntry.new(DAY, MealType.LUNCH, "Menú del día", 850.0, 30.0, null, 40.0)

    private fun TestScope.presenter(diary: FakeDiary, goals: FakeGoals = FakeGoals()) =
        DiaryPresenter(diary, goals, backgroundScope + UnconfinedTestDispatcher(testScheduler), DAY)

    @Test
    fun aQuickEntryCountsInTheMealTotalsAndProgressAndIsNotDoubtful() = runTest {
        val diary = FakeDiary(listOf(entry(1, MealType.LUNCH)))
        diary.add(lunch, 5L)
        val p = presenter(diary, FakeGoals(DailyGoals(kcal = 2000, proteinG = 100)))
        p.load()
        val meal = p.state.value.meals.first { it.meal == MealType.LUNCH }
        assertEquals(370.0 + 850.0, meal.kcal, 1e-9)
        assertEquals(13.0 + 30.0, meal.proteinG, 1e-9)
        assertEquals(60.0, meal.carbsG, 1e-9) // el hidrato no informado no suma
        assertEquals(1220.0, p.state.value.consumedKcal, 1e-9)
        val row = meal.entries.first { it.quick }
        assertEquals("Menú del día", row.name)
        assertEquals(850.0, row.kcal)
        assertFalse(row.doubtful) // 850 kcal serían «dudosas» si se leyeran como por 100 g
        assertEquals("30 P | 40 G", row.macrosText)
    }

    @Test
    fun aQuickEntryWithoutNameGetsTheDefaultName() {
        assertEquals(QuickEntry.DEFAULT_NAME, QuickEntry.new(DAY, MealType.DINNER, "  ", 500.0, null, null, null).name)
    }

    @Test
    fun copyingAMealKeepsQuickEntriesQuick() = runTest {
        val diary = FakeDiary()
        diary.add(lunch, 5L)
        val p = presenter(diary)
        p.load()
        p.copyMeal(MealType.LUNCH, "2026-11-03", null)
        val copy = diary.rows.single { it.date == "2026-11-03" }
        assertTrue(copy.quick)
        assertEquals(lunch.nutrients, copy.nutrients)
        assertEquals(850.0, copy.totals.kcal)
    }

    @Test
    fun deleteAndUndoKeepTheEntryQuick() = runTest {
        val diary = FakeDiary()
        val id = diary.add(lunch, 5L)
        val p = presenter(diary)
        p.load()
        p.delete(id)
        assertTrue(diary.rows.isEmpty())
        p.undoDelete()
        assertTrue(diary.rows.single().quick)
    }

    @Test
    fun formNeedsAtLeastOneValueAndRejectsBadNumbers() {
        assertTrue(validateQuickEntry(QuickEntryFormState()).empty)
        assertTrue(validateQuickEntry(QuickEntryFormState(name = "Solo nombre")).hasErrors)
        assertFalse(validateQuickEntry(QuickEntryFormState(kcal = "650")).hasErrors)
        assertFalse(validateQuickEntry(QuickEntryFormState(protein = "25,5")).hasErrors) // solo un macro basta
        assertEquals("Número no válido", validateQuickEntry(QuickEntryFormState(kcal = "-5")).kcal)
        assertEquals("Número no válido", validateQuickEntry(QuickEntryFormState(kcal = "1,2,3")).kcal)
        assertTrue(validateQuickEntry(QuickEntryFormState(kcal = "20000")).kcal != null)
        assertTrue(validateQuickEntry(QuickEntryFormState(kcal = "100", fat = "5000")).fat != null)
    }

    @Test
    fun formParsesTotalsAndRoundTripsForEditing() {
        val form = QuickEntryFormState(name = "", kcal = "650", protein = "25,5", carbs = "", fat = "30")
        val totals = form.toTotals()!!
        assertEquals(Nutrients(650.0, 25.5, null, 30.0), totals)
        assertEquals(QuickEntry.DEFAULT_NAME, form.resolvedName())
        assertEquals(form, quickEntryFormStateOf(QuickEntry.DEFAULT_NAME, totals))
        assertNull(QuickEntryFormState().toTotals())
    }
}
