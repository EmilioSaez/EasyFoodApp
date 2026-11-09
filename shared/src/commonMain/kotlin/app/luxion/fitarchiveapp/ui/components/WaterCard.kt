package app.luxion.fitarchiveapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.data.WaterStore
import app.luxion.fitarchiveapp.model.WaterRules
import app.luxion.fitarchiveapp.ui.theme.Spacing
import app.luxion.fitarchiveapp.ui.theme.easyFoodColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** «1,25», «2», «0,5»: litros con coma y sin ceros sobrantes (docs/design.md §2.13). */
fun formatLiters(ml: Int): String {
    val hundredths = (ml.toLong() * 100 + 500) / 1000 // redondeo a 0,01 L
    val whole = hundredths / 100
    val frac = (hundredths % 100).toString().padStart(2, '0').trimEnd('0')
    return if (frac.isEmpty()) "$whole" else "$whole,$frac"
}

/**
 * Tarjeta de agua del Diario: total del día frente al objetivo, botones rápidos y «Deshacer» de la última suma
 * (solo mientras se está en este día y esta pantalla). [date] es la fecha ISO del día que muestra el Diario.
 */
@Composable
fun WaterCard(store: WaterStore, date: String, modifier: Modifier = Modifier) {
    var totalMl by remember(store, date) { mutableStateOf(0) }
    var goalMl by remember(store) { mutableStateOf(WaterRules.DEFAULT_GOAL_ML) }
    var lastAddedMl by remember(store, date) { mutableStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(store, date) {
        try {
            totalMl = store.total(date)
            goalMl = store.goalMl()
            failed = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
        }
    }

    fun change(delta: Int, undo: Boolean) {
        scope.launch {
            try {
                totalMl = store.add(date, delta, kotlin.time.Clock.System.now().toEpochMilliseconds())
                lastAddedMl = if (undo) 0 else delta
                failed = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
            }
        }
    }

    Card(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Agua", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "${formatLiters(totalMl)} de ${formatLiters(goalMl)} L",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            LinearProgressIndicator(
                progress = { WaterRules.progress(totalMl, goalMl).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.easyFoodColors.macroCarbs,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Butt,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            if (failed) {
                Text(
                    "No se pudo guardar o leer el agua.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                QuickAdd("+ Vaso", WaterRules.GLASS_ML, Modifier.weight(1f)) { change(it, undo = false) }
                QuickAdd("+ 500 ml", WaterRules.BOTTLE_ML, Modifier.weight(1f)) { change(it, undo = false) }
                QuickAdd("+ 1 L", WaterRules.LITER_ML, Modifier.weight(1f)) { change(it, undo = false) }
            }
            if (lastAddedMl > 0) {
                TextButton(onClick = { change(-lastAddedMl, undo = true) }) {
                    Text("Deshacer (−${lastAddedMl} ml)")
                }
            }
        }
    }
}

@Composable
private fun QuickAdd(label: String, ml: Int, modifier: Modifier, onAdd: (Int) -> Unit) {
    OutlinedButton(onClick = { onAdd(ml) }, modifier = modifier.heightIn(min = 48.dp)) {
        Text(label, maxLines = 1)
    }
}

/** Paso del objetivo de agua en Objetivos: un vaso. */
private const val GOAL_STEP_ML = WaterRules.GLASS_ML

/** Objetivo diario de agua (Objetivos): «− 2 L +» en pasos de un vaso; se guarda al pulsar, como las comidas al día. */
@Composable
fun WaterGoalRow(store: WaterStore, modifier: Modifier = Modifier) {
    var goalMl by remember(store) { mutableStateOf(WaterRules.DEFAULT_GOAL_ML) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(store) {
        try {
            goalMl = store.goalMl()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
        }
    }

    fun step(delta: Int) {
        val next = goalMl + delta
        if (!WaterRules.isValidGoal(next)) return
        scope.launch {
            try {
                store.setGoalMl(next, kotlin.time.Clock.System.now().toEpochMilliseconds())
                goalMl = next
                failed = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
            }
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { step(-GOAL_STEP_ML) },
                enabled = WaterRules.isValidGoal(goalMl - GOAL_STEP_ML),
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Bajar el objetivo de agua" },
            ) { Text("−") }
            Text(
                "${formatLiters(goalMl)} L al día",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(horizontal = Spacing.m),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            OutlinedButton(
                onClick = { step(GOAL_STEP_ML) },
                enabled = WaterRules.isValidGoal(goalMl + GOAL_STEP_ML),
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Subir el objetivo de agua" },
            ) { Text("+") }
        }
        Text(
            if (failed) "No se pudo guardar el objetivo de agua." else "Se cambia de vaso en vaso (250 ml) y se guarda al momento.",
            style = MaterialTheme.typography.bodySmall,
            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
