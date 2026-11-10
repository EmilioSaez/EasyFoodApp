package app.luxion.fitarchiveapp.model

/**
 * Comidas del día, en orden de aparición. Se guardan por nombre: [SNACK] ya existía y se muestra como «Merienda»;
 * [MID_MORNING] es el Almuerzo (media mañana) y [LATE_SNACK] la Recena.
 */
enum class MealType { BREAKFAST, MID_MORNING, LUNCH, SNACK, DINNER, LATE_SNACK }

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
    val saturatedFatG: Double? = null,
    val sugarsG: Double? = null,
    val fiberG: Double? = null,
    val saltG: Double? = null,
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
    /** Valores por 100 g (copia al registrar); los campos extra son `null` en las entradas anteriores a la migración 16. */
    val nutrients: Nutrients,
    val createdAtMillis: Long,
    /**
     * Entrada rápida (comer fuera): sin alimento en la base. Se guarda como [QuickEntry.QUANTITY_G] g y [nutrients]
     * son los totales de la entrada, así [totals], el progreso y la copia funcionan igual que con un alimento.
     */
    val quick: Boolean = false,
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
            saturatedFatG = scaled(nutrients.saturatedFatG),
            sugarsG = scaled(nutrients.sugarsG),
            fiberG = scaled(nutrients.fiberG),
            saltG = scaled(nutrients.saltG),
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
    val quick: Boolean = false,
)

/** Entrada rápida de kcal y macros sin alimento (docs/design.md §2.16). */
object QuickEntry {
    /** Cantidad con la que se guarda: los valores «por 100 g» coinciden con los totales. */
    const val QUANTITY_G = 100.0

    /** Nombre que se guarda si el usuario no pone ninguno. */
    const val DEFAULT_NAME = "Entrada rápida"

    fun nutrients(kcal: Double?, proteinG: Double?, carbsG: Double?, fatG: Double?) =
        Nutrients(kcal, proteinG, carbsG, fatG)

    fun new(
        date: String, meal: MealType, name: String,
        kcal: Double?, proteinG: Double?, carbsG: Double?, fatG: Double?,
    ) = NewDiaryEntry(
        date = date,
        meal = meal,
        barcode = null,
        name = name.trim().ifEmpty { DEFAULT_NAME },
        quantity = Quantity(QUANTITY_G, QuantityUnit.GRAMS),
        servingSizeG = null,
        nutrients = nutrients(kcal, proteinG, carbsG, fatG),
        quick = true,
    )
}
