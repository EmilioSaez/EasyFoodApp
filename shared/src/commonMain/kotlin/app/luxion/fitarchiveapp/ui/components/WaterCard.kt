package app.luxion.fitarchiveapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.OutlinedCard
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import app.luxion.fitarchiveapp.ui.theme.PillShape
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

    OutlinedCard(
        modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text("AGUA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                buildAnnotatedString {
                    append(formatLiters(totalMl))
                    withStyle(SpanStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = MaterialTheme.typography.bodyMedium.fontFamily)) {
                        append(" / ${formatLiters(goalMl)} L")
                    }
                },
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
            )
            LinearProgressIndicator(
                progress = { WaterRules.progress(totalMl, goalMl).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                strokeCap = StrokeCap.Round,
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
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                QuickAdd("+ Vaso", WaterRules.GLASS_ML) { change(it, undo = false) }
                QuickAdd("+ 500 ml", WaterRules.BOTTLE_ML) { change(it, undo = false) }
            }
            QuickAdd("+ 1 L", WaterRules.LITER_ML) { change(it, undo = false) }
            if (lastAddedMl > 0) {
                Text(
                    "Deshacer (−${lastAddedMl} ml)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(PillShape).clickable(role = Role.Button) { change(-lastAddedMl, undo = true) }
                        .heightIn(min = 40.dp).wrapContentHeight(Alignment.CenterVertically),
                )
            }
        }
    }
}

/** Píldora de 40 dp de zona táctil: suma [ml] al agua del día. */
@Composable
private fun QuickAdd(label: String, ml: Int, onAdd: (Int) -> Unit) {
    Box(
        Modifier.heightIn(min = 40.dp).clip(PillShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button) { onAdd(ml) }
            .padding(horizontal = Spacing.m),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
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
