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
    /** Falta la energía o es absurda (>900 kcal/100 g): el usuario debería revisarla. */
    val needsReview: Boolean
        get() = kcal == null || kcal < 0 || kcal > MAX_PLAUSIBLE_KCAL_PER_100G

    private companion object {
        const val MAX_PLAUSIBLE_KCAL_PER_100G = 900.0
    }
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
)
