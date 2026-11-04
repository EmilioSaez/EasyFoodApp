package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.model.DataWarning
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import app.luxion.fitarchiveapp.ui.components.NutritionRow
import app.luxion.fitarchiveapp.ui.components.formatKcal
import app.luxion.fitarchiveapp.ui.components.formatNumber
import app.luxion.fitarchiveapp.ui.components.toRows

/** Cantidad en gramos para una [amount] en [unit]; `null` si son porciones y no hay tamaño de porción. */
fun gramsFor(amount: Double, unit: QuantityUnit, servingSizeG: Double?): Double? = when (unit) {
    QuantityUnit.GRAMS -> amount
    QuantityUnit.SERVINGS -> servingSizeG?.let { it * amount }
}

/** Valores nutricionales de [grams] gramos de un alimento con valores [this] por 100 g. */
fun Nutrients.scaledTo(grams: Double): Nutrients {
    fun s(v: Double?) = v?.let { it * grams / 100.0 }
    return Nutrients(
        kcal = s(kcal), proteinG = s(proteinG), carbsG = s(carbsG), fatG = s(fatG),
        saturatedFatG = s(saturatedFatG), sugarsG = s(sugarsG), fiberG = s(fiberG), saltG = s(saltG),
    )
}

/** Filas de la tabla «Por porción» ya escaladas. */
fun Nutrients.perServingRows(servingG: Double): List<NutritionRow> = scaledTo(servingG).toRows()

/** Texto de cada motivo de [DataWarning] que ve el usuario en el aviso de datos dudosos. */
fun DataWarning.reason(): String? = when (this) {
    is DataWarning.ImplausibleKcal -> "${formatKcal(kcal)} kcal/100 g supera lo posible (900)"
    is DataWarning.InconsistentMacros ->
        "Las ${formatKcal(declaredKcal)} kcal no cuadran con los macros (≈ ${formatKcal(estimatedKcal)} kcal)"
    is DataWarning.MacrosExceed100g -> "Proteína + hidratos + grasa suman ${formatNumber(totalG, 0)} g por 100 g"
    is DataWarning.MissingField -> when (field) {
        "kcal" -> "Falta la energía"
        "proteinG" -> "Falta la proteína"
        "carbsG" -> "Faltan los hidratos"
        "fatG" -> "Faltan las grasas"
        else -> null // nombre, marca o imagen ausentes no afectan al cálculo
    }
}

fun List<DataWarning>.reasons(): List<String> = mapNotNull { it.reason() }.distinct()

/** Texto del campo de cantidad -> [Quantity] válida, o `null` si no es un número positivo. */
fun quantityOrNull(amount: Double?, unit: QuantityUnit): Quantity? =
    amount?.takeIf { it > 0 && it.isFinite() }?.let { Quantity(it, unit) }
