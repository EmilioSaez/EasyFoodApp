package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.luxion.fitarchiveapp.data.WeightStore
import app.luxion.fitarchiveapp.model.NewWeightEntry
import app.luxion.fitarchiveapp.model.WeightEntry
import app.luxion.fitarchiveapp.model.WeightParse
import app.luxion.fitarchiveapp.model.WeightRules
import app.luxion.fitarchiveapp.ui.components.NumberField
import app.luxion.fitarchiveapp.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Diálogo para registrar o editar ([editing]) un peso. Lo usan «Tú» y «Mi peso». Al guardar llama a [onSaved] con el
 * mensaje a mostrar (el llamador cierra el diálogo y recarga); si falla, [onError] y el diálogo sigue abierto.
 * [entries] sirve para avisar de que ese día ya había un peso.
 */
@Composable
fun WeightEntryDialog(
    weightStore: WeightStore,
    entries: List<WeightEntry>,
    todayIso: String,
    editing: WeightEntry?,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
    onError: (String) -> Unit,
) {
    var dateText by remember { mutableStateOf(editing?.date ?: todayIso) }
    var weightText by remember { mutableStateOf(editing?.let { formatKgInput(it.weightKg) } ?: "") }
    var submitted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val dateError = weightDateError(dateText, todayIso)
    val parsed = WeightRules.parse(weightText)
    val weightError = (parsed as? WeightParse.Invalid)?.message

    fun save() {
        submitted = true
        val kg = (parsed as? WeightParse.Valid)?.kg
        if (dateError != null || kg == null) return
        val entry = NewWeightEntry(dateText.trim(), kg)
        val original = editing?.date
        scope.launch {
            try {
                val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
                val replaced = original == null && entries.any { it.date == entry.date }
                if (original != null) weightStore.update(original, entry, now) else weightStore.save(entry, now)
                onSaved(
                    when {
                        original != null -> "Registro actualizado"
                        replaced -> "Ya había un peso ese día: actualizado"
                        else -> "Peso guardado"
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError("No se pudo guardar el peso. Inténtalo de nuevo.")
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "Nuevo registro" else "Editar registro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it.filter { c -> c.isDigit() || c == '-' }.take(10) },
                    label = { Text("Fecha") },
                    isError = dateError != null,
                    supportingText = { Text(dateError ?: "AAAA-MM-DD") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.fillMaxWidth(),
                )
                NumberField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    label = "Peso",
                    suffix = "kg",
                    isError = submitted && weightError != null,
                    supportingText = if (submitted && weightError != null) weightError else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { Button(onClick = ::save) { Text(if (editing == null) "Guardar peso" else "Guardar cambios") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
