package app.luxion.easyfoodapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.luxion.easyfoodapp.ui.components.formatGrams
import app.luxion.easyfoodapp.ui.components.formatKcal
import app.luxion.easyfoodapp.ui.components.label
import app.luxion.easyfoodapp.ui.theme.PillShape
import app.luxion.easyfoodapp.ui.theme.Spacing
import app.luxion.easyfoodapp.ui.theme.easyFoodColors

/** Titular de la tarjeta Coach: lo que queda por comer del día (o una invitación si no hay objetivo). */
internal fun coachHeadline(state: DiaryUiState): String {
    val goal = state.goalKcal ?: return "¿Qué como? Pregúntale al Coach."
    val remaining = goal - state.consumedKcal
    if (remaining < 0) return "Hoy te has pasado ${formatKcal(-remaining)} kcal. ¿Cómo voy?"
    val protein = state.macros.firstOrNull { it.label == "Proteína" }?.let { m -> m.goalG?.let { it - m.consumedG } }
    val proteinText = if (protein != null && protein > 0) " y ${formatGrams(protein)} g de proteína" else ""
    return "¿Qué como? Te quedan ${formatKcal(remaining)} kcal$proteinText."
}

/** Botón de sugerencia: «Sugerir cena» (la primera comida aún vacía). */
internal fun coachSuggestLabel(state: DiaryUiState): String =
    state.meals.firstOrNull { it.entries.isEmpty() }?.meal?.label?.lowercase()?.let { "Sugerir $it" } ?: "Sugerir algo"

/**
 * Tarjeta Coach de Hoy (§2.26): limón, con lo que queda del día y dos accesos al Coach IA ([onOpenCoach]).
 * Ambos botones abren la misma pantalla; ahí se elige «¿Qué como?» o «¿Cómo voy?».
 */
@Composable
fun DiaryCoachCard(state: DiaryUiState, onOpenCoach: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.easyFoodColors
    Column(
        modifier.fillMaxWidth().background(colors.accent, MaterialTheme.shapes.large).padding(Spacing.l + Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text("✦ Coach", style = MaterialTheme.typography.titleMedium, color = colors.onAccent)
        Text(coachHeadline(state), style = MaterialTheme.typography.titleLarge, color = colors.onAccent)
        Row(Modifier.padding(top = Spacing.s), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            CoachButton(coachSuggestLabel(state), colors.onAccent, colors.accent, onOpenCoach)
            CoachButton("¿Cómo voy?", colors.onAccent.copy(alpha = 0.12f), colors.onAccent, onOpenCoach)
        }
    }
}

@Composable
private fun CoachButton(text: String, container: Color, content: Color, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 44.dp).clip(PillShape).background(container)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.l),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1) }
}
