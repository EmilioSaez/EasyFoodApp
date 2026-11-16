package app.luxion.fitarchiveapp.domain

import app.luxion.fitarchiveapp.model.DiaryEntry
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// 2026-11-16 es lunes.
private const val TODAY = "2026-11-16"

private var nextId = 1L

private fun e(
    date: String,
    name: String,
    meal: MealType = MealType.BREAKFAST,
    grams: Double = 100.0,
    barcode: String? = "code-$name",
    quick: Boolean = false,
) = DiaryEntry(
    nextId++, date, meal, barcode, name, Quantity(grams, QuantityUnit.GRAMS), null,
    Nutrients(kcal = 100.0, proteinG = 1.0, carbsG = 1.0, fatG = 1.0), createdAtMillis = nextId, quick = quick,
)

class HabitSuggestionsTest {
    @Test
    fun suggestsFoodsRepeatedInThatMealMostFirst() {
        val history = listOf(
            e("2026-11-10", "Avena"), e("2026-11-11", "Avena"), e("2026-11-12", "Avena"),
            e("2026-11-11", "Café"), e("2026-11-13", "Café"),
            e("2026-11-12", "Pizza", MealType.DINNER), e("2026-11-13", "Pizza", MealType.DINNER),
        )
        val s = HabitSuggestions.suggest(history, MealType.BREAKFAST, TODAY)
        assertEquals(listOf("Avena", "Café"), s.map { it.name })
        assertEquals(3, s[0].count)
    }

    @Test
    fun aSingleOccurrenceIsNotAHabit() {
        assertEquals(emptyList(), HabitSuggestions.suggest(listOf(e("2026-11-10", "Avena")), MealType.BREAKFAST, TODAY))
    }

    @Test
    fun sameWeekdayCountsDouble() {
        // Hoy es lunes: el pan (2 lunes) pasa por delante de la avena (3 días de otros días de la semana).
        val history = listOf(
            e("2026-11-09", "Pan"), e("2026-11-02", "Pan"),
            e("2026-11-10", "Avena"), e("2026-11-11", "Avena"), e("2026-11-12", "Avena"),
        )
        val s = HabitSuggestions.suggest(history, MealType.BREAKFAST, TODAY)
        assertEquals(listOf("Pan", "Avena"), s.map { it.name })
        assertEquals(2, s[0].sameWeekdayCount)
    }

    @Test
    fun usesTheMostRepeatedQuantityAndBreaksTiesWithTheLatest() {
        val history = listOf(
            e("2026-11-10", "Avena", grams = 40.0), e("2026-11-11", "Avena", grams = 50.0), e("2026-11-12", "Avena", grams = 50.0),
        )
        assertEquals(50.0, HabitSuggestions.suggest(history, MealType.BREAKFAST, TODAY).single().quantity.amount)
        val tie = listOf(e("2026-11-10", "Avena", grams = 40.0), e("2026-11-12", "Avena", grams = 60.0))
        assertEquals(60.0, HabitSuggestions.suggest(tie, MealType.BREAKFAST, TODAY).single().quantity.amount)
    }

    @Test
    fun skipsWhatTheDayAlreadyHasInThatMeal() {
        val history = listOf(e("2026-11-10", "Avena"), e("2026-11-11", "Avena"), e(TODAY, "Avena"))
        assertEquals(emptyList(), HabitSuggestions.suggest(history, MealType.BREAKFAST, TODAY))
        // En otra comida del mismo día sí se sugiere.
        assertEquals(1, HabitSuggestions.suggest(listOf(e("2026-11-10", "Avena", MealType.SNACK), e("2026-11-11", "Avena", MealType.SNACK), e(TODAY, "Avena")), MealType.SNACK, TODAY).size)
    }

    @Test
    fun onlyLooksAtTheWindowBeforeTheDate() {
        val old = listOf(e("2026-08-01", "Avena"), e("2026-08-02", "Avena")) // > 60 días
        val future = listOf(e("2026-11-17", "Avena"), e("2026-11-18", "Avena"))
        assertEquals(emptyList(), HabitSuggestions.suggest(old + future, MealType.BREAKFAST, TODAY))
    }

    @Test
    fun ignoresQuickEntriesAndGroupsCustomFoodsByName() {
        val quick = listOf(e("2026-11-10", "Menú", barcode = null, quick = true), e("2026-11-11", "Menú", barcode = null, quick = true))
        assertEquals(emptyList(), HabitSuggestions.suggest(quick, MealType.BREAKFAST, TODAY))
        val custom = listOf(e("2026-11-10", "Mi tostada", barcode = null), e("2026-11-11", "mi tostada ", barcode = null))
        assertEquals(1, HabitSuggestions.suggest(custom, MealType.BREAKFAST, TODAY).size)
    }

    @Test
    fun respectsTheLimitAndBuildsAnEntryForTheTarget() {
        val history = (1..8).flatMap { i -> listOf(e("2026-11-10", "A$i"), e("2026-11-11", "A$i")) }
        val s = HabitSuggestions.suggest(history, MealType.BREAKFAST, TODAY, limit = 3)
        assertEquals(3, s.size)
        val entry = s[0].toNewEntry(TODAY, MealType.SNACK)
        assertEquals(TODAY, entry.date)
        assertEquals(MealType.SNACK, entry.meal)
        assertEquals(s[0].name, entry.name)
        assertTrue(!entry.quick)
    }

    @Test
    fun badDateGivesNoSuggestions() {
        assertEquals(emptyList(), HabitSuggestions.suggest(listOf(e("2026-11-10", "Avena")), MealType.BREAKFAST, "nope"))
    }
}
