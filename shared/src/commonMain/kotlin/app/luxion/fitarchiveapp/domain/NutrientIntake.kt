package app.luxion.fitarchiveapp.domain

import app.luxion.fitarchiveapp.model.DiaryEntry

/** Si el objetivo es un mínimo a alcanzar (fibra) o un máximo a no pasar (sal, azúcares, saturadas). */
enum class IntakeLimit { AT_LEAST, AT_MOST }

/**
 * Total de un nutriente en un día frente a su objetivo orientativo.
 * [incomplete]: alguna entrada no tiene el dato (o está en porciones sin tamaño), así que [totalG] es solo lo conocido.
 * [hasData]: al menos una entrada aporta el dato; si no, no hay total que enseñar.
 */
data class NutrientIntake(
    val label: String,
    val totalG: Double,
    val goalG: Double,
    val limit: IntakeLimit,
    val incomplete: Boolean,
    val hasData: Boolean,
) {
    /** Se ha pasado de un máximo; un mínimo no alcanzado no es un fallo, solo falta por llegar. */
    val exceeded: Boolean get() = limit == IntakeLimit.AT_MOST && hasData && totalG > goalG
}

/**
 * Objetivos orientativos (no son consejo médico): fibra ≥ 25 g y sal ≤ 5 g (OMS); azúcares libres y saturadas
 * ≤ 10 % de la energía (OMS), con 2 000 kcal si no hay objetivo de kcal. Azúcares a 4 kcal/g y saturadas a 9 kcal/g.
 */
object IntakeGuidelines {
    const val FIBER_MIN_G = 25.0
    const val SALT_MAX_G = 5.0
    const val DEFAULT_KCAL = 2000
    private const val ENERGY_SHARE = 0.10

    fun sugarsMaxG(goalKcal: Int?): Double = (goalKcal ?: DEFAULT_KCAL) * ENERGY_SHARE / 4.0
    fun saturatedMaxG(goalKcal: Int?): Double = (goalKcal ?: DEFAULT_KCAL) * ENERGY_SHARE / 9.0

    /** Totales del día de fibra, sal, azúcares y saturadas, sin sumar como 0 lo que no se sabe. */
    fun intake(entries: List<DiaryEntry>, goalKcal: Int?): List<NutrientIntake> = listOf(
        total("Fibra", entries, FIBER_MIN_G, IntakeLimit.AT_LEAST) { it.fiberG },
        total("Sal", entries, SALT_MAX_G, IntakeLimit.AT_MOST) { it.saltG },
        total("Azúcares", entries, sugarsMaxG(goalKcal), IntakeLimit.AT_MOST) { it.sugarsG },
        total("Saturadas", entries, saturatedMaxG(goalKcal), IntakeLimit.AT_MOST) { it.saturatedFatG },
    )

    private fun total(
        label: String,
        entries: List<DiaryEntry>,
        goalG: Double,
        limit: IntakeLimit,
        pick: (app.luxion.fitarchiveapp.model.MacroTotals) -> Double?,
    ): NutrientIntake {
        val values = entries.map { pick(it.totals) }
        return NutrientIntake(
            label = label,
            totalG = values.sumOf { it ?: 0.0 },
            goalG = goalG,
            limit = limit,
            incomplete = values.any { it == null },
            hasData = values.any { it != null },
        )
    }
}
