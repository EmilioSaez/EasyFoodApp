package app.luxion.fitarchiveapp.domain

import app.luxion.fitarchiveapp.model.MealType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Ajustes de los recordatorios locales. Se guardan como un único JSON en `SettingsEntity.reminders`.
 * Todo va desactivado hasta que el usuario lo enciende ([enabled]). Las horas son minutos desde medianoche (hora local)
 * y los días son ISO (1 = lunes … 7 = domingo).
 */
@Serializable
data class ReminderSettings(
    /** Interruptor general: con `false` no se programa nada. */
    val enabled: Boolean = false,
    /** Recordar registrar cada comida del día (las del preset de «comidas al día»). */
    val meals: Boolean = true,
    /** Hora de cada comida; las que faltan usan [defaultMealMinutes]. */
    val mealTimes: Map<MealType, Int> = emptyMap(),
    /** Días de la semana en que se recuerdan las comidas. */
    val mealDays: Set<Int> = ALL_DAYS,
    val weight: Boolean = true,
    /** Día de la semana del aviso de pesaje. */
    val weightDay: Int = DEFAULT_WEIGHT_DAY,
    val weightMinutes: Int = DEFAULT_WEIGHT_MINUTES,
    /** Recordatorio de agua (opcional, apagado por defecto). */
    val water: Boolean = false,
    val waterMinutes: Int = DEFAULT_WATER_MINUTES,
) {
    fun mealMinutes(meal: MealType): Int = mealTimes[meal] ?: defaultMealMinutes(meal)

    /** Copia con todo dentro de rango: días 1..7, horas 0..1439 (lo importado o leído puede venir manipulado). */
    fun sanitized(): ReminderSettings = copy(
        mealTimes = mealTimes.mapValues { it.value.coerceIn(0, MAX_MINUTE) },
        mealDays = mealDays.filter { it in 1..7 }.toSet(),
        weightDay = weightDay.takeIf { it in 1..7 } ?: DEFAULT_WEIGHT_DAY,
        weightMinutes = weightMinutes.coerceIn(0, MAX_MINUTE),
        waterMinutes = waterMinutes.coerceIn(0, MAX_MINUTE),
    )

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        val ALL_DAYS: Set<Int> = (1..7).toSet()
        const val MAX_MINUTE = 24 * 60 - 1
        const val DEFAULT_WEIGHT_DAY = 1
        const val DEFAULT_WEIGHT_MINUTES = 8 * 60
        const val DEFAULT_WATER_MINUTES = 17 * 60

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /** Lee el JSON guardado; `null` si está vacío o corrupto (se usa entonces el valor por defecto). */
        fun fromJson(text: String?): ReminderSettings? {
            if (text.isNullOrBlank()) return null
            return try {
                json.decodeFromString(serializer(), text).sanitized()
            } catch (e: Exception) {
                null
            }
        }

        /** Hora por defecto de cada comida. */
        fun defaultMealMinutes(meal: MealType): Int = when (meal) {
            MealType.BREAKFAST -> 9 * 60
            MealType.MID_MORNING -> 11 * 60 + 30
            MealType.LUNCH -> 14 * 60 + 30
            MealType.SNACK -> 18 * 60
            MealType.DINNER -> 21 * 60 + 30
            MealType.LATE_SNACK -> 23 * 60
        }
    }
}
