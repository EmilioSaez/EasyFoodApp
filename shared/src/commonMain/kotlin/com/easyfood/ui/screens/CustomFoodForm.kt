package com.easyfood.ui.screens

import com.easyfood.model.CustomFood
import com.easyfood.model.NewCustomFood
import com.easyfood.model.Nutrients
import com.easyfood.ui.components.formatGrams
import com.easyfood.ui.components.parseNumber

/** Texto de cada campo del formulario de alimento propio (docs/design.md §2.5). */
data class CustomFoodFormState(
    val name: String = "",
    val servingLabel: String = "",
    val servingSize: String = "",
    val kcal: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val saturated: String = "",
    val sugars: String = "",
    val fiber: String = "",
    val salt: String = "",
)

/** Errores por campo (null = sin error). */
data class CustomFoodFormErrors(
    val name: String? = null,
    val servingSize: String? = null,
    val kcal: String? = null,
    val protein: String? = null,
    val carbs: String? = null,
    val fat: String? = null,
    val saturated: String? = null,
    val sugars: String? = null,
    val fiber: String? = null,
    val salt: String? = null,
) {
    val hasErrors: Boolean
        get() = listOf(name, servingSize, kcal, protein, carbs, fat, saturated, sugars, fiber, salt).any { it != null }
}

private const val MAX_KCAL_PER_100G = 900.0
private const val MAX_GRAMS_PER_100G = 100.0

/** Valida el formulario. [requireAll] marca como error los obligatorios vacíos (al pulsar «Guardar»). */
fun validateCustomFood(form: CustomFoodFormState, requireAll: Boolean = true): CustomFoodFormErrors {
    fun required(text: String, max: Double, maxMsg: String): String? {
        if (text.isBlank()) return if (requireAll) "Obligatorio" else null
        val v = parseNumber(text) ?: return "Número no válido"
        return if (v > max) maxMsg else null
    }

    fun optional(text: String, max: Double = MAX_GRAMS_PER_100G): String? {
        if (text.isBlank()) return null
        val v = parseNumber(text) ?: return "Número no válido"
        return if (v > max) "Máximo ${formatGrams(max)}" else null
    }

    val fat = parseNumber(form.fat)
    val carbs = parseNumber(form.carbs)
    val saturated = parseNumber(form.saturated)
    val sugars = parseNumber(form.sugars)

    val servingSizeError = when {
        form.servingSize.isBlank() -> null
        else -> parseNumber(form.servingSize)?.let { if (it <= 0) "Debe ser mayor que 0" else null } ?: "Número no válido"
    }

    return CustomFoodFormErrors(
        name = if (form.name.isBlank() && requireAll) "Obligatorio" else null,
        servingSize = servingSizeError,
        kcal = required(form.kcal, MAX_KCAL_PER_100G, "Máximo 900 kcal por 100 g"),
        protein = required(form.protein, MAX_GRAMS_PER_100G, "Máximo 100 g"),
        carbs = required(form.carbs, MAX_GRAMS_PER_100G, "Máximo 100 g"),
        fat = required(form.fat, MAX_GRAMS_PER_100G, "Máximo 100 g"),
        saturated = optional(form.saturated) ?: if (saturated != null && fat != null && saturated > fat) "Mayor que las grasas" else null,
        sugars = optional(form.sugars) ?: if (sugars != null && carbs != null && sugars > carbs) "Mayor que los hidratos" else null,
        fiber = optional(form.fiber),
        salt = optional(form.salt),
    )
}

/** Construye el modelo a guardar, o null si el formulario no es válido. */
fun CustomFoodFormState.toNewCustomFood(brand: String? = null): NewCustomFood? {
    if (validateCustomFood(this).hasErrors) return null
    return NewCustomFood(
        name = name.trim(),
        brand = brand,
        nutrients = Nutrients(
            kcal = parseNumber(kcal),
            proteinG = parseNumber(protein),
            carbsG = parseNumber(carbs),
            fatG = parseNumber(fat),
            saturatedFatG = parseNumber(saturated),
            sugarsG = parseNumber(sugars),
            fiberG = parseNumber(fiber),
            saltG = parseNumber(salt),
        ),
        servingSizeG = parseNumber(servingSize),
        servingLabel = servingLabel.trim().ifEmpty { null },
    )
}

/** Rellena el formulario con un alimento existente (modo edición). */
fun CustomFood.toFormState(): CustomFoodFormState {
    // Sin pérdida de precisión (formatGrams redondea a 1 decimal) y con «,» como separador.
    fun fmt(v: Double?) = v?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString().replace('.', ',') } ?: ""
    return CustomFoodFormState(
        name = name,
        servingLabel = servingLabel.orEmpty(),
        servingSize = fmt(servingSizeG),
        kcal = fmt(nutrients.kcal),
        protein = fmt(nutrients.proteinG),
        carbs = fmt(nutrients.carbsG),
        fat = fmt(nutrients.fatG),
        saturated = fmt(nutrients.saturatedFatG),
        sugars = fmt(nutrients.sugarsG),
        fiber = fmt(nutrients.fiberG),
        salt = fmt(nutrients.saltG),
    )
}
