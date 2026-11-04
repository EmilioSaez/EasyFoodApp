package app.luxion.fitarchiveapp.model

/** Origen de un dato nutricional (mismo vocabulario que `ProductEntity.source`). */
enum class FoodSource { OPEN_FOOD_FACTS, GENERIC, CUSTOM }

/**
 * Datos que introduce el usuario para un alimento propio. Macros por 100 g (o 100 ml).
 * Energía y macros principales son obligatorios; el resto de nutrientes son opcionales.
 */
data class NewCustomFood(
    val name: String,
    val brand: String? = null,
    val nutrients: Nutrients,
    /** Tamaño de una porción en g (o ml). */
    val servingSizeG: Double? = null,
    /** Texto de la porción (p. ej. "1 rebanada"). */
    val servingLabel: String? = null,
) {
    init {
        require(name.isNotBlank()) { "El nombre no puede estar vacío" }
        requireNotNull(nutrients.kcal) { "Faltan las kcal" }
        requireNotNull(nutrients.proteinG) { "Faltan las proteínas" }
        requireNotNull(nutrients.carbsG) { "Faltan los hidratos" }
        requireNotNull(nutrients.fatG) { "Faltan las grasas" }
        listOf(
            nutrients.kcal, nutrients.proteinG, nutrients.carbsG, nutrients.fatG,
            nutrients.saturatedFatG, nutrients.sugarsG, nutrients.fiberG, nutrients.saltG,
        ).forEach { v -> require(v == null || (v.isFinite() && v >= 0)) { "Los nutrientes no pueden ser negativos" } }
        require(servingSizeG == null || (servingSizeG.isFinite() && servingSizeG > 0)) {
            "La porción debe ser mayor que 0"
        }
    }
}

/** Alimento propio ya guardado. */
data class CustomFood(
    val id: Long,
    val name: String,
    val brand: String?,
    val nutrients: Nutrients,
    val servingSizeG: Double?,
    val servingLabel: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    val source: FoodSource get() = FoodSource.CUSTOM
}
