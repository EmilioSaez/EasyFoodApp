package app.luxion.fitarchiveapp.model

enum class MealType { BREAKFAST, LUNCH, DINNER, SNACK }

enum class QuantityUnit { GRAMS, SERVINGS }

/** Cantidad registrada: gramos (o ml) o número de porciones. */
data class Quantity(val amount: Double, val unit: QuantityUnit) {
    init {
        require(amount > 0 && amount.isFinite()) { "La cantidad debe ser positiva" }
    }
}

/** Totales de energía y macros; cada campo es `null` si algún dato necesario no está disponible. */
data class MacroTotals(
    val kcal: Double?,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
)

/**
 * Alimento registrado en el diario. Copia el nombre y los valores por 100 g del producto en el momento
 * del registro. [date] es una fecha ISO-8601 (`YYYY-MM-DD`).
 */
data class DiaryEntry(
    val id: Long,
    val date: String,
    val meal: MealType,
    val barcode: String?,
    val name: String,
    val quantity: Quantity,
    val servingSizeG: Double?,
    /** Valores por 100 g; solo se usan kcal, proteínas, hidratos y grasa. */
    val nutrients: Nutrients,
    val createdAtMillis: Long,
) {
    /** Cantidad en gramos, o `null` si está en porciones y se desconoce el tamaño de la porción. */
    val grams: Double?
        get() = when (quantity.unit) {
            QuantityUnit.GRAMS -> quantity.amount
            QuantityUnit.SERVINGS -> servingSizeG?.let { it * quantity.amount }
        }

    val totals: MacroTotals
        get() = MacroTotals(
            kcal = scaled(nutrients.kcal),
            proteinG = scaled(nutrients.proteinG),
            carbsG = scaled(nutrients.carbsG),
            fatG = scaled(nutrients.fatG),
        )

    private fun scaled(per100g: Double?): Double? {
        val g = grams ?: return null
        return per100g?.let { it * g / 100.0 }
    }
}

/** Datos para crear una entrada del diario. */
data class NewDiaryEntry(
    val date: String,
    val meal: MealType,
    val barcode: String?,
    val name: String,
    val quantity: Quantity,
    val servingSizeG: Double?,
    val nutrients: Nutrients,
)
