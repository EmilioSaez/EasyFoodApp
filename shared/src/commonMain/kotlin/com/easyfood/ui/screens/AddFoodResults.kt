package com.easyfood.ui.screens

import com.easyfood.data.generic.GenericFoodFormat
import com.easyfood.model.CustomFood
import com.easyfood.model.Product
import com.easyfood.ui.components.DataSource
import com.easyfood.ui.components.formatGrams

/** Prefijo de la clave de navegación de un alimento propio (el resto es su id). */
const val CUSTOM_FOOD_KEY_PREFIX = "custom:"

/** Fila de resultado de la pantalla Añadir: unifica productos (OFF / genérico) y alimentos propios. */
data class FoodResult(
    /** Clave que se pasa a la ficha: EAN, `generic:<id>` o `custom:<id>`. */
    val key: String,
    val name: String,
    val detail: String,
    val source: DataSource,
    val kcalPer100g: Double?,
)

/** Filtro por origen de los FilterChips; `null` en [source] = todos. */
enum class SourceFilter(val label: String, val source: DataSource?) {
    All("Todos", null),
    Generic("Genérico", DataSource.Generic),
    OpenFoodFacts("Open Food Facts", DataSource.OpenFoodFacts),
    Custom("Propios", DataSource.Custom),
}

fun List<FoodResult>.filteredBy(filter: SourceFilter): List<FoodResult> =
    if (filter.source == null) this else filter { it.source == filter.source }

fun Product.toFoodResult(): FoodResult {
    val isGeneric = barcode.startsWith(GenericFoodFormat.KEY_PREFIX)
    val parts = listOfNotNull(
        brand?.takeIf { it.isNotBlank() },
        servingSizeG?.let { "${formatGrams(it)} g" },
        barcode.takeUnless { isGeneric },
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

/** Coincidencia simple por prefijo de palabra (sin acentos ni mayúsculas) para los alimentos propios. */
fun CustomFood.matches(text: String): Boolean {
    val terms = text.normalizedTerms()
    if (terms.isEmpty()) return false
    val words = "$name ${brand.orEmpty()}".normalizedTerms()
    return terms.all { t -> words.any { it.startsWith(t) } }
}

private fun String.normalizedTerms(): List<String> =
    lowercase().map { c ->
        when (c) {
            'á', 'à', 'ä', 'â' -> 'a'
            'é', 'è', 'ë', 'ê' -> 'e'
            'í', 'ì', 'ï', 'î' -> 'i'
            'ó', 'ò', 'ö', 'ô' -> 'o'
            'ú', 'ù', 'ü', 'û' -> 'u'
            else -> c
        }
    }.joinToString("").split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }

/** Propios primero (son los más relevantes para el usuario), luego el resto en el orden del FTS. */
fun mergeResults(custom: List<CustomFood>, products: List<Product>): List<FoodResult> =
    custom.map { it.toFoodResult() } + products.map { it.toFoodResult() }
