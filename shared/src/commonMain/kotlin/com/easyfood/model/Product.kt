package com.easyfood.model

/** Valores nutricionales por 100 g (o 100 ml). `null` = dato no disponible. */
data class Nutrients(
    val kcal: Double?,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
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
)
