package app.luxion.fitarchiveapp.model

import app.luxion.fitarchiveapp.domain.NutritionTargets

/** Objetivos diarios; `null` = sin definir. Macros en gramos. */
data class DailyGoals(
    val kcal: Int? = null,
    val proteinG: Int? = null,
    val carbsG: Int? = null,
    val fatG: Int? = null,
) {
    init {
        require(kcal == null || kcal > 0) { "Las kcal objetivo deben ser > 0: $kcal" }
        require(proteinG == null || proteinG >= 0) { "Proteína negativa: $proteinG" }
        require(carbsG == null || carbsG >= 0) { "Hidratos negativos: $carbsG" }
        require(fatG == null || fatG >= 0) { "Grasa negativa: $fatG" }
    }

    val isEmpty: Boolean get() = kcal == null && proteinG == null && carbsG == null && fatG == null

    companion object {
        /** Objetivos propuestos por la calculadora TDEE. */
        fun from(targets: NutritionTargets) = DailyGoals(
            kcal = targets.targetKcal,
            proteinG = targets.macros.proteinG,
            carbsG = targets.macros.carbsG,
            fatG = targets.macros.fatG,
        )
    }
}
