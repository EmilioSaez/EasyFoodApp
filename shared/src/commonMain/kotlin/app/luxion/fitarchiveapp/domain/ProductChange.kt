package app.luxion.fitarchiveapp.domain

import app.luxion.fitarchiveapp.model.Nutrients
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Cambios nutricionales relevantes entre dos versiones de un producto (por 100 g).
 * Relevante = energía con ≥ 5 kcal y ≥ 5 % de diferencia, o proteína/hidratos/grasas con ≥ 1 g y ≥ 10 %,
 * o un dato que aparece/desaparece. Las diferencias de redondeo no se avisan.
 */
object ProductChange {
    /** Resumen legible («Energía 250 → 230 kcal · Proteína 8 → 10 g»), o `null` si no hay cambios relevantes. */
    fun summary(old: Nutrients, new: Nutrients): String? {
        val parts = listOfNotNull(
            part("Energía", "kcal", old.kcal, new.kcal, minAbs = 5.0, minRel = 0.05),
            part("Proteína", "g", old.proteinG, new.proteinG, minAbs = 1.0, minRel = 0.10),
            part("Hidratos", "g", old.carbsG, new.carbsG, minAbs = 1.0, minRel = 0.10),
            part("Grasas", "g", old.fatG, new.fatG, minAbs = 1.0, minRel = 0.10),
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private fun part(label: String, unit: String, old: Double?, new: Double?, minAbs: Double, minRel: Double): String? {
        val relevant = when {
            old == null && new == null -> false
            old == null || new == null -> true
            else -> {
                val diff = abs(new - old)
                diff >= minAbs && diff >= minRel * maxOf(abs(old), abs(new))
            }
        }
        return if (relevant) "$label ${fmt(old)} → ${fmt(new)} $unit" else null
    }

    private fun fmt(v: Double?): String = when {
        v == null -> "sin dato"
        v % 1.0 == 0.0 -> v.toLong().toString()
        else -> ((v * 10).roundToLong() / 10.0).toString().replace('.', ',')
    }
}
