package com.easyfood.ui.screens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomFoodFormTest {
    private val valid = CustomFoodFormState(
        name = " Pan casero ", kcal = "250", protein = "8,5", carbs = "50", fat = "2",
        servingLabel = "1 rebanada", servingSize = "30",
    )

    @Test
    fun validFormBuildsFood() {
        val food = assertNotNull(valid.toNewCustomFood())
        assertEquals("Pan casero", food.name)
        assertEquals(8.5, food.nutrients.proteinG)
        assertEquals(30.0, food.servingSizeG)
        assertNull(food.nutrients.fiberG)
    }

    @Test
    fun requiredFieldsFlaggedOnlyWhenRequested() {
        val empty = CustomFoodFormState()
        assertFalse(validateCustomFood(empty, requireAll = false).hasErrors)
        val errors = validateCustomFood(empty)
        assertEquals("Obligatorio", errors.name)
        assertEquals("Obligatorio", errors.kcal)
        assertNull(empty.toNewCustomFood())
    }

    @Test
    fun saturatedAndSugarsCannotExceedParents() {
        val errors = validateCustomFood(valid.copy(saturated = "3", sugars = "60"))
        assertEquals("Mayor que las grasas", errors.saturated)
        assertEquals("Mayor que los hidratos", errors.sugars)
    }

    @Test
    fun limitsAndServing() {
        assertTrue(validateCustomFood(valid.copy(kcal = "901")).kcal != null)
        assertTrue(validateCustomFood(valid.copy(protein = "101")).protein != null)
        assertTrue(validateCustomFood(valid.copy(servingSize = "0")).servingSize != null)
        assertFalse(validateCustomFood(valid.copy(servingSize = "")).hasErrors)
        assertEquals("Número no válido", validateCustomFood(valid.copy(kcal = ",")).kcal)
    }

    @Test
    fun editRoundTripKeepsValues() {
        val food = assertNotNull(valid.copy(salt = "0,05").toNewCustomFood())
        val saved = com.easyfood.model.CustomFood(
            1, food.name, null, food.nutrients, food.servingSizeG, food.servingLabel, 0, 0,
        )
        val form = saved.toFormState()
        assertEquals("0,05", form.salt)
        assertEquals("250", form.kcal)
        assertEquals(food, form.toNewCustomFood())
    }
}
