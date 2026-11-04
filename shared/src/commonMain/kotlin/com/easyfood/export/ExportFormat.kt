package com.easyfood.export

import com.easyfood.model.MealType
import com.easyfood.model.Nutrients
import com.easyfood.model.Quantity
import com.easyfood.model.QuantityUnit
import kotlinx.serialization.Serializable

/**
 * Formato de exportación JSON. Reglas de evolución:
 * - Añadir un campo opcional (con valor por defecto) o una colección nueva NO cambia [ExportCodec.CURRENT_VERSION].
 * - Cambiar o quitar el significado de un campo existente SÍ lo incrementa.
 * - Los campos desconocidos se ignoran al leer; las colecciones ausentes se leen como vacías.
 *
 * Pendiente de que existan sus almacenes: alimentos propios y objetivos (se añadirán como campos opcionales).
 */
@Serializable
data class ExportFile(
    val schemaVersion: Int,
    val exportedAtMillis: Long,
    val products: List<ExportedProduct> = emptyList(),
    val diaryEntries: List<ExportedDiaryEntry> = emptyList(),
)

@Serializable
data class ExportedNutrients(
    val kcal: Double? = null,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val saturatedFatG: Double? = null,
    val sugarsG: Double? = null,
    val fiberG: Double? = null,
    val saltG: Double? = null,
)

@Serializable
data class ExportedProduct(
    val barcode: String,
    val name: String,
    val brand: String? = null,
    val imageUrl: String? = null,
    val nutrients: ExportedNutrients = ExportedNutrients(),
    val servingSizeG: Double? = null,
    val servingLabel: String? = null,
    /** Origen del dato: OPEN_FOOD_FACTS, GENERIC o CUSTOM. */
    val source: String = "OPEN_FOOD_FACTS",
    val updatedAtMillis: Long = 0,
)

@Serializable
data class ExportedDiaryEntry(
    val id: Long,
    /** Fecha ISO-8601 (`YYYY-MM-DD`). */
    val date: String,
    val meal: MealType,
    val barcode: String? = null,
    val name: String,
    val quantity: Double,
    val unit: QuantityUnit,
    val servingSizeG: Double? = null,
    val nutrients: ExportedNutrients = ExportedNutrients(),
    val createdAtMillis: Long,
)

internal fun Nutrients.toExported() =
    ExportedNutrients(kcal, proteinG, carbsG, fatG, saturatedFatG, sugarsG, fiberG, saltG)

internal fun ExportedNutrients.toDomain() =
    Nutrients(kcal, proteinG, carbsG, fatG, saturatedFatG, sugarsG, fiberG, saltG)

internal fun quantityOf(amount: Double, unit: QuantityUnit) = Quantity(amount, unit)
