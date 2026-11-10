package app.luxion.fitarchiveapp.domain

import kotlin.math.roundToInt

enum class Sex { MALE, FEMALE }

enum class Goal(val kcalFactor: Double, val proteinGramsPerKg: Double) {
    LOSE(0.8, 2.0),
    MAINTAIN(1.0, 1.6),
    GAIN(1.1, 1.8),
}

data class UserProfile(
    val ageYears: Int,
    val sex: Sex,
    val weightKg: Double,
    val heightCm: Double,
    val goal: Goal,
) {
    init {
        require(ageYears in 10..120) { "Edad fuera de rango: $ageYears" }
        require(weightKg in 20.0..500.0) { "Peso fuera de rango: $weightKg" }
        require(heightCm in 80.0..260.0) { "Altura fuera de rango: $heightCm" }
    }
}

data class MacroTargets(val proteinG: Int, val carbsG: Int, val fatG: Int)

data class NutritionTargets(
    val bmrKcal: Int,
    val tdeeKcal: Int,
    val targetKcal: Int,
    val macros: MacroTargets,
)

object TdeeCalculator {
    const val MIN_TARGET_KCAL = 1200
    private const val FAT_KCAL_SHARE = 0.25
    private const val KCAL_PER_G_PROTEIN = 4.0
    private const val KCAL_PER_G_CARBS = 4.0
    private const val KCAL_PER_G_FAT = 9.0

    /** TDEE sin pasos: TMB × 1,2 (gasto digestivo y actividad cotidiana mínima). */
    const val BASE_ACTIVITY_FACTOR = 1.2

    /** Metabolismo basal (Mifflin-St Jeor). */
    fun bmr(profile: UserProfile): Double {
        val base = 10 * profile.weightKg + 6.25 * profile.heightCm - 5 * profile.ageYears
        return base + if (profile.sex == Sex.MALE) 5 else -161
    }

    /** Gasto total: TMB × [BASE_ACTIVITY_FACTOR] y, si hay media de pasos, lo que suman sobre el umbral. */
    fun tdee(profile: UserProfile, averageSteps: Int? = null): Double =
        if (averageSteps == null) bmr(profile) * BASE_ACTIVITY_FACTOR else StepsActivity.stepsTdee(profile, averageSteps)

    fun calculate(profile: UserProfile, averageSteps: Int? = null): NutritionTargets {
        val bmr = bmr(profile)
        val tdee = tdee(profile, averageSteps)
        val target = maxOf((tdee * profile.goal.kcalFactor).roundToInt(), MIN_TARGET_KCAL)

        val protein = profile.weightKg * profile.goal.proteinGramsPerKg
        val fat = target * FAT_KCAL_SHARE / KCAL_PER_G_FAT
        val carbsKcal = target - protein * KCAL_PER_G_PROTEIN - fat * KCAL_PER_G_FAT
        val carbs = maxOf(carbsKcal / KCAL_PER_G_CARBS, 0.0)

        return NutritionTargets(
            bmrKcal = bmr.roundToInt(),
            tdeeKcal = tdee.roundToInt(),
            targetKcal = target,
            macros = MacroTargets(protein.roundToInt(), carbs.roundToInt(), fat.roundToInt()),
        )
    }
}
