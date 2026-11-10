package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import app.luxion.fitarchiveapp.ui.components.EasyFoodFilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.ai.AiAdviceLimits
import app.luxion.fitarchiveapp.ai.AiAdvicePoint
import app.luxion.fitarchiveapp.ai.AiReview
import app.luxion.fitarchiveapp.ai.AiReviewPeriod
import app.luxion.fitarchiveapp.ai.MealSuggestion
import app.luxion.fitarchiveapp.ui.components.label
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.navigation.Route
import app.luxion.fitarchiveapp.ui.theme.Spacing
import kotlinx.coroutines.launch
import kotlin.math.round

/** Texto fijo bajo las tarjetas: las respuestas son orientativas. */
const val AI_DISCLAIMER = "Las respuestas de la IA son orientativas y pueden contener errores. No sustituyen a un profesional de la salud."

/** Error de una llamada, en el color `error` (o neutro si es un rechazo de la IA). */
@Composable
private fun AiFailure(failed: AiLoad.Failed, onRetry: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            failed.message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (failed.refusal) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        )
        if (failed.retryable && onRetry != null) TextButton(onClick = onRetry) { Text("Reintentar") }
    }
}

/** Botón de una acción de IA: deshabilitado en curso, sin aviso aceptado o con el tope alcanzado. */
@Composable
private fun AiActionButton(text: String, running: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled && !running, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(if (running) "Pensando…" else text)
    }
}

/** «¿Qué como?»: sugerencias para lo que queda del día. */
@Composable
fun AiSuggestCard(state: AiTabState, navigator: Navigator) {
    val scope = rememberCoroutineScope()
    var preferences by remember { mutableStateOf("") }
    val enabled = state.advicePrivacyAccepted && !state.limitReached
    AiCard("¿Qué como?") {
        Text(
            "Ideas para lo que queda del día según tus kcal y macros restantes y lo que sueles comer.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = preferences,
            onValueChange = { preferences = it.take(AiAdviceLimits.MAX_PREFERENCES) },
            label = { Text("Preferencias (opcional): sin gluten, rápido…") },
            modifier = Modifier.fillMaxWidth(),
        )
        AiActionButton("Sugerir comida", state.suggestions == AiLoad.Loading, enabled) { scope.launch { state.suggest(preferences) } }
        when (val s = state.suggestions) {
            AiLoad.Idle, AiLoad.Loading -> Unit
            is AiLoad.Failed -> AiFailure(s) { scope.launch { state.suggest(preferences) } }
            is AiLoad.Done -> {
                s.value.forEachIndexed { i, option ->
                    SuggestionRow(option, state.added[i], onAdd = { scope.launch { state.addToDiary(i) } },
                        onUndo = { scope.launch { state.undoAdd(i) } },
                        onEdit = { id -> navigator.push(Route.QuickEntry(entryId = id)) })
                }
                state.addError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                Text(
                    "Cifras aproximadas. Se apuntan como entrada rápida; puedes ajustarlas después.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    option: MealSuggestion,
    added: AddedSuggestion?,
    onAdd: () -> Unit,
    onUndo: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(option.diaryName, style = MaterialTheme.typography.titleMedium)
            Text(
                "~${option.kcal.int()} kcal · P ${option.proteinG.int()} g · H ${option.carbsG.int()} g · G ${option.fatG.int()} g",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (option.reason.isNotBlank()) {
                Text(option.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (added == null) {
                OutlinedButton(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) { Text("Añadir al diario") }
            } else {
                Text("Añadido a ${added.meal.label}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    TextButton(onClick = { onEdit(added.entryId) }) { Text("Ajustar") }
                    TextButton(onClick = onUndo) { Text("Deshacer") }
                }
            }
        }
    }
}

/** «¿Cómo voy?»: resumen y consejo del día o de la semana. */
@Composable
fun AiReviewCard(state: AiTabState) {
    val scope = rememberCoroutineScope()
    val enabled = state.advicePrivacyAccepted && !state.limitReached
    AiCard("¿Cómo voy?") {
        Text(
            "Un resumen breve de kcal, proteína, fibra, sal, azúcares y agua, con un consejo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val running = state.review == AiLoad.Loading
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            AiReviewPeriod.entries.forEach { period ->
                val selected = state.reviewPeriod == period && state.review !is AiLoad.Idle
                EasyFoodFilterChip(
                    selected = selected,
                    onClick = { if (enabled && !running) scope.launch { state.review(period) } },
                    label = { Text(if (period == AiReviewPeriod.DAY) "Hoy" else "Esta semana") },
                    enabled = enabled && !running,
                )
            }
        }
        if (state.review == AiLoad.Idle) Text("Elige «Hoy» o «Esta semana».", style = MaterialTheme.typography.bodySmall)
        when (val r = state.review) {
            AiLoad.Idle -> Unit
            AiLoad.Loading -> Text("Pensando…", style = MaterialTheme.typography.bodyMedium)
            is AiLoad.Failed -> AiFailure(r) { scope.launch { state.review(state.reviewPeriod) } }
            is AiLoad.Done -> ReviewContent(r.value)
        }
    }
}

@Composable
private fun ReviewContent(review: AiReview) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(review.summary, style = MaterialTheme.typography.bodyLarge)
        review.points.forEach { PointRow(it) }
    }
}

@Composable
private fun PointRow(point: AiAdvicePoint) {
    val (mark, color) = when (point.status) {
        "alto" -> "▲" to MaterialTheme.colorScheme.error
        "bajo" -> "▼" to MaterialTheme.colorScheme.tertiary
        else -> "✓" to MaterialTheme.colorScheme.primary
    }
    val statusText = when (point.status) { "alto" -> "alto"; "bajo" -> "bajo"; else -> "bien" }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(mark, color = color, style = MaterialTheme.typography.titleMedium)
        Column {
            Text(
                listOf(point.topic.replaceFirstChar { it.uppercase() }, statusText).filter { it.isNotEmpty() }.joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(point.advice, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Pregunta libre con una conversación corta (solo los últimos mensajes, en memoria). */
@Composable
fun AiAskCard(state: AiTabState) {
    val scope = rememberCoroutineScope()
    var question by remember { mutableStateOf("") }
    val enabled = state.advicePrivacyAccepted && !state.limitReached
    val running = state.chatStatus == AiLoad.Loading
    AiCard("Pregunta lo que quieras") {
        Text(
            "Dudas sobre alimentación. Solo se recuerdan los últimos mensajes para gastar poco.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.chat.forEach { turn ->
            Text(
                (if (turn.fromUser) "Tú: " else "IA: ") + turn.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (turn.fromUser) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
        OutlinedTextField(
            value = question,
            onValueChange = { question = it.take(AiAdviceLimits.MAX_QUESTION) },
            label = { Text("Tu pregunta") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            supportingText = { Text("${question.length}/${AiAdviceLimits.MAX_QUESTION}") },
            modifier = Modifier.fillMaxWidth(),
        )
        AiActionButton("Preguntar", running, enabled && question.isNotBlank()) {
            val q = question
            scope.launch {
                state.ask(q)
                if (state.chatStatus is AiLoad.Done) question = ""
            }
        }
        (state.chatStatus as? AiLoad.Failed)?.let { f -> AiFailure(f) { scope.launch { state.ask(question) } } }
        if (state.chat.isNotEmpty()) TextButton(onClick = { state.clearChat() }) { Text("Borrar conversación") }
    }
}

private fun Double.int(): Int = round(this).toInt()
