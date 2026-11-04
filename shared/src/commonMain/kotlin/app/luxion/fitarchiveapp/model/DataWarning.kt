package app.luxion.fitarchiveapp.model

import kotlin.math.abs

/** Aviso sobre la fiabilidad de los datos de un [Product] (Open Food Facts tiene calidad variable). */
sealed interface DataWarning {
    /** Falta un campo (`field` es un identificador estable, p. ej. "kcal" o "name"). */
    data class MissingField(val field: String) : DataWarning

    /** Las kcal declaradas superan lo físicamente posible (>900 kcal/100 g). */
    data class ImplausibleKcal(val kcal: Double) : DataWarning

    /** Las kcal declaradas no cuadran con 4·proteína + 4·hidratos + 9·grasa. */
    data class InconsistentMacros(val declaredKcal: Double, val estimatedKcal: Double) : DataWarning

    /** Proteína + hidratos + grasa superan los 100 g de cada 100 g. */
    data class MacrosExceed100g(val totalG: Double) : DataWarning
}

object ProductValidator {
    const val MAX_PLAUSIBLE_KCAL_PER_100G = 900.0

    /** Diferencia relativa tolerada entre kcal declaradas y estimadas (fibra, alcohol, polioles, redondeos). */
    const val KCAL_RELATIVE_TOLERANCE = 0.25

    /** Diferencia absoluta mínima (kcal) para avisar; evita falsos positivos en valores bajos. */
    const val KCAL_ABSOLUTE_TOLERANCE = 30.0

    /** Margen en gramos sobre 100 g por redondeos de la etiqueta. */
    const val MACRO_SUM_TOLERANCE_G = 1.0

    fun validate(product: Product): List<DataWarning> = buildList {
        if (product.name.isBlank()) add(DataWarning.MissingField("name"))
        if (product.brand.isNullOrBlank()) add(DataWarning.MissingField("brand"))
        if (product.imageUrl.isNullOrBlank()) add(DataWarning.MissingField("imageUrl"))

        val n = product.nutrients
        if (n.kcal == null) add(DataWarning.MissingField("kcal"))
        if (n.proteinG == null) add(DataWarning.MissingField("proteinG"))
        if (n.carbsG == null) add(DataWarning.MissingField("carbsG"))
        if (n.fatG == null) add(DataWarning.MissingField("fatG"))

        val kcal = n.kcal
        if (kcal != null && kcal > MAX_PLAUSIBLE_KCAL_PER_100G) add(DataWarning.ImplausibleKcal(kcal))

        val p = n.proteinG
        val c = n.carbsG
        val f = n.fatG
        if (p != null && c != null && f != null) {
            val total = p + c + f
            if (total > 100.0 + MACRO_SUM_TOLERANCE_G) add(DataWarning.MacrosExceed100g(total))
            if (kcal != null) {
                val estimated = 4 * p + 4 * c + 9 * f
                val diff = abs(kcal - estimated)
                if (diff > KCAL_ABSOLUTE_TOLERANCE && diff > KCAL_RELATIVE_TOLERANCE * maxOf(kcal, estimated)) {
                    add(DataWarning.InconsistentMacros(kcal, estimated))
                }
            }
        }
    }
}

/** Avisos de datos dudosos de este producto; lista vacía si no hay nada sospechoso. */
fun Product.dataWarnings(): List<DataWarning> = ProductValidator.validate(this)
