package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.luxion.fitarchiveapp.domain.DayStatus
import app.luxion.fitarchiveapp.model.epochDayToIsoDate
import app.luxion.fitarchiveapp.model.isoDateToEpochDay
import app.luxion.fitarchiveapp.domain.DaySummary
import app.luxion.fitarchiveapp.ui.components.StreakIcon
import app.luxion.fitarchiveapp.ui.components.formatKcal
import app.luxion.fitarchiveapp.ui.theme.PillShape
import app.luxion.fitarchiveapp.ui.theme.Spacing
import app.luxion.fitarchiveapp.ui.theme.easyFoodColors
import kotlin.math.abs

/**
 * Cabecera del Diario (docs/design.md §2.12 y §2.26): «Hoy ▾» (abre el selector de fecha) con el día de la semana debajo,
 * día anterior/siguiente, racha en limón y el menú «⋯» (copiar día, Resumen, Datos).
 */
@Composable
fun DiaryTopRow(
    state: DiaryUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPickDate: () -> Unit,
    onSummary: () -> Unit,
    onData: () -> Unit,
    modifier: Modifier = Modifier,
    /** `null` = el día no tiene nada que copiar: la opción no aparece. */
    onCopyDay: (() -> Unit)? = null,
    /** «Mismo que ayer»: copia el día anterior al mostrado; `null` = la opción no aparece. */
    onRepeatDay: (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable(onClickLabel = "Elegir fecha", onClick = onPickDate).padding(vertical = Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (state.date == state.today) "Hoy" else IsoDate.long(state.date).substringBeforeLast(" de ")) + " ▾",
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Streak(state.streak)
            }
            Text(
                IsoDate.weekdayAndDay(state.date),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onPrevious, modifier = Modifier.size(40.dp).semantics { contentDescription = "Día anterior" }) {
            Text("‹", style = MaterialTheme.typography.titleLarge)
        }
        IconButton(onClick = onNext, modifier = Modifier.size(40.dp).semantics { contentDescription = "Día siguiente" }) {
            Text("›", style = MaterialTheme.typography.titleLarge)
        }
        Box {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier.semantics { contentDescription = "Más acciones: copiar día, resumen, datos" },
            ) { Text("⋯", style = MaterialTheme.typography.titleLarge) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (onRepeatDay != null) {
                    DropdownMenuItem(text = { Text("Mismo que ayer") }, onClick = { menuOpen = false; onRepeatDay() })
                }
                if (onCopyDay != null) {
                    DropdownMenuItem(text = { Text("Copiar día") }, onClick = { menuOpen = false; onCopyDay() })
                }
                DropdownMenuItem(text = { Text("Resumen semanal y mensual") }, onClick = { menuOpen = false; onSummary() })
                DropdownMenuItem(text = { Text("Datos: exportar e importar") }, onClick = { menuOpen = false; onData() })
            }
        }
    }
}

/** Píldora limón con la llama propia (StreakIcon) y los días de racha; con racha 0 sale apagada. */
@Composable
private fun Streak(days: Int) {
    val colors = MaterialTheme.easyFoodColors
    Row(
        Modifier.padding(start = Spacing.m)
            .background(if (days > 0) colors.accent else MaterialTheme.colorScheme.surfaceContainerHigh, PillShape)
            .padding(horizontal = Spacing.m, vertical = Spacing.xs)
            .semantics { contentDescription = if (days == 1) "Racha de 1 día" else "Racha de $days días" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        StreakIcon(
            active = days > 0,
            size = 20.dp,
            tint = if (days > 0) colors.onAccent else Color.Unspecified,
            dropColor = if (days > 0) colors.accent else Color.Unspecified,
        )
        Text(
            days.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = if (days > 0) colors.onAccent else MaterialTheme.colorScheme.onSurfaceVariant,
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
 * Franja semanal (lunes a domingo): inicial y número de cada día en un círculo cuyo color indica el estado (verde
 * completado, ámbar no completado; el seleccionado va relleno). Tocar un día lo selecciona; deslizar cambia de semana
 * ([onShiftWeek] recibe -1 o 1).
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
    val (fill, textColor) = when {
        day.selected -> scheme.onSurface to scheme.surface
        day.status == DayStatus.COMPLETED -> scheme.tertiaryContainer to scheme.tertiary
        day.status == DayStatus.MISSED -> scheme.errorContainer to scheme.error
        day.status == DayStatus.LOGGED_NO_GOAL -> scheme.secondaryContainer to scheme.onSurface
        day.status == DayStatus.TODAY_PENDING -> Color.Transparent to scheme.primary
        day.status == DayStatus.FUTURE -> Color.Transparent to scheme.onSurfaceVariant
        else -> Color.Transparent to scheme.onSurface
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
        Text(
            day.initial,
            style = MaterialTheme.typography.labelMedium,
            color = if (day.status == DayStatus.TODAY_PENDING) scheme.primary else scheme.onSurfaceVariant,
        )
        Box(
            Modifier.size(38.dp)
                .background(fill, CircleShape)
                .then(if (day.status == DayStatus.TODAY_PENDING && !day.selected) Modifier.border(1.5.dp, scheme.primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(day.dayNumber.toString(), style = MaterialTheme.typography.titleMedium, color = textColor)
        }
    }
}

/**
 * Tarjeta «hero» de kcal (§2.12, §2.26): «consumidas / objetivo», barra limón con las marcas del margen de cumplimiento
 * (objetivo ±[DayStatus.MARGIN]), «restantes» y la meta, y las páginas deslizables de macros y otros nutrientes.
 */
@Composable
fun GoalCard(state: DiaryUiState, onSetGoal: () -> Unit, modifier: Modifier = Modifier) {
    val goal = state.goalKcal
    val colors = MaterialTheme.easyFoodColors
    Column(
        modifier.fillMaxWidth().background(colors.hero, MaterialTheme.shapes.extraLarge).padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text("CONSUMIDAS", style = MaterialTheme.typography.labelMedium, color = colors.onHeroVariant)
        if (goal != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    buildAnnotatedString {
                        append(formatKcal(state.consumedKcal))
                        withStyle(SpanStyle(color = colors.onHeroVariant, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = MaterialTheme.typography.bodyMedium.fontFamily)) {
                            append(" / ${formatKcal(goal.toDouble())}")
                        }
                    },
                    style = MaterialTheme.typography.displayMedium,
                    color = colors.onHero,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    "kcal",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onHeroVariant,
                    modifier = Modifier.padding(start = Spacing.s).alignByBaseline(),
                )
            }
            MarginBar(state.consumedKcal, goal)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(formatKcal(state.consumedKcal), style = MaterialTheme.typography.displayMedium, color = colors.onHero, maxLines = 1)
                    Text("Sin objetivo diario", style = MaterialTheme.typography.bodyMedium, color = colors.onHeroVariant)
                }
                Button(onClick = onSetGoal, colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)) {
                    Text("Fijar objetivo")
                }
            }
        }
        NutrientPager(state)
    }
}

/**
 * Páginas deslizables bajo las kcal (§2.19): macros y «otros nutrientes» (fibra, sal, azúcares, saturadas). Ambas miden
 * lo mismo, así que la tarjeta no cambia de altura al deslizar; los puntos indican la página.
 */
@Composable
private fun NutrientPager(state: DiaryUiState) {
    val pages = listOf("Macros", "Otros nutrientes · objetivos orientativos (OMS)")
    val pager = rememberPagerState { pages.size }
    val colors = MaterialTheme.easyFoodColors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        HorizontalPager(pager, Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { page ->
            if (page == 0) MacrosLine(state) else IntakeLine(state.intake)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                pages[pager.currentPage],
                style = MaterialTheme.typography.labelSmall,
                color = colors.onHeroVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Row(
                Modifier.semantics { contentDescription = "Página ${pager.currentPage + 1} de ${pages.size}" },
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                repeat(pages.size) { i ->
                    Box(Modifier.size(6.dp).background(if (i == pager.currentPage) colors.accent else colors.heroTrack, CircleShape))
                }
            }
        }
    }
}

/**
 * Barra de progreso limón con dos marcas (límites del margen de cumplimiento), «N kcal restantes» (o «de más») y la meta.
 * La barra pasa a ámbar por encima del margen.
 */
@Composable
private fun MarginBar(consumedKcal: Double, goalKcal: Int) {
    val lower = DayStatus.lowerBound(goalKcal)
    val upper = DayStatus.upperBound(goalKcal)
    val scale = maxOf(goalKcal * 1.3, consumedKcal * 1.02)
    val colors = MaterialTheme.easyFoodColors
    val fill = if (consumedKcal > upper + 1e-6) colors.heroWarning else colors.accent
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Canvas(Modifier.fillMaxWidth().height(20.dp)) {
            val barTop = 4.dp.toPx()
            val barHeight = 12.dp.toPx()
            val round = CornerRadius(barHeight / 2)
            drawRoundRect(colors.heroTrack, Offset(0f, barTop), Size(size.width, barHeight), round)
            val progress = (consumedKcal / scale).toFloat().coerceIn(0f, 1f)
            if (progress > 0f) drawRoundRect(fill, Offset(0f, barTop), Size(maxOf(size.width * progress, barHeight), barHeight), round)
            for (value in listOf(lower, upper)) {
                val x = (value / scale).toFloat() * size.width
                drawRect(colors.onHeroVariant, Offset(x - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val remaining = goalKcal - consumedKcal
            Text(
                buildAnnotatedString {
                    if (remaining >= 0) {
                        withStyle(SpanStyle(color = colors.onHero, fontWeight = FontWeight.Bold)) { append("${formatKcal(remaining)} kcal") }
                        append(" restantes")
                    } else {
                        withStyle(SpanStyle(color = colors.heroWarning, fontWeight = FontWeight.Bold)) { append("${formatKcal(-remaining)} kcal de más") }
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onHeroVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                "meta ${formatKcal(lower)}–${formatKcal(upper)}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onHeroVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * Selector de fecha (el «Hoy ▾» del Diario): calendario mensual con navegación entre meses. Con [summaries] cada día
 * muestra bajo el número el [StreakIcon] (activo si ese día se cumplió el objetivo ±[DayStatus.MARGIN]); sin ellos
 * (p. ej. al elegir el destino de una copia) solo se elige fecha. Tocar un día llama a [onPick] con la fecha ISO.
 */
@Composable
fun DiaryDatePicker(
    date: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    today: String = date,
    goalKcal: Int? = null,
    summaries: Map<String, DaySummary>? = null,
) {
    var month by remember(date) { mutableStateOf(IsoDate.monthStart(date)) }
    val ui = buildMonth(month, date, today, goalKcal, summaries ?: emptyMap())
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = IsoDate.shiftMonth(month, -1) }, modifier = Modifier.semantics { contentDescription = "Mes anterior" }) {
                    Text("‹", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    ui.title.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { month = IsoDate.shiftMonth(month, 1) }, modifier = Modifier.semantics { contentDescription = "Mes siguiente" }) {
                    Text("›", style = MaterialTheme.typography.titleLarge)
                }
            }
        },
        text = {
            Column {
                Row(Modifier.fillMaxWidth()) {
                    listOf("L", "M", "X", "J", "V", "S", "D").forEach {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                val cells: List<MonthDayUi?> = List(ui.leadingBlanks) { null } + ui.days
                cells.chunked(7).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { day -> MonthCell(day, showStreak = summaries != null, onPick = onPick, Modifier.weight(1f)) }
                        repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        },
    )
}

@Composable
private fun MonthCell(day: MonthDayUi?, showStreak: Boolean, onPick: (String) -> Unit, modifier: Modifier) {
    if (day == null) return Spacer(modifier)
    val scheme = MaterialTheme.colorScheme
    val textColor = when {
        day.selected -> scheme.surface
        day.status == DayStatus.TODAY_PENDING -> scheme.primary
        day.status == DayStatus.FUTURE -> scheme.onSurfaceVariant
        else -> scheme.onSurface
    }
    Column(
        modifier
            .clickable(role = Role.Button, onClick = { onPick(day.date) })
            .semantics(mergeDescendants = true) {
                contentDescription = IsoDate.long(day.date) + if (showStreak) ", " + (if (day.streak) "racha cumplida" else "sin racha") else ""
                selected = day.selected
            }
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(32.dp)
                .background(if (day.selected) scheme.onSurface else Color.Transparent, CircleShape)
                .then(if (day.status == DayStatus.TODAY_PENDING && !day.selected) Modifier.border(1.5.dp, scheme.primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) { Text(day.dayNumber.toString(), style = MaterialTheme.typography.bodyMedium, color = textColor) }
        if (showStreak) StreakIcon(active = day.streak, size = 14.dp) else Spacer(Modifier.height(14.dp))
    }
}
