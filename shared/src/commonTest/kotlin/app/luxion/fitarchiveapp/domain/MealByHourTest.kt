package app.luxion.fitarchiveapp.domain

import app.luxion.fitarchiveapp.model.MealType
import kotlin.test.Test
import kotlin.test.assertEquals

class MealByHourTest {
    @Test
    fun picksTheMealForEachPartOfTheDay() {
        assertEquals(MealType.BREAKFAST, mealForHour(8))
        assertEquals(MealType.LUNCH, mealForHour(14))
        assertEquals(MealType.SNACK, mealForHour(18))
        assertEquals(MealType.DINNER, mealForHour(21))
        assertEquals(MealType.DINNER, mealForHour(2))
    }

    @Test
    fun boundariesBelongToTheLaterMeal() {
        assertEquals(MealType.DINNER, mealForHour(4))
        assertEquals(MealType.BREAKFAST, mealForHour(5))
        assertEquals(MealType.LUNCH, mealForHour(11))
        assertEquals(MealType.SNACK, mealForHour(16))
        assertEquals(MealType.DINNER, mealForHour(20))
    }
}
