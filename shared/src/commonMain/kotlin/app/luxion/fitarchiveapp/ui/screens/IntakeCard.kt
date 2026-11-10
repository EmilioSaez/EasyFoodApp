package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** Desglose del día de fibra, sal, azúcares y saturadas con objetivos orientativos (docs/design.md §2.14). */
@Composable
fun IntakeCard(intake: List<NutrientIntake>, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text("OTROS NUTRIENTES", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            intake.forEach { n ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(n.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        n.valueText(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            n.exceeded -> MaterialTheme.colorScheme.error
                            n.incomplete || !n.hasData -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
            Text(
                "Objetivos orientativos (OMS), no consejo médico.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
