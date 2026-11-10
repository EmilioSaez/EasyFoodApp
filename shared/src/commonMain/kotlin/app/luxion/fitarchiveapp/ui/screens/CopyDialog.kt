package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.luxion.fitarchiveapp.domain.MealPlan
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.ui.components.label
import app.luxion.fitarchiveapp.ui.theme.Spacing

/** Qué se copia: una comida del día mostrado o el día entero (docs/design.md §2.15). */
data class CopyRequest(val meal: MealType?)

/**
 * Elige el destino de «Copiar» / «Copiar día»: fecha (por defecto, el día anterior) y, solo al copiar una comida,
 * la comida (por defecto, la equivalente).
 */
@Composable
fun CopyDialog(
    request: CopyRequest,
    sourceDate: String,
    mealsPerDay: Int?,
    onDismiss: () -> Unit,
    onConfirm: (targetDate: String, targetMeal: MealType?) -> Unit,
) {
    var date by remember { mutableStateOf(IsoDate.plusDays(sourceDate, -1)) }
    var meal by remember { mutableStateOf<MealType?>(null) } // null = la equivalente
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (request.meal == null) "Copiar día" else "Copiar ${request.meal.label}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text("Copiar a", style = MaterialTheme.typography.labelLarge)
                DateStepperRow(date, onDateChange = { date = it; error = null })
                if (request.meal != null) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        FilterChip(selected = meal == null, onClick = { meal = null; error = null }, label = { Text("La misma") })
                        MealPlan.preset(mealsPerDay).forEach { m ->
                            FilterChip(selected = meal == m, onClick = { meal = m; error = null }, label = { Text(m.label) })
                        }
                    }
                } else {
                    Text(
                        "Cada comida va a la misma del día elegido; si no existe en tu preset, a la más cercana.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Aviso ámbar dentro del diálogo (en vez del snackbar del Diario): no se cierra hasta que el destino es válido.
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val problem = copyTargetError(request.meal, sourceDate, date, meal)
                if (problem == null) onConfirm(date, meal) else error = problem
            }) { Text("Copiar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
