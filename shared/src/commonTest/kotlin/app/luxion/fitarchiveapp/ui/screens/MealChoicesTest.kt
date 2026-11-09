package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.model.MealType
import kotlin.test.Test
import kotlin.test.assertEquals

class MealChoicesTest {
    @Test
    fun defaultMealIsTheActiveOneNearestToTheClock() {
        assertEquals(MealType.BREAKFAST, MealChoices(4) { 8 * 60 }.defaultMeal())
        assertEquals(MealType.LUNCH, MealChoices(4) { 14 * 60 }.defaultMeal())
        assertEquals(MealType.SNACK, MealChoices(4) { 17 * 60 }.defaultMeal())
        assertEquals(MealType.DINNER, MealChoices(4) { 22 * 60 }.defaultMeal())
    }

    @Test
    fun defaultMealRespectsTheNumberOfMeals() {
        assertEquals(MealType.LUNCH, MealChoices(3) { 17 * 60 + 30 }.defaultMeal())
        assertEquals(MealType.SNACK, MealChoices(5) { 17 * 60 + 30 }.defaultMeal())
        assertEquals(MealType.LATE_SNACK, MealChoices(6) { 23 * 60 + 40 }.defaultMeal())
    }

    @Test
    fun withoutAChoiceTheDefaultFourMealsApply() {
        assertEquals(listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.SNACK, MealType.DINNER), MealChoices(null) { 0 }.options())
    }

    @Test
    fun optionsAreTheActiveMealsPlusTheCurrentOneIfItLeftThePreset() {
        val three = MealChoices(3) { 0 }
        assertEquals(listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER), three.options())
        assertEquals(
            listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.SNACK, MealType.DINNER),
            three.options(MealType.SNACK),
        )
        assertEquals(three.options(), three.options(MealType.LUNCH))
    }
}
