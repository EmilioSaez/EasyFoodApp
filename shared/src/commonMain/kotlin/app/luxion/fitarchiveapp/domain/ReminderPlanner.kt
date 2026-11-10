package app.luxion.fitarchiveapp.domain

import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.WeightEntry
import app.luxion.fitarchiveapp.model.epochDayToIsoDate
import app.luxion.fitarchiveapp.model.isoDateToEpochDay
import kotlinx.serialization.Serializable

enum class ReminderKind { MEAL, WEIGHT, WATER }

/**
 * Aviso concreto para programar: [date] (ISO) y [minutes] (desde medianoche) en hora local del dispositivo.
 * [id] es estable (misma fecha y mismo aviso → mismo id) para poder reemplazarlo.
 */
@Serializable
data class PlannedReminder(
    val id: Int,
    val date: String,
    val minutes: Int,
    val kind: ReminderKind,
    val title: String,
    val body: String,
)

/** Lo que ya se ha registrado hoy; con ello el planificador no avisa de lo que ya está hecho. */
data class ReminderContext(
    val today: String,
    val nowMinutes: Int,
    val mealsPerDay: Int?,
    val loggedMealsToday: Set<MealType> = emptySet(),
    val weights: List<WeightEntry> = emptyList(),
    val waterTodayMl: Int = 0,
    val waterGoalMl: Int = Int.MAX_VALUE,
)

/**
 * Decide qué avisos programar para los próximos [HORIZON_DAYS] días. Es una función pura: el dispositivo la vuelve
 * a ejecutar (y reemplaza lo programado) al abrir la app y tras cada cambio de pantalla, de modo que un aviso de hoy
 * desaparece en cuanto se registra esa comida o ese peso.
 */
object ReminderPlanner {
    const val HORIZON_DAYS = 7

    private const val SLOT_WEIGHT = 10
    private const val SLOT_WATER = 11

    fun plan(settings: ReminderSettings, ctx: ReminderContext): List<PlannedReminder> {
        if (!settings.enabled) return emptyList()
        val todayDay = isoDateToEpochDay(ctx.today) ?: return emptyList()
        val s = settings.sanitized()
        val meals = MealPlan.preset(ctx.mealsPerDay)
        val weightDays = ctx.weights.mapNotNull { isoDateToEpochDay(it.date) }
        val out = mutableListOf<PlannedReminder>()

        for (offset in 0 until HORIZON_DAYS) {
            val day = todayDay + offset
            val date = epochDayToIsoDate(day)
            val dow = isoWeekday(day)
            fun upcoming(minutes: Int) = offset > 0 || minutes > ctx.nowMinutes
            fun id(slot: Int) = (day * 100 + slot).toInt()

            if (s.meals && dow in s.mealDays) {
                for (meal in meals) {
                    val minutes = s.mealMinutes(meal)
                    if (!upcoming(minutes)) continue
                    if (offset == 0 && meal in ctx.loggedMealsToday) continue
                    out += PlannedReminder(
                        id(meal.ordinal), date, minutes, ReminderKind.MEAL,
                        "Registra tu ${mealName(meal)}", "Apunta lo que has comido para llevar el día al corriente.",
                    )
                }
            }
            if (s.weight && dow == s.weightDay && upcoming(s.weightMinutes)) {
                // Ya pesado esta semana (lunes a ese día): no se avisa.
                val weekStart = day - (dow - 1)
                if (weightDays.none { it in weekStart..day }) {
                    out += PlannedReminder(
                        id(SLOT_WEIGHT), date, s.weightMinutes, ReminderKind.WEIGHT,
                        "Hora de pesarte", "Registra tu peso de esta semana.",
                    )
                }
            }
            if (s.water && upcoming(s.waterMinutes) && !(offset == 0 && ctx.waterTodayMl >= ctx.waterGoalMl)) {
                out += PlannedReminder(
                    id(SLOT_WATER), date, s.waterMinutes, ReminderKind.WATER,
                    "Bebe agua", "Apunta el agua que llevas hoy.",
                )
            }
        }
        return out
    }

    /** Día de la semana ISO (1 = lunes … 7 = domingo) de un día desde 1970-01-01 (jueves). */
    fun isoWeekday(epochDay: Long): Int = (((epochDay + 3) % 7 + 7) % 7).toInt() + 1

    private fun mealName(meal: MealType): String = when (meal) {
        MealType.BREAKFAST -> "desayuno"
        MealType.MID_MORNING -> "almuerzo"
        MealType.LUNCH -> "comida"
        MealType.SNACK -> "merienda"
        MealType.DINNER -> "cena"
        MealType.LATE_SNACK -> "recena"
    }
}
