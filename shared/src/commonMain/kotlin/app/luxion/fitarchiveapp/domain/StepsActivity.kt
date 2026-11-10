package app.luxion.fitarchiveapp.domain

import app.luxion.fitarchiveapp.model.epochDayToIsoDate
import app.luxion.fitarchiveapp.model.isoDateToEpochDay
import app.luxion.fitarchiveapp.steps.DailySteps
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Propuesta de reevaluar el nivel de actividad a partir de los pasos medidos.
 */
data class StepsSuggestion(
    val averageSteps: Int,
    val daysUsed: Int,
    val stepsTdeeKcal: Int,
    val currentTdeeKcal: Int,
    val currentLevel: ActivityLevel,
    val suggestedLevel: ActivityLevel,
) {
    /** `true` si los pasos apuntan a otro nivel que el elegido. */
    val changesLevel: Boolean get() = suggestedLevel != currentLevel
}

/**
 * Estima el gasto a partir de los pasos y propone un nivel de actividad.
 *
 * Fórmula: `TDEE_pasos = TMB × 1,2 + max(pasos_medios − 3000, 0) × 0,0005 × kg`.
 * - TMB × 1,2 (sedentaria) ya incluye el gasto digestivo y la actividad cotidiana mínima, que se
 *   atribuye a unos 3000 pasos diarios; solo los pasos por encima de ese umbral suman.
 * - 0,0005 kcal por paso y kg ≈ 0,5 kcal/kg/km caminando (≈ 0,035 kcal por paso con 70 kg).
 * - El nivel propuesto es el [ActivityLevel] cuyo factor está más cerca de `TDEE_pasos / TMB`.
 *
 * Límites: los pasos no miden ciclismo, natación, pesas ni carga; si haces ese ejercicio, el nivel
 * actual puede ser más acertado que la propuesta. Es una estimación, no una medición.
 * Se usa la media de los días completos (sin `today`) de los últimos [WINDOW_DAYS] y se necesitan
 * al menos [MIN_DAYS] con datos; un día con menos de [MIN_VALID_STEPS] pasos se toma por «móvil no
 * llevado» y se ignora, y cada día se limita a [MAX_DAILY_STEPS] para que un valor erróneo no
 * distorsione la media.
 */
object StepsActivity {
    const val WINDOW_DAYS = 14
    const val MIN_DAYS = 5
    const val MIN_VALID_STEPS = 500L
    const val MAX_DAILY_STEPS = 40_000L
    const val BASELINE_STEPS = 3000
    const val KCAL_PER_STEP_PER_KG = 0.0005

    /** Primer día de la ventana de lectura (inclusive) para [today]. */
    fun windowStart(today: String): String? =
        isoDateToEpochDay(today)?.let { epochDayToIsoDate(it - WINDOW_DAYS) }

    /** Último día completo de la ventana (ayer). */
    fun windowEnd(today: String): String? =
        isoDateToEpochDay(today)?.let { epochDayToIsoDate(it - 1) }

    /** (media de pasos, días usados) de los días válidos de la ventana, o `null` si hay menos de [MIN_DAYS]. */
    fun averageSteps(steps: List<DailySteps>, today: String): Pair<Int, Int>? {
        val todayDay = isoDateToEpochDay(today) ?: return null
        val valid = steps
            .filter { day ->
                val d = isoDateToEpochDay(day.date) ?: return@filter false
                d in (todayDay - WINDOW_DAYS) until todayDay && day.steps >= MIN_VALID_STEPS
            }
            .distinctBy { it.date }
        if (valid.size < MIN_DAYS) return null
        val avg = valid.sumOf { it.steps.coerceAtMost(MAX_DAILY_STEPS) }.toDouble() / valid.size
        return avg.roundToInt() to valid.size
    }

    fun stepsTdee(profile: UserProfile, averageSteps: Int): Double {
        val bmr = TdeeCalculator.bmr(profile)
        val extra = maxOf(averageSteps - BASELINE_STEPS, 0) * KCAL_PER_STEP_PER_KG * profile.weightKg
        return bmr * ActivityLevel.SEDENTARY.factor + extra
    }

    fun nearestLevel(factor: Double): ActivityLevel =
        ActivityLevel.entries.minBy { abs(it.factor - factor) }

    fun suggest(profile: UserProfile, steps: List<DailySteps>, today: String): StepsSuggestion? {
        val (avg, days) = averageSteps(steps, today) ?: return null
        val bmr = TdeeCalculator.bmr(profile)
        val tdee = stepsTdee(profile, avg)
        return StepsSuggestion(
            averageSteps = avg,
            daysUsed = days,
            stepsTdeeKcal = tdee.roundToInt(),
            currentTdeeKcal = (bmr * profile.activity.factor).roundToInt(),
            currentLevel = profile.activity,
            suggestedLevel = nearestLevel(tdee / bmr),
        )
    }
}
