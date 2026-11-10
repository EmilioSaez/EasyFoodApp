package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.domain.StepsActivity
import app.luxion.fitarchiveapp.steps.DailySteps
import app.luxion.fitarchiveapp.steps.StepsAccess
import app.luxion.fitarchiveapp.steps.StepsProvider
import app.luxion.fitarchiveapp.ui.components.formatKcal
import app.luxion.fitarchiveapp.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Pasos leídos del sensor para la pantalla Objetivos. */
@Stable
class StepsState(private val provider: StepsProvider, private val today: () -> String) {
    var access by mutableStateOf<StepsAccess?>(null)
        private set
    var days by mutableStateOf<List<DailySteps>>(emptyList())
        private set
    var failed by mutableStateOf(false)
        private set

    val todaySteps: Long? get() = days.firstOrNull { it.date == today() }?.steps

    suspend fun refresh() {
        failed = false
        try {
            access = provider.access()
            days = if (access == StepsAccess.GRANTED) {
                val from = StepsActivity.windowStart(today())
                if (from == null) emptyList() else provider.dailySteps(from, today())
            } else emptyList()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
        }
    }

    suspend fun request() {
        try {
            access = provider.requestAccess()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
            return
        }
        refresh()
    }
}

@Composable
fun rememberStepsState(provider: StepsProvider, today: () -> String): StepsState {
    val state = remember(provider) { StepsState(provider, today) }
    LaunchedEffect(state) { state.refresh() }
    return state
}

/** Pasos del día, estado del permiso y la media (pasos, días) con la que se calcula el gasto. */
@Composable
fun StepsSection(
    state: StepsState,
    average: Pair<Int, Int>?,
    modifier: Modifier = Modifier,
) {
    // En escritorio (y sin Health Connect) no hay nada que mostrar.
    if (state.access == null || state.access == StepsAccess.UNAVAILABLE) return
    val scope = rememberCoroutineScope()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text("Pasos diarios", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Card(
            Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                if (state.access == StepsAccess.NEEDS_PERMISSION) {
                    Text(
                        "Permite leer tus pasos para reevaluar tu gasto. Solo se leen en el dispositivo y no se guardan.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = { scope.launch { state.request() } },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("Permitir acceso a pasos") }
                } else {
                    Text(
                        state.todaySteps?.let { "Hoy: ${formatKcal(it.toDouble())} pasos" } ?: "Hoy: sin datos",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        average?.let { (steps, days) ->
                            "Tu gasto se calcula con la media de ${formatKcal(steps.toDouble())} pasos al día ($days días). " +
                                "Los pasos no recogen ciclismo, pesas ni natación; es una estimación."
                        } ?: "Con pasos de al menos ${StepsActivity.MIN_DAYS} de los últimos ${StepsActivity.WINDOW_DAYS} días " +
                            "el gasto se ajusta a tu actividad real; mientras, se usa un gasto base sedentario.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.failed) {
                    Text(
                        "No se pudieron leer los pasos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
