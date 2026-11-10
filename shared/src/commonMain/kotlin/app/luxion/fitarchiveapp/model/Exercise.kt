package app.luxion.fitarchiveapp.model

/**
 * Reglas del ejercicio que suma al objetivo del día (docs/design.md §2.14).
 *
 * Criterio contra el doble conteo: el TDEE ya incluye la actividad habitual (nivel de actividad y sugerencia por
 * pasos), así que solo debe registrarse el ejercicio que NO está en ese nivel. Además, solo se suma un
 * [DEFAULT_PERCENT] % por defecto, porque las kcal de tablas y relojes suelen estar sobreestimadas.
 */
object ExerciseRules {
    /** Porcentaje que se suma si el usuario no lo ha cambiado. */
    const val DEFAULT_PERCENT = 50

    /** 0 = desactivado. */
    const val MIN_PERCENT = 0
    const val MAX_PERCENT = 100

    /** Porcentajes ofrecidos en Objetivos (se admite cualquiera de 0 a 100 al importar). */
    val PERCENT_CHOICES = listOf(0, 25, 50, 75, 100)

    const val MAX_KCAL = 10_000
    const val MAX_MINUTES = 1440
    const val MAX_NAME_LENGTH = 60

    fun isValidPercent(percent: Int) = percent in MIN_PERCENT..MAX_PERCENT

    fun isValidKcal(kcal: Int) = kcal in 1..MAX_KCAL

    fun isValidMinutes(minutes: Int?) = minutes == null || minutes in 1..MAX_MINUTES

    /** Kcal que se suman al objetivo por [exerciseKcal] gastadas: `exerciseKcal × percent / 100`, redondeado. */
    fun bonusKcal(exerciseKcal: Int, percent: Int): Int {
        if (exerciseKcal <= 0 || percent <= 0) return 0
        return ((exerciseKcal.toLong() * percent.coerceAtMost(MAX_PERCENT) + 50) / 100).toInt()
    }
}

/** Una actividad registrada. [minutes] es opcional. */
data class ExerciseEntry(
    val id: Long,
    val date: String,
    val name: String,
    val kcal: Int,
    val minutes: Int? = null,
    val createdAtMillis: Long = 0,
)

/** Actividad por registrar; lanza [IllegalArgumentException] si no es válida. */
data class NewExercise(
    val date: String,
    val name: String,
    val kcal: Int,
    val minutes: Int? = null,
) {
    init {
        require(name.isNotBlank()) { "El nombre del ejercicio no puede estar vacío" }
        require(name.trim().length <= ExerciseRules.MAX_NAME_LENGTH) { "Nombre demasiado largo: ${name.length}" }
        require(ISO_DATE.matches(date)) { "Fecha no válida: $date" }
        require(ExerciseRules.isValidKcal(kcal)) { "Las kcal deben estar entre 1 y ${ExerciseRules.MAX_KCAL}: $kcal" }
        require(ExerciseRules.isValidMinutes(minutes)) { "Duración fuera de rango: $minutes" }
    }
}

private val ISO_DATE = Regex("""\d{4}-\d{2}-\d{2}""")
