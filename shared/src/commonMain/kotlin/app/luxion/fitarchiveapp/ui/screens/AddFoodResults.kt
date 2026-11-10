package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.data.FavoriteKeys
import app.luxion.fitarchiveapp.data.SearchText
import app.luxion.fitarchiveapp.data.generic.GenericFoodFormat
import app.luxion.fitarchiveapp.model.CustomFood
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Product
import app.luxion.fitarchiveapp.ui.components.DataSource
import app.luxion.fitarchiveapp.ui.components.formatGrams

/** Prefijo de la clave de navegación de un alimento propio (el resto es su id). */
const val CUSTOM_FOOD_KEY_PREFIX = FavoriteKeys.CUSTOM_PREFIX

/** Id del alimento propio si [key] es una clave `custom:<id>` válida; `null` en cualquier otro caso. */
fun customFoodIdOf(key: String): Long? =
    if (key.startsWith(CUSTOM_FOOD_KEY_PREFIX)) key.removePrefix(CUSTOM_FOOD_KEY_PREFIX).toLongOrNull() else null

/** Fila de resultado de la pantalla Añadir: unifica productos (OFF / genérico) y alimentos propios. */
data class FoodResult(
    /** Clave que se pasa a la ficha: EAN, `generic:<id>` o `custom:<id>`. */
    val key: String,
    val name: String,
    val detail: String,
    val source: DataSource,
    val kcalPer100g: Double?,
)

fun Product.toFoodResult(): FoodResult {
    val isGeneric = barcode.startsWith(GenericFoodFormat.KEY_PREFIX)
    val parts = listOfNotNull(
        brand?.takeIf { it.isNotBlank() },
        servingSizeG?.let { "${formatGrams(it)} g" },
        barcode.takeUnless { isGeneric },
        "corregido por ti".takeIf { isCorrected },
    )
    return FoodResult(
        key = barcode,
        name = name,
        detail = parts.joinToString(" · ").ifEmpty { if (isGeneric) DataSource.Generic.label else "" },
        source = if (isGeneric) DataSource.Generic else DataSource.OpenFoodFacts,
        kcalPer100g = nutrients.kcal,
    )
}

fun CustomFood.toFoodResult(): FoodResult = FoodResult(
    key = CUSTOM_FOOD_KEY_PREFIX + id,
    name = name,
    detail = listOfNotNull(
        brand?.takeIf { it.isNotBlank() },
        servingSizeG?.let { "${formatGrams(it)} g" },
    ).joinToString(" · ").ifEmpty { DataSource.Custom.label },
    source = DataSource.Custom,
    kcalPer100g = nutrients.kcal,
)

/**
 * Coincidencia de un alimento propio con el texto buscado, con la misma tolerancia que el FTS de los productos:
 * prefijo de palabra sin acentos ni mayúsculas, plurales, erratas y supermercados (por marca).
 */
fun CustomFood.matches(text: String): Boolean {
    val terms = SearchText.terms(text)
    if (terms.isEmpty()) return false
    val nameWords = SearchText.terms(name)
    val brandWords = SearchText.terms(brand.orEmpty())
    val brandText = brandWords.joinToString(" ")
    return terms.all { t ->
        val supermarket = SearchText.supermarkets[t]
        if (supermarket != null) {
            supermarket.any { b -> " $brandText ".contains(" $b ") }
        } else {
            val stem = SearchText.stem(t)
            (nameWords + brandWords).any { it.startsWith(stem) || SearchText.isCloseTo(stem, it) }
        }
    }
}

/** Propios primero (son los más relevantes para el usuario), luego el resto en el orden del FTS. */
fun mergeResults(custom: List<CustomFood>, products: List<Product>): List<FoodResult> =
    custom.map { it.toFoodResult() } + products.map { it.toFoodResult() }

/**
 * Resultado de búsqueda con lo necesario para usarlo sin volver a consultarlo (p. ej. como ingrediente):
 * [barcode] es la clave del producto (EAN o `generic:<id>`) y `null` en un alimento propio.
 */
data class FoodPick(
    val result: FoodResult,
    val barcode: String?,
    val nutrients: Nutrients,
    val servingSizeG: Double?,
)

/** Igual que [mergeResults], conservando los datos nutricionales de cada resultado. */
fun mergePicks(custom: List<CustomFood>, products: List<Product>): List<FoodPick> =
    custom.map { FoodPick(it.toFoodResult(), null, it.nutrients, it.servingSizeG) } +
        products.map { FoodPick(it.toFoodResult(), it.barcode, it.nutrients, it.servingSizeG) }
