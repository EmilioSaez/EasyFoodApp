package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.data.SettingsStore
import app.luxion.fitarchiveapp.domain.MealPlan
import app.luxion.fitarchiveapp.domain.ReminderSettings
import app.luxion.fitarchiveapp.reminders.ReminderScheduler
import app.luxion.fitarchiveapp.scanner.rememberAppSettingsOpener
import app.luxion.fitarchiveapp.ui.components.label
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val DAY_LABELS = listOf("L", "M", "X", "J", "V", "S", "D")
private val DAY_NAMES = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
private const val STEP_MINUTES = 15

/** «08:30» para 510 minutos desde medianoche. */
fun formatMinutes(minutes: Int): String {
    val m = minutes.coerceIn(0, ReminderSettings.MAX_MINUTE)
    return "${(m / 60).toString().padStart(2, '0')}:${(m % 60).toString().padStart(2, '0')}"
}

/** Mueve [minutes] en pasos de 15 min dando la vuelta a medianoche. */
fun stepMinutes(minutes: Int, direction: Int): Int {
    val day = 24 * 60
    return ((minutes + direction * STEP_MINUTES) % day + day) % day
}

/**
 * Recordatorios locales: interruptor general (pide el permiso de notificaciones al activarlo), comidas con hora y días,
 * pesaje semanal y agua opcional. Cada cambio se guarda al momento y [onChanged] hace que se reprograme lo del sistema.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    navigator: Navigator,
    settingsStore: SettingsStore,
    scheduler: ReminderScheduler,
    mealsPerDay: Int?,
    onChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf<ReminderSettings?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val openAppSettings = rememberAppSettingsOpener()

    LaunchedEffect(settingsStore) {
        settings = try {
            settingsStore.reminders()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: ReminderSettings()
    }

    fun update(next: ReminderSettings) {
        settings = next
        error = null
        scope.launch {
            try {
                settingsStore.setReminders(next)
                onChanged()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "No se pudo guardar. Inténtalo de nuevo."
            }
        }
    }

    fun setEnabled(current: ReminderSettings, on: Boolean) {
        if (!on) {
            permissionDenied = false
            update(current.copy(enabled = false))
            return
        }
        scope.launch {
            val granted = try {
                scheduler.requestPermission()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            permissionDenied = !granted
            if (granted) update(current.copy(enabled = true))
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Recordatorios") },
                navigationIcon = {
                    IconButton(
                        onClick = { navigator.pop() },
                        modifier = Modifier.semantics { contentDescription = "Volver" },
                    ) { Text("‹", style = MaterialTheme.typography.titleLarge) }
                },
            )
        },
    ) { padding ->
        val current = settings
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = Spacing.l, end = Spacing.l, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            if (current == null) return@Column
            SwitchRow(
                title = "Activar recordatorios",
                subtitle = "Avisos del sistema para registrar comidas, pesarte y beber agua. No avisan de lo que ya has registrado.",
                checked = current.enabled && scheduler.supported,
                enabled = scheduler.supported,
                onChange = { setEnabled(current, it) },
            )
            if (!scheduler.supported) {
                Note("Este dispositivo no puede mostrar notificaciones.")
            } else {
                scheduler.limitation?.let { Note(it) }
            }
            if (permissionDenied) {
                Note("Las notificaciones están desactivadas para EasyFood. Actívalas en los ajustes del sistema.")
                openAppSettings?.let { OutlinedButton(onClick = it) { Text("Abrir ajustes") } }
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }

            if (current.enabled && scheduler.supported) {
                HorizontalDivider()
                SwitchRow(
                    title = "Comidas",
                    subtitle = "Un aviso por cada comida de tu día, a su hora.",
                    checked = current.meals,
                    onChange = { update(current.copy(meals = it)) },
                )
                if (current.meals) {
                    MealPlan.preset(mealsPerDay).forEach { meal ->
                        TimeRow(
                            label = meal.label,
                            minutes = current.mealMinutes(meal),
                            onChange = { update(current.copy(mealTimes = current.mealTimes + (meal to it))) },
                        )
                    }
                    Text("Días", style = MaterialTheme.typography.labelLarge)
                    DayChips(
                        selected = current.mealDays,
                        onToggle = { day ->
                            val days = if (day in current.mealDays) current.mealDays - day else current.mealDays + day
                            update(current.copy(mealDays = days))
                        },
                    )
                    if (current.mealDays.isEmpty()) Note("Sin ningún día elegido no se avisará de las comidas.")
                }
                HorizontalDivider()
                SwitchRow(
                    title = "Pesarme",
                    subtitle = "Un aviso a la semana, salvo que ya te hayas pesado esa semana.",
                    checked = current.weight,
                    onChange = { update(current.copy(weight = it)) },
                )
                if (current.weight) {
                    DayChips(
                        selected = setOf(current.weightDay),
                        onToggle = { update(current.copy(weightDay = it)) },
                    )
                    TimeRow(
                        label = "Hora (${DAY_NAMES[current.weightDay - 1]})",
                        minutes = current.weightMinutes,
                        onChange = { update(current.copy(weightMinutes = it)) },
                    )
                }
                HorizontalDivider()
                SwitchRow(
                    title = "Agua",
                    subtitle = "Un aviso diario, salvo que ya hayas llegado a tu objetivo de agua.",
                    checked = current.water,
                    onChange = { update(current.copy(water = it)) },
                )
                if (current.water) {
                    TimeRow(
                        label = "Hora",
                        minutes = current.waterMinutes,
                        onChange = { update(current.copy(waterMinutes = it)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = Spacing.m)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}

/** Hora editable con botones − / + de 15 minutos. */
@Composable
private fun TimeRow(label: String, minutes: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(
            onClick = { onChange(stepMinutes(minutes, -1)) },
            modifier = Modifier.semantics { contentDescription = "Adelantar 15 minutos: $label" },
        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text(formatMinutes(minutes), style = MaterialTheme.typography.titleMedium)
        IconButton(
            onClick = { onChange(stepMinutes(minutes, 1)) },
            modifier = Modifier.semantics { contentDescription = "Retrasar 15 minutos: $label" },
        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun DayChips(selected: Set<Int>, onToggle: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        DAY_LABELS.forEachIndexed { i, label ->
            val day = i + 1
            FilterChip(
                selected = day in selected,
                onClick = { onToggle(day) },
                label = { Text(label) },
                modifier = Modifier.weight(1f).semantics { contentDescription = DAY_NAMES[i] },
            )
        }
    }
}
