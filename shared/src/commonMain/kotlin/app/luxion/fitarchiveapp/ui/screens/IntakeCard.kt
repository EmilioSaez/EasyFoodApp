package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.domain.IntakeLimit
import app.luxion.fitarchiveapp.domain.NutrientIntake
import app.luxion.fitarchiveapp.ui.components.formatGrams
import app.luxion.fitarchiveapp.ui.theme.Spacing

/** Texto del total de un nutriente: «12 g de 25 g», «≥ 25 g» sin dato, con «(incompleto)» si faltan datos. */
fun NutrientIntake.valueText(): String {
    val goal = (if (limit == IntakeLimit.AT_LEAST) "mín. " else "máx. ") + "${formatGrams(goalG)} g"
    if (!hasData) return "sin datos · $goal"
    return "${formatGrams(totalG)} g · $goal" + if (incomplete) " · incompleto" else ""
}

/** Texto corto para la celda: «12 / 50 g», «12+ / 50 g» si faltan datos y «—» si ninguna entrada aporta el dato. */
fun NutrientIntake.cellText(): String =
    if (!hasData) "—" else "${formatGrams(totalG)}${if (incomplete) "+" else ""} / ${formatGrams(goalG)} g"

/**
 * Página «otros nutrientes» de la tarjeta de kcal (docs/design.md §2.19): fibra, sal, azúcares y saturadas con la misma
 * estructura que los macros (nombre, «total / objetivo» y barra) para que ambas páginas midan lo mismo.
 */
@Composable
internal fun IntakeLine(intake: List<NutrientIntake>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        intake.forEach { n -> IntakeCell(n, Modifier.weight(1f)) }
    }
}

@Composable
private fun IntakeCell(n: NutrientIntake, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val color = if (n.exceeded) scheme.error else scheme.secondary
    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = "${n.label}: ${n.valueText()}" },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(n.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        Text(
            n.cellText(),
            style = MaterialTheme.typography.labelMedium,
            color = if (n.exceeded) scheme.error else scheme.onSurfaceVariant,
            maxLines = 1,
        )
        LinearProgressIndicator(
            progress = { if (n.hasData && n.goalG > 0) (n.totalG / n.goalG).toFloat().coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = color,
            trackColor = scheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}
