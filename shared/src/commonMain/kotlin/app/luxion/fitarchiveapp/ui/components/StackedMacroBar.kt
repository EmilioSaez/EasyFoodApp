package app.luxion.fitarchiveapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.ui.theme.Spacing
import app.luxion.fitarchiveapp.ui.theme.easyFoodColors

/** Un macro de la barra: nombre, gramos y porcentaje de la energía (los tres suman 100 %). */
data class MacroShare(val label: String, val grams: Double, val percent: Int)

private const val KcalPerGramProtein = 4.0
private const val KcalPerGramCarbs = 4.0
private const val KcalPerGramFat = 9.0

/**
 * Reparto de energía en proteína, hidratos y grasa (4/4/9 kcal/g), en ese orden.
 * Los porcentajes son enteros y suman siempre 100 (resto mayor); si no hay macros, todos son 0.
 */
fun macroShares(proteinG: Double, carbsG: Double, fatG: Double): List<MacroShare> {
    val grams = listOf(proteinG, carbsG, fatG).map { if (it.isFinite()) it.coerceAtLeast(0.0) else 0.0 }
    val energy = listOf(grams[0] * KcalPerGramProtein, grams[1] * KcalPerGramCarbs, grams[2] * KcalPerGramFat)
    val total = energy.sum()
    val percents = IntArray(3)
    if (total > 0) {
        val exact = energy.map { it * 100 / total }
        for (i in 0..2) percents[i] = exact[i].toInt()
        val missing = 100 - percents.sum()
        exact.indices.sortedByDescending { exact[it] - percents[it] }.take(missing).forEach { percents[it]++ }
    }
    val labels = listOf("Proteína", "Hidratos", "Grasa")
    return labels.indices.map { MacroShare(labels[it], grams[it], percents[it]) }
}

/** Texto para lectores de pantalla: «Proteína 30 g, 24 %; Hidratos 50 g, 40 %; Grasa 25 g, 36 %». */
fun macroBarDescription(shares: List<MacroShare>): String =
    shares.joinToString("; ") { "${it.label} ${formatGrams(it.grams)} g, ${it.percent} %" }

/**
 * Barra apilada de proteína / hidratos / grasa con su leyenda: punto de color, nombre, gramos y porcentaje de energía.
 * Usa los colores de macros de `easyFoodColors` (claro y oscuro) y deja huecos de 2 dp entre segmentos.
 * El color nunca va solo: la leyenda lleva el texto y toda la barra se lee como un único elemento accesible.
 */
@Composable
fun StackedMacroBar(
    proteinG: Double,
    carbsG: Double,
    fatG: Double,
    modifier: Modifier = Modifier,
    barHeight: Dp = 8.dp,
    showLegend: Boolean = true,
) {
    val shares = macroShares(proteinG, carbsG, fatG)
    val colors = MaterialTheme.easyFoodColors.let { listOf(it.macroProtein, it.macroCarbs, it.macroFat) }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val shape = RoundedCornerShape(barHeight / 2)
    Column(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = macroBarDescription(shares) },
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            Modifier.fillMaxWidth().height(barHeight).clip(shape).background(track),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            shares.forEachIndexed { i, share ->
                // El ancho sigue la energía; un macro a 0 no ocupa sitio.
                val energy = share.percent.toFloat()
                if (energy > 0f) Box(Modifier.weight(energy).height(barHeight).background(colors[i]))
            }
        }
        if (showLegend) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                shares.forEachIndexed { i, share ->
                    Column(Modifier.weight(1f).clearAndSetSemantics {}) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MacroDot(colors[i], size = 8.dp)
                            Text(
                                share.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = Spacing.xs),
                            )
                        }
                        Text("${formatGrams(share.grams)} g", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${share.percent} %",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
