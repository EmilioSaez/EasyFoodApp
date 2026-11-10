package app.luxion.fitarchiveapp.model

/** Valores nutricionales por 100 g (o 100 ml). `null` = dato no disponible. */
data class Nutrients(
    val kcal: Double?,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val saturatedFatG: Double? = null,
    val sugarsG: Double? = null,
    val fiberG: Double? = null,
    val saltG: Double? = null,
) {
    /** Falta la energía o es absurda (más de ~900 kcal/100 g): el usuario debería revisarla. */
    val needsReview: Boolean
        get() = kcal == null || kcal < 0 || kcal > ProductValidator.MAX_PLAUSIBLE_KCAL_PER_100G
}

data class Product(
    val barcode: String,
    val name: String,
    val brand: String?,
    val imageUrl: String?,
    val nutrients: Nutrients,
    /** Tamaño de una porción en g (o ml), si el producto lo indica. */
    val servingSizeG: Double? = null,
    /** Texto original de la porción en OFF (p. ej. "30 g"). */
    val servingLabel: String? = null,
    /**
     * Valores originales (de Open Food Facts o de la base genérica) cuando el usuario los ha corregido; en ese caso
     * [nutrients] lleva los valores corregidos. `null` = producto sin corregir.
     */
    val originalNutrients: Nutrients? = null,
) {
    /** El usuario ha corregido los valores nutricionales de este producto (ver [ProductCorrection]). */
    val isCorrected: Boolean get() = originalNutrients != null
}

/**
 * Corrección local de los valores nutricionales (por 100 g o 100 ml) de un producto, por su [barcode]
 * (EAN o `generic:<id>`). Energía y macros principales son obligatorios; el resto, opcionales.
 */
data class ProductCorrection(
    val barcode: String,
    val nutrients: Nutrients,
    val updatedAtMillis: Long = 0,
) {
    init {
        require(barcode.isNotBlank()) { "Falta el código del producto" }
        validateCorrectedNutrients(nutrients)
    }
}

/** Comprueba los valores de una corrección; lanza [IllegalArgumentException] con un mensaje apto para el usuario. */
fun validateCorrectedNutrients(n: Nutrients) {
    val kcal = requireNotNull(n.kcal) { "Faltan las kcal" }
    requireNotNull(n.proteinG) { "Faltan las proteínas" }
    requireNotNull(n.carbsG) { "Faltan los hidratos" }
    requireNotNull(n.fatG) { "Faltan las grasas" }
    listOf(n.kcal, n.proteinG, n.carbsG, n.fatG, n.saturatedFatG, n.sugarsG, n.fiberG, n.saltG).forEach { v ->
        require(v == null || (v.isFinite() && v >= 0)) { "Los nutrientes no pueden ser negativos" }
    }
    require(kcal <= ProductValidator.MAX_PLAUSIBLE_KCAL_PER_100G) { "Las kcal superan lo posible (≈ 900 por 100 g)" }
    listOf(n.proteinG, n.carbsG, n.fatG, n.saturatedFatG, n.sugarsG, n.fiberG, n.saltG).forEach { v ->
        require(v == null || v <= 100.0) { "Un nutriente no puede superar los 100 g por 100 g" }
    }
}
