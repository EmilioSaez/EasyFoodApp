package app.luxion.fitarchiveapp.domain

import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.MealType.BREAKFAST
import app.luxion.fitarchiveapp.model.MealType.DINNER
import app.luxion.fitarchiveapp.model.MealType.LATE_SNACK
import app.luxion.fitarchiveapp.model.MealType.LUNCH
import app.luxion.fitarchiveapp.model.MealType.MID_MORNING
import app.luxion.fitarchiveapp.model.MealType.SNACK
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MealPlanTest {
    @Test
    fun presetsMatchTheSpec() {
        assertEquals(listOf(BREAKFAST, LUNCH, DINNER), MealPlan.preset(3))
        assertEquals(listOf(BREAKFAST, LUNCH, SNACK, DINNER), MealPlan.preset(4))
        assertEquals(listOf(BREAKFAST, MID_MORNING, LUNCH, SNACK, DINNER), MealPlan.preset(5))
        assertEquals(listOf(BREAKFAST, MID_MORNING, LUNCH, SNACK, DINNER, LATE_SNACK), MealPlan.preset(6))
    }

    @Test
    fun missingOrInvalidCountFallsBackToTheDefaultPreset() {
        assertEquals(MealPlan.preset(MealPlan.DEFAULT_MEALS), MealPlan.preset(null))
        assertEquals(MealPlan.preset(MealPlan.DEFAULT_MEALS), MealPlan.preset(9))
        assertEquals(4, MealPlan.DEFAULT_MEALS)
    }

    @Test
    fun onlyThreeToSixAreValid() {
        assertFalse(MealPlan.isValid(2))
        assertTrue((3..6).all { MealPlan.isValid(it) })
        assertFalse(MealPlan.isValid(7))
    }

    @Test
    fun presetsFollowTheOrderOfTheDay() {
        for (n in MealPlan.options) {
            val preset = MealPlan.preset(n)
            assertEquals(preset.sortedBy { it.ordinal }, preset)
            assertEquals(n, preset.size)
        }
    }

    @Test
    fun visibleKeepsMealsWithEntriesThatLeftThePreset() {
        // Con 3 comidas, una merienda con datos sigue enseñándose, en su sitio del día.
        assertEquals(listOf(BREAKFAST, LUNCH, SNACK, DINNER), MealPlan.visible(3, setOf(SNACK)))
        assertEquals(listOf(BREAKFAST, LUNCH, DINNER), MealPlan.visible(3, emptySet()))
        assertEquals(MealType.entries, MealPlan.visible(6, setOf(SNACK)))
    }

    @Test
    fun nearestPicksTheClosestActiveMeal() {
        val four = MealPlan.preset(4)
        assertEquals(BREAKFAST, MealPlan.nearest(four, 8 * 60))
        assertEquals(LUNCH, MealPlan.nearest(four, 13 * 60 + 30))
        assertEquals(SNACK, MealPlan.nearest(four, 17 * 60))
        assertEquals(DINNER, MealPlan.nearest(four, 21 * 60 + 30))
        // Madrugada: la recena no está activa, la cena queda a menos distancia que el desayuno.
        assertEquals(DINNER, MealPlan.nearest(four, 23 * 60 + 30))
        assertEquals(BREAKFAST, MealPlan.nearest(four, 3 * 60))
    }

    @Test
    fun nearestOnlyConsidersActiveMeals() {
        assertEquals(LUNCH, MealPlan.nearest(MealPlan.preset(3), 12 * 60 + 30))
        assertEquals(MID_MORNING, MealPlan.nearest(MealPlan.preset(5), 11 * 60))
        assertEquals(LATE_SNACK, MealPlan.nearest(MealPlan.preset(6), 23 * 60 + 45))
        // La distancia es circular: a medianoche la recena queda más cerca que el desayuno.
        assertEquals(LATE_SNACK, MealPlan.nearest(MealPlan.preset(6), 0))
    }

    @Test
    fun nearestNeedsAMeal() {
        assertFailsWith<IllegalArgumentException> { MealPlan.nearest(emptyList(), 600) }
    }
}
