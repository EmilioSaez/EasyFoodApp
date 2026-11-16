package app.luxion.easyfoodapp.ui.screens

import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import app.luxion.easyfoodapp.ui.components.EasyFoodFilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.luxion.easyfoodapp.ai.AiAdviceLimits
import app.luxion.easyfoodapp.ai.AiAdvicePoint
import app.luxion.easyfoodapp.ai.AiReview
import app.luxion.easyfoodapp.ai.AiReviewPeriod
import app.luxion.easyfoodapp.ai.MealSuggestion
import app.luxion.easyfoodapp.ui.components.label
import app.luxion.easyfoodapp.ui.navigation.Navigator
import app.luxion.easyfoodapp.ui.navigation.Route
import app.luxion.easyfoodapp.ui.theme.PillShape
import app.luxion.easyfoodapp.ui.theme.Spacing
import app.luxion.easyfoodapp.ui.theme.easyFoodColors
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
    Button(onClick = onClick, enabled = enabled && !running, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
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
            "Con lo que te queda hoy y tus favoritos.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = preferences,
            onValueChange = { preferences = it.take(AiAdviceLimits.MAX_PREFERENCES) },
            label = { Text("Preferencias (opcional): sin gluten, rápido…") },
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
        AiActionButton("Sugerir comida", state.suggestions == AiLoad.Loading, enabled) { scope.launch { state.suggest(preferences) } }
        when (val s = state.suggestions) {
            AiLoad.Idle, AiLoad.Loading -> Unit
            is AiLoad.Failed -> AiFailure(s) { scope.launch { state.suggest(preferences) } }
            is AiLoad.Done -> {
                s.value.forEachIndexed { i, option ->
                    SuggestionRow(option, state.links[i], state.added[i], onAdd = { scope.launch { state.addToDiary(i) } },
                        onUndo = { scope.launch { state.undoAdd(i) } },
                        onEdit = { id -> navigator.push(Route.QuickEntry(entryId = id)) })
                }
                state.addError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                Text(
                    "Lo enlazado con tu base lleva sus valores reales y se apunta como alimento; lo «ESTIMADO» son cifras aproximadas y se apunta como entrada rápida.",
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
    link: SuggestionLink?,
    added: AddedSuggestion?,
    onAdd: () -> Unit,
    onUndo: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(option.diaryName, style = MaterialTheme.typography.titleMedium)
                    // Enlazada con la base: cifras reales de «link» (sin «~»); si no, las aproximadas de la IA.
                    Text(
                        if (link != null) "${link.kcal.int()} kcal · P ${link.proteinG.int()} · H ${link.carbsG.int()} · G ${link.fatG.int()}"
                        else "~${option.kcal.int()} kcal · P ${option.proteinG.int()} · H ${option.carbsG.int()} · G ${option.fatG.int()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (link != null) "${link.origin} · ${link.pick.result.name}" else "ESTIMADO por la IA",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (link != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                }
                if (added == null) {
                    Button(
                        onClick = onAdd,
                        modifier = Modifier.heightIn(min = 44.dp).semantics { contentDescription = "Añadir ${option.diaryName} al diario" },
                    ) { Text("Añadir") }
                }
            }
            if (option.reason.isNotBlank()) {
                Text(option.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (added != null) {
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
    val colors = MaterialTheme.easyFoodColors
    val (badge, container, content) = when (point.status) {
        "alto" -> Triple("Alto", colors.warningContainer, colors.onWarningContainer)
        "bajo" -> Triple("Bajo", colors.warningContainer, colors.onWarningContainer)
        else -> Triple("OK", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.Top) {
        Surface(color = container, shape = PillShape) {
            Text(
                badge,
                style = MaterialTheme.typography.labelMedium,
                color = content,
                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            )
        }
        Column(Modifier.weight(1f)) {
            val topic = point.topic.replaceFirstChar { it.uppercase() }
            if (topic.isNotEmpty()) Text(topic, style = MaterialTheme.typography.labelLarge)
            Text(point.advice, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** «Menú y compra»: menú de 3 o 7 días ajustado a tus objetivos y la lista de la compra para prepararlo. Solo se muestra; no toca el diario. */
@Composable
fun AiPlanCard(state: AiTabState) {
    val scope = rememberCoroutineScope()
    var preferences by remember { mutableStateOf("") }
    var days by remember { mutableStateOf(3) }
    val enabled = state.advicePrivacyAccepted && !state.limitReached
    val running = state.plan == AiLoad.Loading
    AiCard("Menú y compra") {
        Text(
            "Un menú para varios días ajustado a tus objetivos, con tus recetas y favoritos cuando encajan, y la lista de la compra.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            listOf(3, 7).forEach { n ->
                EasyFoodFilterChip(selected = days == n, onClick = { days = n }, label = { Text("$n días") }, enabled = !running)
            }
        }
        OutlinedTextField(
            value = preferences,
            onValueChange = { preferences = it.take(AiAdviceLimits.MAX_PREFERENCES) },
            label = { Text("Preferencias (opcional): vegetariano, sin lactosa…") },
            modifier = Modifier.fillMaxWidth(),
        )
        AiActionButton("Preparar menú", running, enabled) { scope.launch { state.planMeals(days, preferences) } }
        when (val p = state.plan) {
            AiLoad.Idle, AiLoad.Loading -> Unit
            is AiLoad.Failed -> AiFailure(p) { scope.launch { state.planMeals(days, preferences) } }
            is AiLoad.Done -> {
                p.value.days.forEach { day ->
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(day.day, style = MaterialTheme.typography.titleMedium)
                            day.meals.forEach { m ->
                                Text(
                                    if (m.meal.isBlank()) m.name else "${m.meal.replaceFirstChar { it.uppercase() }}: ${m.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
                if (p.value.shopping.isNotEmpty()) {
                    Text("Lista de la compra", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    p.value.shopping.forEachIndexed { i, item ->
                        // Toda la fila marca el artículo (zona táctil de 48 dp) y un lector de pantalla la anuncia como una casilla con su texto.
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                                value = i in state.bought,
                                role = Role.Checkbox,
                                onValueChange = { state.toggleBought(i) },
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = i in state.bought, onCheckedChange = null)
                            Text(
                                item.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (i in state.bought) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                Text(
                    "Es una propuesta orientativa: no se guarda ni se apunta en el diario. Al cerrar la pestaña se pierde.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { state.clearPlan() }) { Text("Borrar menú") }
            }
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (turn.fromUser) Arrangement.End else Arrangement.Start) {
                Surface(
                    color = if (turn.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(0.88f),
                ) {
                    Text(
                        turn.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (turn.fromUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }
            }
        }
        OutlinedTextField(
            value = question,
            onValueChange = { question = it.take(AiAdviceLimits.MAX_QUESTION) },
            label = { Text("Tu pregunta") },
            shape = MaterialTheme.shapes.small,
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
