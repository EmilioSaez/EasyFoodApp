package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.domain.DayStatus
import app.luxion.fitarchiveapp.model.epochDayToIsoDate
import app.luxion.fitarchiveapp.model.isoDateToEpochDay
import app.luxion.fitarchiveapp.ui.components.formatKcal
import app.luxion.fitarchiveapp.ui.theme.Spacing
import kotlin.math.abs

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Fila anclada del Diario (docs/design.md §2.12): «Hoy ▾» (abre el selector de fecha), día anterior/siguiente,
 * racha (llama y nº de días) y acceso a Datos.
 */
@Composable
fun DiaryTopRow(
    state: DiaryUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPickDate: () -> Unit,
    onData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, modifier = Modifier.semantics { contentDescription = "Día anterior" }) {
            Text("‹", style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier.weight(1f).clickable(onClickLabel = "Elegir fecha", onClick = onPickDate).padding(vertical = Spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                (if (state.date == state.today) "Hoy" else IsoDate.long(state.date).substringBeforeLast(" de ")) + " ▾",
                style = MaterialTheme.typography.titleLarge,
            )
            if (state.date == state.today) {
                Text(
                    IsoDate.long(state.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onNext, modifier = Modifier.semantics { contentDescription = "Día siguiente" }) {
            Text("›", style = MaterialTheme.typography.titleLarge)
        }
        Streak(state.streak)
        IconButton(onClick = onData, modifier = Modifier.semantics { contentDescription = "Datos: exportar e importar" }) {
            Text("⇅", style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** Llama y número de días de racha; con racha 0 la llama sale apagada. */
@Composable
private fun Streak(days: Int) {
    Row(
        Modifier.padding(horizontal = Spacing.xs).semantics {
            contentDescription = if (days == 1) "Racha de 1 día" else "Racha de $days días"
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text("🔥", style = MaterialTheme.typography.titleMedium, modifier = Modifier.alpha(if (days > 0) 1f else 0.3f))
        Text(
            days.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = if (days > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun DayStatus.description(): String = when (this) {
    DayStatus.COMPLETED -> "completado"
    DayStatus.MISSED -> "no completado"
    DayStatus.TODAY_PENDING -> "hoy, sin completar"
    DayStatus.FUTURE -> "día futuro"
    DayStatus.LOGGED_NO_GOAL -> "con registro, sin objetivo"
    DayStatus.EMPTY_NO_GOAL -> "sin registro, sin objetivo"
}

/**
 * Franja semanal (lunes a domingo): inicial, número y punto de estado por día (§2.12). Tocar un día lo selecciona;
 * deslizar cambia de semana ([onShiftWeek] recibe -1 o 1).
 */
@Composable
fun WeekStrip(
    week: List<WeekDayUi>,
    onSelect: (String) -> Unit,
    onShiftWeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var drag by remember { mutableFloatStateOf(0f) }
    Row(
        modifier.fillMaxWidth().pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { drag = 0f },
                onDragEnd = {
                    // Deslizar a la izquierda = semana siguiente.
                    if (abs(drag) > 80f) onShiftWeek(if (drag < 0) 1 else -1)
                    drag = 0f
                },
                onDragCancel = { drag = 0f },
            ) { _, amount -> drag += amount }
        },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        week.forEach { day -> WeekDay(day, onClick = { onSelect(day.date) }, Modifier.weight(1f)) }
    }
}

@Composable
private fun WeekDay(day: WeekDayUi, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val statusColor = when (day.status) {
        DayStatus.COMPLETED -> scheme.tertiary
        DayStatus.MISSED -> scheme.error
        DayStatus.TODAY_PENDING -> scheme.primary
        DayStatus.FUTURE -> scheme.outlineVariant
        DayStatus.LOGGED_NO_GOAL, DayStatus.EMPTY_NO_GOAL -> scheme.onSurfaceVariant
    }
    val numberColor = when (day.status) {
        DayStatus.COMPLETED, DayStatus.MISSED, DayStatus.TODAY_PENDING -> statusColor
        DayStatus.FUTURE -> scheme.onSurfaceVariant
        else -> scheme.onSurface
    }
    Column(
        modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "${IsoDate.long(day.date)}, ${day.status.description()}"
                selected = day.selected
            }
            .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(day.initial, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        Box(
            Modifier.size(36.dp).then(if (day.selected) Modifier.background(scheme.primaryContainer, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.dayNumber.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = if (day.selected) scheme.onPrimaryContainer else numberColor,
            )
        }
        StatusDot(day.status, statusColor)
    }
}

@Composable
private fun StatusDot(status: DayStatus, color: Color) {
    val dot = Modifier.size(8.dp)
    when (status) {
        DayStatus.TODAY_PENDING, DayStatus.EMPTY_NO_GOAL -> Box(dot.border(1.5.dp, color, CircleShape)) // anillo, sin relleno
        else -> Box(dot.background(color, CircleShape))
    }
}

/**
 * Tarjeta de objetivo de la cabecera completa (§2.12): «consumidas / objetivo», barra con las marcas del margen
 * de cumplimiento (objetivo ±[DayStatus.MARGIN]) y sus kcal, y los tres macros.
 */
@Composable
fun GoalCard(state: DiaryUiState, onSetGoal: () -> Unit, modifier: Modifier = Modifier) {
    val goal = state.goalKcal
    Card(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text("kcal", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (goal != null) {
                Text(
                    buildAnnotatedString {
                        append(formatKcal(state.consumedKcal))
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(" / ${formatKcal(goal.toDouble())}") }
                    },
                    style = MaterialTheme.typography.displayMedium,
                    maxLines = 1,
                )
                MarginBar(state.consumedKcal, goal)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(formatKcal(state.consumedKcal), style = MaterialTheme.typography.displayMedium, maxLines = 1)
                        Text(
                            "Sin objetivo diario",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = onSetGoal) { Text("Fijar objetivo") }
                }
            }
            MacrosLine(state)
        }
    }
}

/**
 * Barra de progreso con dos marcas: límite inferior y superior del margen de cumplimiento y sus kcal debajo.
 * Verde dentro del margen, ámbar por encima, rojo (primary) por debajo.
 */
@Composable
private fun MarginBar(consumedKcal: Double, goalKcal: Int) {
    val lower = DayStatus.lowerBound(goalKcal)
    val upper = DayStatus.upperBound(goalKcal)
    val scale = maxOf(goalKcal * 1.3, consumedKcal * 1.02)
    val scheme = MaterialTheme.colorScheme
    val fill = when {
        consumedKcal > upper + 1e-6 -> scheme.error
        consumedKcal >= lower - 1e-6 -> scheme.tertiary
        else -> scheme.primary
    }
    val track = scheme.surfaceContainerHighest
    val mark = scheme.onSurface
    val labelWidth = 52.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = maxWidth
        Column {
            Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                val barTop = 3.dp.toPx()
                val barHeight = 8.dp.toPx()
                drawRect(track, Offset(0f, barTop), androidx.compose.ui.geometry.Size(size.width, barHeight))
                val progress = (consumedKcal / scale).toFloat().coerceIn(0f, 1f)
                drawRect(fill, Offset(0f, barTop), androidx.compose.ui.geometry.Size(size.width * progress, barHeight))
                for (value in listOf(lower, upper)) {
                    val x = (value / scale).toFloat() * size.width
                    drawRect(mark, Offset(x - 1.dp.toPx(), 0f), androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height))
                }
            }
            Box(Modifier.fillMaxWidth().height(18.dp)) {
                val lowerX = width * (lower / scale).toFloat()
                val upperX = maxOf(width * (upper / scale).toFloat(), lowerX + labelWidth)
                fun place(center: androidx.compose.ui.unit.Dp) = (center - labelWidth / 2).coerceIn(0.dp, width - labelWidth)
                for ((x, value) in listOf(lowerX to lower, upperX to upper)) {
                    Text(
                        formatKcal(value),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.width(labelWidth).offset(x = place(x)),
                    )
                }
            }
        }
    }
}

/** Selector de fecha del Diario (el «Hoy ▾»). [onPick] recibe la fecha ISO elegida. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryDatePicker(date: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val initial = isoDateToEpochDay(date)?.times(MILLIS_PER_DAY)
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { pickerState.selectedDateMillis?.let { onPick(epochDayToIsoDate(it.floorDiv(MILLIS_PER_DAY))) } },
                enabled = pickerState.selectedDateMillis != null,
            ) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    ) { DatePicker(pickerState) }
}
