package app.luxion.fitarchiveapp.model

import kotlin.math.abs

/** Aviso sobre la fiabilidad de los datos de un [Product] (Open Food Facts tiene calidad variable). */
sealed interface DataWarning {
    /** Falta un campo (`field` es un identificador estable, p. ej. "kcal" o "name"). */
    data class MissingField(val field: String) : DataWarning

    /** Las kcal declaradas superan lo físicamente posible (grasa pura ≈ 900 kcal/100 g). */
    data class ImplausibleKcal(val kcal: Double) : DataWarning

    /** Las kcal declaradas no cuadran con 4·proteína + 4·hidratos + 9·grasa (la fibra cuenta a 2 kcal/g). */
    data class InconsistentMacros(val declaredKcal: Double, val estimatedKcal: Double) : DataWarning

    /** Proteína + hidratos + grasa superan los 100 g de cada 100 g. */
    data class MacrosExceed100g(val totalG: Double) : DataWarning

    /** Las grasas saturadas superan a las grasas totales. */
    data class SaturatedExceedsFat(val saturatedG: Double, val fatG: Double) : DataWarning

    /** Los azúcares superan a los hidratos totales. */
    data class SugarsExceedCarbs(val sugarsG: Double, val carbsG: Double) : DataWarning
}

object ProductValidator {
    /**
     * Máximo de kcal por 100 g: la grasa pura aporta 900 (9 kcal/g). USDA publica 902 para aceites y
     * mantecas reales, así que se deja un margen de 5 kcal para no avisar de datos correctos.
     */
    const val MAX_PLAUSIBLE_KCAL_PER_100G = 905.0

    /**
     * Diferencia relativa tolerada entre kcal declaradas y estimadas. Con la base USDA (7 793 alimentos)
     * el 15 % avisa en ~1,3 % de ellos, casi todos atípicos de verdad (alcohol, edulcorantes, cacao);
     * descontando la fibra de los hidratos desaparecen los falsos positivos de salvados y especias.
     */
    const val KCAL_RELATIVE_TOLERANCE = 0.15

    /** Diferencia absoluta mínima (kcal) para avisar; evita falsos positivos en valores bajos. */
    const val KCAL_ABSOLUTE_TOLERANCE = 30.0

    /** Margen en gramos sobre 100 g por redondeos de la etiqueta. */
    const val MACRO_SUM_TOLERANCE_G = 1.0

    /** Margen en gramos para «saturadas > grasas» y «azúcares > hidratos» (redondeos: USDA publica 0,35 g de azúcar con 0 g de hidratos). */
    const val SUBFRACTION_TOLERANCE_G = 0.5

    /** kcal estimadas por macros: 4·P + 4·H + 9·G, con la fibra (parte de los hidratos) a 2 kcal/g si se conoce. */
    fun estimatedKcal(nutrients: Nutrients): Double? {
        val p = nutrients.proteinG ?: return null
        val c = nutrients.carbsG ?: return null
        val f = nutrients.fatG ?: return null
        val fiber = nutrients.fiberG?.takeIf { it in 0.0..c } ?: 0.0
        return 4 * p + 4 * (c - fiber) + 2 * fiber + 9 * f
    }

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
                val estimated = estimatedKcal(n)!!
                val diff = abs(kcal - estimated)
                if (diff > KCAL_ABSOLUTE_TOLERANCE && diff > KCAL_RELATIVE_TOLERANCE * maxOf(kcal, estimated)) {
                    add(DataWarning.InconsistentMacros(kcal, estimated))
                }
            }
        }

        val sat = n.saturatedFatG
        if (sat != null && f != null && sat > f + SUBFRACTION_TOLERANCE_G) add(DataWarning.SaturatedExceedsFat(sat, f))
        val sugars = n.sugarsG
        if (sugars != null && c != null && sugars > c + SUBFRACTION_TOLERANCE_G) {
            add(DataWarning.SugarsExceedCarbs(sugars, c))
        }
    }
}

/** Avisos de datos dudosos de este producto; lista vacía si no hay nada sospechoso. */
fun Product.dataWarnings(): List<DataWarning> = ProductValidator.validate(this)
