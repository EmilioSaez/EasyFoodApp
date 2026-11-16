package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.luxion.fitarchiveapp.domain.HabitSuggestion
import app.luxion.fitarchiveapp.domain.HabitSuggestions
import app.luxion.fitarchiveapp.ui.components.label
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Accesos rápidos por hábito de la hoja «Añadir» (docs/design.md §2.28): lo que sueles apuntar en la comida elegida,
 * calculado con el historial local. Tocar uno lo apunta con su cantidad habitual en la comida y el día de la hoja y vuelve
 * al Diario. Sin hábitos (o sin diario) no se muestra nada.
 */
@Composable
fun AddSheetSuggestions(model: AddSheetModel, navigator: Navigator) {
    val diary = model.deps.diaryStore ?: return
    val scope = rememberCoroutineScope()
    var suggestions by remember { mutableStateOf<List<HabitSuggestion>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(diary, model.meal, model.date) {
        suggestions = try {
            val history = diary.entriesBetween(IsoDate.plusDays(model.date, -HabitSuggestions.WINDOW_DAYS), model.date)
            HabitSuggestions.suggest(history, model.meal, model.date)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList() // las sugerencias son opcionales: si falla la lectura, no se muestran
        }
    }
    if (suggestions.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("Lo que sueles tomar en ${model.meal.label}", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            suggestions.forEach { s ->
                AssistChip(
                    onClick = {
                        error = null
                        scope.launch {
                            try {
                                diary.add(s.toNewEntry(model.date, model.meal), kotlin.time.Clock.System.now().toEpochMilliseconds())
                                navigator.popToDiary()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                error = "No se pudo añadir. Inténtalo de nuevo."
                            }
                        }
                    },
                    label = { Text("${s.name} · ${s.quantity.format()}", maxLines = 1) },
                )
            }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}
