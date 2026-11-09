package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Recipe
import app.luxion.fitarchiveapp.model.RecipeIngredient
import app.luxion.fitarchiveapp.ui.components.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RecipeFormTest {
    private val rice = RecipeIngredient("generic:1", "Arroz", 200.0, Nutrients(350.0, 7.0, 78.0, 1.0))
    private val oil = RecipeIngredient(null, "Aceite", 10.0, Nutrients(900.0, 0.0, 0.0, 100.0))

    private fun pick(servingSizeG: Double?, barcode: String? = "123") = FoodPick(
        FoodResult("123", "Galletas", "", DataSource.OpenFoodFacts, 450.0),
        barcode, Nutrients(450.0, 6.0, 70.0, 15.0), servingSizeG,
    )

    @Test
    fun emptyFormIsInvalidWithEachError() {
        val f = RecipeFormState()
        assertNotNull(f.nameError)
        assertNotNull(f.ingredientsError)
        assertNull(f.servingsError)
        assertNull(f.toNewRecipe())
    }

    @Test
    fun validFormBuildsTrimmedRecipe() {
        val r = RecipeFormState("  Paella ", "4", listOf(rice, oil)).toNewRecipe()!!
        assertEquals("Paella", r.name)
        assertEquals(4, r.servings)
        assertEquals(listOf(rice, oil), r.ingredients)
    }

    @Test
    fun servingsAreOptionalButMustBeAnIntegerInRange() {
        assertNull(RecipeFormState("A", "", listOf(rice)).toNewRecipe()!!.servings)
        for (bad in listOf("0", "-2", "2,5", "abc", "101")) {
            assertNotNull(RecipeFormState("A", bad, listOf(rice)).servingsError, bad)
            assertNull(RecipeFormState("A", bad, listOf(rice)).toNewRecipe(), bad)
        }
        assertEquals(100, RecipeFormState("A", " 100 ", listOf(rice)).toNewRecipe()!!.servings)
    }

    @Test
    fun ingredientEditing() {
        val f = RecipeFormState("A", "", listOf(rice)).addIngredient(oil)
        assertEquals(listOf("Arroz", "Aceite"), f.ingredients.map { it.name })
        assertEquals(50.0, f.withGrams(1, 50.0).ingredients[1].grams)
        assertEquals(200.0, f.withGrams(1, 50.0).ingredients[0].grams)
        assertEquals(listOf("Aceite"), f.removeIngredient(0).ingredients.map { it.name })
    }

    @Test
    fun formFromRecipeRoundTrips() {
        val recipe = Recipe(7, "Paella", 4, listOf(rice, oil), 1, 2)
        val form = RecipeFormState.of(recipe)
        assertEquals("4", form.servingsText)
        assertEquals(recipe.ingredients, form.toNewRecipe()!!.ingredients)
        assertEquals("", RecipeFormState.of(recipe.copy(servings = null)).servingsText)
    }

    @Test
    fun defaultGramsIsOneServingOr100() {
        assertEquals(30.0, defaultIngredientGrams(pick(30.0)))
        assertEquals(100.0, defaultIngredientGrams(pick(null)))
    }

    @Test
    fun ingredientFromPickCopiesNameAndNutrients() {
        val i = ingredientOf(pick(30.0, barcode = null), 45.0)
        assertEquals("Galletas", i.name)
        assertEquals(45.0, i.grams)
        assertNull(i.barcode)
        assertEquals(30.0, i.servingSizeG)
        assertEquals(450.0, i.nutrients.kcal)
    }
}
