package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.data.generic.GenericFoodFormat
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Product
import app.luxion.fitarchiveapp.ui.components.parseNumber

/** Formulario de corrección de valores por 100 g: reutiliza los campos y la validación del alimento propio. */
fun correctionFormStateOf(nutrients: Nutrients): CustomFoodFormState =
    customFoodFormStateOf(name = "x", servingLabel = null, servingSizeG = null, nutrients = nutrients)

/** Errores del formulario de corrección (el nombre no se edita, así que no cuenta). */
fun validateCorrection(form: CustomFoodFormState, requireAll: Boolean = true): CustomFoodFormErrors =
    validateCustomFood(form.copy(name = "x"), requireAll)

/** Valores corregidos, o `null` si el formulario no es válido. */
fun CustomFoodFormState.toCorrectedNutrients(): Nutrients? {
    if (validateCorrection(this).hasErrors) return null
    return Nutrients(
        kcal = parseNumber(kcal), proteinG = parseNumber(protein), carbsG = parseNumber(carbs), fatG = parseNumber(fat),
        saturatedFatG = parseNumber(saturated), sugarsG = parseNumber(sugars),
        fiberG = parseNumber(fiber), saltG = parseNumber(salt),
    )
}

private const val OFF_PRODUCT_URL = "https://world.openfoodfacts.org/product/"

/**
 * Página del producto en Open Food Facts, donde cualquiera puede corregirlo. Solo para productos de OFF (un EAN):
 * los genéricos y los alimentos propios no existen allí. El enlace no lleva ningún dato del usuario.
 */
fun Product.openFoodFactsUrl(): String? =
    barcode.takeIf { !it.startsWith(GenericFoodFormat.KEY_PREFIX) && it.isNotEmpty() && it.all(Char::isDigit) }
        ?.let { OFF_PRODUCT_URL + it }
