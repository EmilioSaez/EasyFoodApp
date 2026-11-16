package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.model.MealType
import kotlin.test.Test
import kotlin.test.assertEquals

/** Textos de la cabecera y de la tarjeta Coach de Hoy (docs/design.md §2.26). */
class DiaryCoachCardTest {
    private fun meal(type: MealType, kcal: Double = 0.0, entries: List<EntryUi> = emptyList()) = MealUi(type, kcal, 0.0, 0.0, 0.0, entries)

    private val entry = EntryUi(1, null, "Avena", app.luxion.fitarchiveapp.model.Quantity(60.0, app.luxion.fitarchiveapp.model.QuantityUnit.GRAMS),
        app.luxion.fitarchiveapp.ui.components.DataSource.Generic, 228.0, false, null)

    private fun state(goal: Int?, consumed: Double, proteinGoal: Double? = 130.0, proteinDone: Double = 68.0) = DiaryUiState(
        date = "2026-11-16",
        goalKcal = goal,
        consumedKcal = consumed,
        macros = listOf(MacroProgress("Proteína", proteinDone, proteinGoal)),
        meals = listOf(meal(MealType.BREAKFAST, 228.0, listOf(entry)), meal(MealType.LUNCH), meal(MealType.DINNER)),
    )

    @Test fun headlineSaysWhatIsLeftInKcalAndProtein() =
        assertEquals("¿Qué como? Te quedan 885 kcal y 62 g de proteína.", coachHeadline(state(2100, 1215.0)))

    @Test fun headlineSkipsProteinWithoutGoalOrOnceReached() {
        assertEquals("¿Qué como? Te quedan 885 kcal.", coachHeadline(state(2100, 1215.0, proteinGoal = null)))
        assertEquals("¿Qué como? Te quedan 885 kcal.", coachHeadline(state(2100, 1215.0, proteinDone = 130.0)))
    }

    @Test fun headlineWhenOverGoalAndWithoutGoal() {
        assertEquals("Hoy te has pasado 100 kcal. ¿Cómo voy?", coachHeadline(state(2000, 2100.0)))
        assertEquals("¿Qué como? Pregúntale al Coach.", coachHeadline(state(null, 500.0)))
    }

    @Test fun suggestButtonNamesTheFirstEmptyMeal() {
        assertEquals("Sugerir comida", coachSuggestLabel(state(2100, 228.0)))
        assertEquals("Sugerir algo", coachSuggestLabel(DiaryUiState(date = "2026-11-16", meals = listOf(meal(MealType.DINNER, entries = listOf(entry))))))
    }

    @Test fun weekdayAndDayNamesTheDayOfTheWeek() {
        assertEquals("lunes 16 de noviembre", IsoDate.weekdayAndDay("2026-11-16"))
        assertEquals("miércoles 4 de noviembre", IsoDate.weekdayAndDay("2026-11-04"))
        assertEquals("domingo 1 de noviembre", IsoDate.weekdayAndDay("2026-11-01"))
    }
}
