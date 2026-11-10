package app.luxion.fitarchiveapp.export

import app.luxion.fitarchiveapp.domain.ReminderSettings
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import kotlinx.serialization.Serializable

/**
 * Formato de exportación JSON. Reglas de evolución:
 * - Añadir un campo opcional (con valor por defecto) o una colección nueva NO cambia [ExportCodec.CURRENT_VERSION].
 * - Cambiar o quitar el significado de un campo existente SÍ lo incrementa.
 * - Los campos desconocidos se ignoran al leer; las colecciones ausentes se leen como vacías.
 *
 * Pendiente: los objetivos (se añadirán como campo opcional).
 */
@Serializable
data class ExportFile(
    val schemaVersion: Int,
    val exportedAtMillis: Long,
    val products: List<ExportedProduct> = emptyList(),
    val diaryEntries: List<ExportedDiaryEntry> = emptyList(),
    /** Añadido sin subir la versión: los archivos anteriores no lo traen y se leen como vacío. */
    val customFoods: List<ExportedCustomFood> = emptyList(),
    /** Registros de peso. Añadido sin subir la versión: los archivos anteriores no lo traen y se leen como vacío. */
    val weights: List<ExportedWeight> = emptyList(),
    /** Recetas con sus ingredientes. Añadido sin subir la versión: los archivos anteriores no lo traen y se leen como vacío. */
    val recipes: List<ExportedRecipe> = emptyList(),
    /** Peso objetivo. Añadido sin subir la versión: los archivos anteriores no lo traen y se leen como `null`. */
    val weightGoal: ExportedWeightGoal? = null,
    /** Comidas al día (3 a 6). Añadido sin subir la versión: los archivos anteriores no lo traen y se leen como `null`. */
    val mealsPerDay: Int? = null,
    /** Agua por día. Añadido sin subir la versión: los archivos anteriores no lo traen y se leen como vacío. */
    val water: List<ExportedWater> = emptyList(),
    /** Objetivo diario de agua. Añadido sin subir la versión: los archivos anteriores no lo traen y se leen como `null`. */
    val waterGoal: ExportedWaterGoal? = null,
    /** Alimentos favoritos. Añadido sin subir la versión: los archivos anteriores no lo traen y se leen como vacío. */
    val favorites: List<ExportedFavorite> = emptyList(),
    /** Plantillas de comida. Añadido sin subir la versión: los archivos anteriores no lo traen y se leen como vacío. */
    val mealTemplates: List<ExportedMealTemplate> = emptyList(),
    /** Ejercicio registrado. Añadido sin subir la versión: los archivos anteriores no lo traen y se leen como vacío. */
    val exercises: List<ExportedExercise> = emptyList(),
    /** Porcentaje del ejercicio que se suma al objetivo (0 a 100). Añadido sin subir la versión; ausente = `null`. */
    val exercisePercent: ExportedExercisePercent? = null,
    /** Correcciones del usuario de los valores de productos. Añadido sin subir la versión: los archivos anteriores no lo traen y se leen como vacío. */
    val productCorrections: List<ExportedProductCorrection> = emptyList(),
    /** Recordatorios locales. Añadido sin subir la versión: los archivos anteriores no lo traen y se leen como `null`. */
    val reminders: ReminderSettings? = null,
)

@Serializable
data class ExportedMealTemplate(
    /** Id local del dispositivo de origen; solo sirve para reconocer la misma plantilla al reimportar. */
    val id: Long,
    val name: String,
    val items: List<ExportedTemplateItem> = emptyList(),
    val createdAtMillis: Long = 0,
    val updatedAtMillis: Long = 0,
)

@Serializable
data class ExportedTemplateItem(
    /** EAN o `generic:<id>`; `null` en un alimento propio. */
    val barcode: String? = null,
    val name: String,
    val quantity: Double,
    val unit: QuantityUnit,
    val servingSizeG: Double? = null,
    val nutrients: ExportedNutrients = ExportedNutrients(),
)

/** Una actividad de ejercicio. Sin id propio: se reconoce por fecha, nombre, kcal y duración. */
@Serializable
data class ExportedExercise(
    /** Fecha ISO-8601 (`YYYY-MM-DD`). */
    val date: String,
    val name: String,
    val kcal: Int,
    val minutes: Int? = null,
    val createdAtMillis: Long = 0,
)

@Serializable
data class ExportedExercisePercent(
    val percent: Int,
    val updatedAtMillis: Long = 0,
)

/**
 * Alimento favorito. Es portable entre dispositivos: un producto (EAN o `generic:<id>`) viaja por su [barcode]; un
 * alimento propio, cuyo id es local de cada dispositivo, por su [customName] y [customBrand]. Solo uno de los dos
 * grupos va informado.
 */
@Serializable
data class ExportedFavorite(
    val barcode: String? = null,
    val customName: String? = null,
    val customBrand: String? = null,
    val createdAtMillis: Long = 0,
)

@Serializable
data class ExportedWater(
    /** Fecha ISO-8601 (`YYYY-MM-DD`); un total por día. */
    val date: String,
    val ml: Int,
    val updatedAtMillis: Long = 0,
)

@Serializable
data class ExportedWaterGoal(
    val goalMl: Int,
    val updatedAtMillis: Long = 0,
)

@Serializable
data class ExportedRecipe(
    /** Id local del dispositivo de origen; solo sirve para reconocer la misma receta al reimportar. */
    val id: Long,
    val name: String,
    val servings: Int? = null,
    val ingredients: List<ExportedRecipeIngredient> = emptyList(),
    val createdAtMillis: Long = 0,
    val updatedAtMillis: Long = 0,
)

@Serializable
data class ExportedRecipeIngredient(
    /** EAN o `generic:<id>`; `null` en un alimento propio. */
    val barcode: String? = null,
    val name: String,
    /** Cantidad en gramos (o ml). */
    val grams: Double,
    val nutrients: ExportedNutrients = ExportedNutrients(),
    val servingSizeG: Double? = null,
)

@Serializable
data class ExportedWeightGoal(
    val targetKg: Double,
    val updatedAtMillis: Long = 0,
)

@Serializable
data class ExportedWeight(
    /** Fecha ISO-8601 (`YYYY-MM-DD`); un registro por día. */
    val date: String,
    val weightKg: Double,
    val createdAtMillis: Long = 0,
    val updatedAtMillis: Long = 0,
)

@Serializable
data class ExportedCustomFood(
    /** Id local del dispositivo de origen; solo sirve para reconocer el mismo alimento al reimportar. */
    val id: Long,
    val name: String,
    val brand: String? = null,
    val nutrients: ExportedNutrients = ExportedNutrients(),
    val servingSizeG: Double? = null,
    val servingLabel: String? = null,
    val createdAtMillis: Long = 0,
    val updatedAtMillis: Long = 0,
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
    /**
     * Entrada rápida (kcal y macros sin alimento): cantidad 100 g y `nutrients` con los totales. Añadido sin subir la
     * versión: los archivos anteriores no lo traen y se leen como `false`.
     */
    val quick: Boolean = false,
)

internal fun Nutrients.toExported() =
    ExportedNutrients(kcal, proteinG, carbsG, fatG, saturatedFatG, sugarsG, fiberG, saltG)

internal fun ExportedNutrients.toDomain() =
    Nutrients(kcal, proteinG, carbsG, fatG, saturatedFatG, sugarsG, fiberG, saltG)

internal fun quantityOf(amount: Double, unit: QuantityUnit) = Quantity(amount, unit)

/** Valores nutricionales por 100 g que el usuario ha puesto en lugar de los de un producto (EAN o `generic:<id>`). */
@Serializable
data class ExportedProductCorrection(
    val barcode: String,
    val nutrients: ExportedNutrients = ExportedNutrients(),
    val updatedAtMillis: Long = 0,
)
