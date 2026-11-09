package app.luxion.fitarchiveapp.model

import kotlin.math.roundToLong

/** Fecha ISO (`YYYY-MM-DD`) de calendario real → días desde 1970-01-01; `null` si el texto no lo es. */
fun isoDateToEpochDay(date: String): Long? {
    if (date.length != 10 || date[4] != '-' || date[7] != '-') return null
    if (date.withIndex().any { (i, c) -> i != 4 && i != 7 && !c.isDigit() }) return null
    val y = date.substring(0, 4).toInt()
    val m = date.substring(5, 7).toInt()
    val d = date.substring(8, 10).toInt()
    if (m !in 1..12 || d < 1 || d > daysInMonth(y, m)) return null
    return daysFromCivil(y, m, d)
}

/** Inversa de [isoDateToEpochDay]. */
fun epochDayToIsoDate(epochDay: Long): String {
    val z = epochDay + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val y = yoe + era * 400 + if (m <= 2) 1 else 0
    return "${y.toString().padStart(4, '0')}-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
}

private fun isLeap(y: Int) = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0

private fun daysInMonth(y: Int, m: Int) = when (m) {
    2 -> if (isLeap(y)) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}

private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
    val y = (if (month <= 2) year - 1 else year).toLong()
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val doy = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
}

/** Peso corporal registrado un día. */
data class WeightEntry(
    val date: String,
    val weightKg: Double,
    val createdAtMillis: Long = 0,
    val updatedAtMillis: Long = 0,
)

/** Peso a guardar; valida al construirse (fecha real y peso entre [WeightRules.MIN_KG] y [WeightRules.MAX_KG]). */
data class NewWeightEntry(val date: String, val weightKg: Double) {
    init {
        require(isoDateToEpochDay(date) != null) { "Fecha no válida: $date" }
        require(weightKg.isFinite() && weightKg in WeightRules.MIN_KG..WeightRules.MAX_KG) {
            "El peso debe estar entre ${WeightRules.MIN_KG.toInt()} y ${WeightRules.MAX_KG.toInt()} kg: $weightKg"
        }
    }
}

/** Peso objetivo elegido por el usuario. */
data class WeightGoal(val targetKg: Double, val updatedAtMillis: Long = 0)

object WeightRules {
    const val MIN_KG = 20.0
    const val MAX_KG = 500.0

    /** Redondea a 0,1 kg (la precisión de una báscula doméstica). */
    fun round(kg: Double): Double = (kg * 10).roundToLong() / 10.0

    /** Interpreta lo escrito por el usuario (coma o punto decimal). */
    fun parse(text: String): WeightParse {
        val t = text.trim().replace(',', '.')
        if (t.isEmpty()) return WeightParse.Invalid("Escribe tu peso")
        val kg = t.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return WeightParse.Invalid("Peso no válido")
        if (kg < MIN_KG || kg > MAX_KG) {
            return WeightParse.Invalid("Debe estar entre ${MIN_KG.toInt()} y ${MAX_KG.toInt()} kg")
        }
        return WeightParse.Valid(round(kg))
    }
}

sealed interface WeightParse {
    data class Valid(val kg: Double) : WeightParse
    data class Invalid(val message: String) : WeightParse
}
