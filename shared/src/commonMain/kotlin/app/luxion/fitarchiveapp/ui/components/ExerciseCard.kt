package app.luxion.fitarchiveapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.data.ExerciseStore
import app.luxion.fitarchiveapp.model.ExerciseEntry
import app.luxion.fitarchiveapp.model.ExerciseRules
import app.luxion.fitarchiveapp.ui.screens.DiaryUiState
import app.luxion.fitarchiveapp.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** «45 min», «1 h», «1 h 30 min». */
fun formatMinutes(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

/** Texto del efecto del ejercicio sobre el objetivo: «+120 kcal al objetivo (50 %)» o el aviso de desactivado. */
fun exerciseEffectText(exerciseKcal: Int, bonusKcal: Int, percent: Int): String = when {
    percent <= 0 -> "Desactivado en Objetivos: no suma al objetivo."
    exerciseKcal <= 0 -> "Suma el $percent % de lo que gastes al objetivo del día."
    else -> "+${formatKcal(bonusKcal.toDouble())} kcal al objetivo ($percent % de ${formatKcal(exerciseKcal.toDouble())})"
}

/**
 * Tarjeta de ejercicio del Diario (docs/design.md §2.14): actividades del día, lo que suman al objetivo y
 * «+ Añadir ejercicio». El estado viene del [DiaryUiState]; las acciones las gestiona el presentador.
 */
@Composable
fun ExerciseCard(
    state: DiaryUiState,
    onAdd: () -> Unit,
    onDelete: (ExerciseEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            // Sin actividades la tarjeta es una sola fila (título, efecto y «+ Añadir»), para no alargar el Diario.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row {
                        Text("Ejercicio", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (state.exerciseKcal > 0) {
                            Text("${formatKcal(state.exerciseKcal.toDouble())} kcal", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Text(
                        exerciseEffectText(state.exerciseKcal, state.exerciseBonusKcal, state.exercisePercent),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = onAdd,
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Añadir ejercicio" },
                ) { Text("+ Añadir") }
            }
            state.exercises.forEach { entry ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                        entry.minutes?.let {
                            Text(
                                formatMinutes(it),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text("${formatKcal(entry.kcal.toDouble())} kcal", style = MaterialTheme.typography.bodyMedium)
                    TextButton(
                        onClick = { onDelete(entry) },
                        modifier = Modifier.semantics { contentDescription = "Quitar ${entry.name}" },
                    ) { Text("Quitar", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

/** Diálogo para registrar una actividad: nombre, kcal gastadas y duración opcional. */
@Composable
fun ExerciseDialog(onDismiss: () -> Unit, onConfirm: (name: String, kcal: Int, minutes: Int?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kcalText by remember { mutableStateOf("") }
    var minutesText by remember { mutableStateOf("") }
    val kcal = parseNumber(kcalText)?.toInt()
    val minutes = parseNumber(minutesText)?.toInt()
    val nameOk = name.isNotBlank() && name.trim().length <= ExerciseRules.MAX_NAME_LENGTH
    val kcalOk = kcal != null && ExerciseRules.isValidKcal(kcal)
    val minutesOk = minutesText.isEmpty() || (minutes != null && ExerciseRules.isValidMinutes(minutes))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir ejercicio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(ExerciseRules.MAX_NAME_LENGTH) },
                    label = { Text("Actividad") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.fillMaxWidth(),
                )
                NumberField(
                    kcalText, { kcalText = it }, "Kcal gastadas", Modifier.fillMaxWidth(), suffix = "kcal",
                    isError = kcalText.isNotEmpty() && !kcalOk,
                    supportingText = "Entre 1 y ${ExerciseRules.MAX_KCAL}",
                )
                NumberField(
                    minutesText, { minutesText = it }, "Duración (opcional)", Modifier.fillMaxWidth(), suffix = "min",
                    isError = !minutesOk,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), kcal!!, minutes.takeIf { minutesText.isNotEmpty() }) },
                enabled = nameOk && kcalOk && minutesOk,
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/**
 * Ajuste de Objetivos: qué porcentaje del ejercicio se suma al objetivo, con el criterio contra el doble conteo.
 * Se guarda al elegir, como las comidas al día.
 */
@Composable
fun ExercisePercentPicker(store: ExerciseStore, modifier: Modifier = Modifier) {
    var percent by remember(store) { mutableStateOf(ExerciseRules.DEFAULT_PERCENT) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(store) {
        try {
            percent = store.percent()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
        }
    }

    fun choose(value: Int) {
        scope.launch {
            try {
                store.setPercent(value, kotlin.time.Clock.System.now().toEpochMilliseconds())
                percent = value
                failed = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
            }
        }
    }

    // Un porcentaje importado que no es de los habituales se muestra también como opción.
    val choices = (ExerciseRules.PERCENT_CHOICES + percent).distinct().sorted()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            "Tu nivel de actividad ya cuenta el ejercicio habitual. Registra en el Diario solo el que no esté " +
                "incluido y elige cuánto de él se suma al objetivo del día.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        choices.forEach { value ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .selectable(selected = percent == value, onClick = { choose(value) }, role = Role.RadioButton),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = percent == value, onClick = null)
                Text(
                    exercisePercentLabel(value),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = Spacing.m),
                )
            }
        }
        if (failed) {
            Text(
                "No se pudo guardar o leer el ajuste de ejercicio.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** «No sumar», «Sumar el 50 %» o «Sumar todo». */
fun exercisePercentLabel(percent: Int): String = when (percent) {
    0 -> "No sumar (desactivado)"
    ExerciseRules.MAX_PERCENT -> "Sumar todo (100 %)"
    ExerciseRules.DEFAULT_PERCENT -> "Sumar el 50 % (recomendado)"
    else -> "Sumar el $percent %"
}
