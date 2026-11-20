package app.luxion.easyfoodapp.domain

import app.luxion.easyfoodapp.model.DailyGoals
import app.luxion.easyfoodapp.model.DiaryEntry
import kotlin.math.roundToInt

/** Un macro del día: lo tomado y su objetivo (`null` = sin objetivo). */
data class MacroProgress(val eatenG: Int, val goalG: Int?)

/**
 * Lo que enseña el widget de Android: kcal restantes del día, macros y agua. Se calcula aquí (y no en el widget)
 * para que sea comprobable sin dispositivo.
 */
data class DayWidgetSnapshot(
    val eatenKcal: Int,
    val goalKcal: Int?,
    val protein: MacroProgress,
    val carbs: MacroProgress,
    val fat: MacroProgress,
    val waterMl: Int,
    val waterGoalMl: Int,
) {
    /** Kcal que quedan hasta el objetivo (negativo si se ha pasado); `null` sin objetivo. */
    val remainingKcal: Int? get() = goalKcal?.let { it - eatenKcal }

    companion object {
        fun of(entries: List<DiaryEntry>, goals: DailyGoals, waterMl: Int, waterGoalMl: Int): DayWidgetSnapshot {
            val totals = entries.map { it.totals }
            fun grams(pick: (app.luxion.easyfoodapp.model.MacroTotals) -> Double?) = totals.sumOf { pick(it) ?: 0.0 }.roundToInt()
            return DayWidgetSnapshot(
                eatenKcal = totals.sumOf { it.kcal ?: 0.0 }.roundToInt(),
                goalKcal = goals.kcal,
                protein = MacroProgress(grams { it.proteinG }, goals.proteinG),
                carbs = MacroProgress(grams { it.carbsG }, goals.carbsG),
                fat = MacroProgress(grams { it.fatG }, goals.fatG),
                waterMl = waterMl,
                waterGoalMl = waterGoalMl,
            )
        }
    }
}
