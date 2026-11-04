package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.domain.ActivityLevel
import app.luxion.fitarchiveapp.domain.Goal
import app.luxion.fitarchiveapp.domain.MacroTargets
import app.luxion.fitarchiveapp.domain.TdeeCalculator
import app.luxion.fitarchiveapp.domain.UserProfile
import app.luxion.fitarchiveapp.model.DailyGoals
import kotlin.math.roundToInt

/** Resultado de la calculadora para la pantalla Objetivos. [limited]: el objetivo se subió al mínimo. */
data class GoalsPlan(
    val bmrKcal: Int,
    val tdeeKcal: Int,
    val targetKcal: Int,
    val macros: MacroTargets,
    val limited: Boolean,
)

/** Lógica de la pantalla Objetivos (docs/design.md §2.6), sin dependencias de UI. */
object GoalsPlanner {
    /** Ritmos del slider en kg/semana. */
    val PACES = listOf(0.0, 0.25, 0.5, 0.75, 1.0)
    const val KCAL_PER_KG_PER_WEEK = 1100
    const val DEFAULT_PACE_INDEX = 2
    private const val FAT_KCAL_SHARE = 0.25

    fun paceKcal(paceIndex: Int): Int = (PACES[paceIndex.coerceIn(PACES.indices)] * KCAL_PER_KG_PER_WEEK).roundToInt()

    /** TDEE ± ajuste de la meta; nunca baja de max(TMB, mínimo del calculador). */
    fun plan(profile: UserProfile, paceIndex: Int): GoalsPlan {
        val bmr = TdeeCalculator.bmr(profile)
        val tdee = bmr * profile.activity.factor
        val delta = when (profile.goal) {
            Goal.LOSE -> -paceKcal(paceIndex)
            Goal.MAINTAIN -> 0
            Goal.GAIN -> paceKcal(paceIndex)
        }
        val raw = (tdee + delta).roundToInt()
        val floor = maxOf(bmr.roundToInt(), TdeeCalculator.MIN_TARGET_KCAL)
        val target = maxOf(raw, floor)
        return GoalsPlan(
            bmrKcal = bmr.roundToInt(),
            tdeeKcal = tdee.roundToInt(),
            targetKcal = target,
            macros = macros(target, profile.weightKg, profile.goal),
            limited = raw < floor,
        )
    }

    /** Proteína por kg según la meta, grasa 25 % de las kcal y el resto en hidratos. */
    fun macros(kcal: Int, weightKg: Double, goal: Goal): MacroTargets {
        val protein = weightKg * goal.proteinGramsPerKg
        val fat = kcal * FAT_KCAL_SHARE / 9.0
        val carbs = maxOf((kcal - protein * 4 - fat * 9) / 4, 0.0)
        return MacroTargets(protein.roundToInt(), carbs.roundToInt(), fat.roundToInt())
    }

    /** kcal que suman los macros en gramos. */
    fun macroKcal(proteinG: Int, carbsG: Int, fatG: Int): Int = proteinG * 4 + carbsG * 4 + fatG * 9

    /** Porcentajes de kcal (P, H, G) que suman 100; 0/0/0 si no hay macros. */
    fun macroPercents(proteinG: Int, carbsG: Int, fatG: Int): Triple<Int, Int, Int> {
        val total = macroKcal(proteinG, carbsG, fatG)
        if (total == 0) return Triple(0, 0, 0)
        val p = (proteinG * 4.0 * 100 / total).roundToInt()
        val c = (carbsG * 4.0 * 100 / total).roundToInt()
        return Triple(p, c, 100 - p - c)
    }

    /**
     * Objetivos finales: los ajustes manuales mandan sobre la calculadora y esta sobre lo guardado.
     * Con kcal a mano y datos de perfil, los macros se recalculan para esas kcal.
     */
    fun resolve(
        plan: GoalsPlan?,
        profile: UserProfile?,
        saved: DailyGoals,
        kcalOverride: Int?,
        proteinOverride: Int?,
        carbsOverride: Int?,
        fatOverride: Int?,
    ): DailyGoals {
        val kcal = kcalOverride ?: plan?.targetKcal ?: saved.kcal
        val base: MacroTargets? = when {
            plan != null && kcalOverride == null -> plan.macros
            profile != null && kcal != null -> macros(kcal, profile.weightKg, profile.goal)
            saved.proteinG != null && saved.carbsG != null && saved.fatG != null ->
                MacroTargets(saved.proteinG, saved.carbsG, saved.fatG)
            else -> null
        }
        return DailyGoals(
            kcal = kcal,
            proteinG = proteinOverride ?: base?.proteinG,
            carbsG = carbsOverride ?: base?.carbsG,
            fatG = fatOverride ?: base?.fatG,
        )
    }
}

val ActivityLevel.title: String
    get() = when (this) {
        ActivityLevel.SEDENTARY -> "Sedentaria"
        ActivityLevel.LIGHT -> "Ligera"
        ActivityLevel.MODERATE -> "Moderada"
        ActivityLevel.ACTIVE -> "Alta"
        ActivityLevel.VERY_ACTIVE -> "Muy alta"
    }

val ActivityLevel.description: String
    get() = when (this) {
        ActivityLevel.SEDENTARY -> "Trabajo de oficina, poco o nada de ejercicio"
        ActivityLevel.LIGHT -> "Ejercicio ligero 1–3 días por semana"
        ActivityLevel.MODERATE -> "Ejercicio moderado 3–5 días por semana"
        ActivityLevel.ACTIVE -> "Ejercicio intenso 6–7 días por semana"
        ActivityLevel.VERY_ACTIVE -> "Entrenamiento muy intenso o trabajo físico"
    }
