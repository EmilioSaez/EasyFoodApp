package app.luxion.fitarchiveapp.ui.components

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/** Formato es-ES sin depender de la plataforma: miles con «.» y decimales con «,» (1.215 · 4,6). */
fun formatNumber(value: Double, decimals: Int = 0): String {
    val factor = 10.0.pow(decimals).toLong()
    val scaled = (abs(value) * factor).roundToLong()
    val intPart = (scaled / factor).toString()
    val grouped = intPart.reversed().chunked(3).joinToString(".").reversed()
    val frac = if (decimals > 0) "," + (scaled % factor).toString().padStart(decimals, '0') else ""
    val negative = value < 0 && scaled != 0L
    return (if (negative) "-" else "") + grouped + frac
}

fun formatKcal(value: Double): String = formatNumber(value, 0)

/** Gramos con un decimal solo si hace falta (68 · 4,6). */
fun formatGrams(value: Double): String = formatNumber(value, 1).removeSuffix(",0")
