package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.model.MealTemplate
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.ui.components.label
import app.luxion.fitarchiveapp.ui.theme.Spacing

/** Fila «‹ fecha ›» con el selector de fecha: la comparten «Copiar» y «Añadir plantilla». */
@Composable
fun DateStepperRow(date: String, onDateChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var picking by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { onDateChange(IsoDate.plusDays(date, -1)) }) { Text("‹") }
        TextButton(onClick = { picking = true }, modifier = Modifier.weight(1f)) { Text(IsoDate.long(date)) }
        TextButton(onClick = { onDateChange(IsoDate.plusDays(date, 1)) }) { Text("›") }
    }
    if (picking) {
        DiaryDatePicker(date = date, onPick = { onDateChange(it); picking = false }, onDismiss = { picking = false })
    }
}

/**
 * Pide el nombre de una plantilla (al guardarla desde una comida del Diario o al renombrarla). [error] se muestra
 * bajo el campo y el diálogo no se cierra: lo cierra quien llama cuando se ha guardado.
 */
@Composable
fun TemplateNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre") },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim()) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Elige fecha (por defecto, hoy) y comida donde añadir [template] al diario. */
@Composable
fun AddTemplateDialog(
    template: MealTemplate,
    today: String,
    initialMeal: MealType,
    mealChoices: MealChoices,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (date: String, meal: MealType) -> Unit,
) {
    var date by remember(template) { mutableStateOf(today) }
    var meal by remember(template) { mutableStateOf(initialMeal) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(template.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(template.listDetail(), style = MaterialTheme.typography.bodyMedium)
                Text("Añadir a", style = MaterialTheme.typography.labelLarge)
                DateStepperRow(date, onDateChange = { date = it })
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    mealChoices.options(initialMeal).forEach { m ->
                        FilterChip(selected = meal == m, onClick = { meal = m }, label = { Text(m.label) })
                    }
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(date, meal) }) { Text("Añadir") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
