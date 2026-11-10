package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.luxion.fitarchiveapp.data.DiaryStore
import app.luxion.fitarchiveapp.model.QuickEntry
import app.luxion.fitarchiveapp.ui.components.NumberField
import app.luxion.fitarchiveapp.ui.components.label
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.navigation.Route
import app.luxion.fitarchiveapp.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Entrada rápida (docs/design.md §2.16): kcal y macros sin alimento, con nombre opcional, para una comida y un día.
 * Con [Route.QuickEntry.entryId] edita la entrada existente. [today] se usa si la ruta no trae fecha.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickEntryScreen(
    navigator: Navigator,
    route: Route.QuickEntry,
    diaryStore: DiaryStore,
    today: () -> String,
    mealChoices: MealChoices,
) {
    val scope = rememberCoroutineScope()
    val editing = route.entryId != null
    var form by remember { mutableStateOf(QuickEntryFormState()) }
    var meal by remember { mutableStateOf(route.meal ?: mealChoices.defaultMeal()) }
    var date by remember { mutableStateOf(route.date ?: today()) }
    var loaded by remember { mutableStateOf(!editing) }
    var showErrors by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(route) {
        val id = route.entryId ?: return@LaunchedEffect
        try {
            val entry = diaryStore.find(id)
            if (entry == null || !entry.quick) {
                navigator.pop()
            } else {
                form = quickEntryFormStateOf(entry.name, entry.nutrients)
                meal = entry.meal
                date = entry.date
                loaded = true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "No se pudo cargar la entrada."
            loaded = true
        }
    }

    val errors = validateQuickEntry(form)
    // «Falta algún valor» solo se enseña tras intentar guardar; los números mal escritos, al momento.
    val visible = if (showErrors) errors else errors.copy(empty = false)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (editing) "Editar entrada rápida" else "Entrada rápida") },
                navigationIcon = {
                    IconButton(
                        onClick = { navigator.pop() },
                        modifier = Modifier.semantics { contentDescription = "Atrás" },
                    ) { Text("←", style = MaterialTheme.typography.titleLarge) }
                },
            )
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(
                "Para cuando comes fuera: apunta solo las kcal y los macros que sepas, sin crear un alimento. " +
                    "Se guarda en ${meal.label} · ${IsoDate.long(date)}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                mealChoices.options(meal).forEach { m ->
                    FilterChip(selected = meal == m, onClick = { meal = m }, label = { Text(m.label) })
                }
            }
            OutlinedTextField(
                value = form.name,
                onValueChange = { form = form.copy(name = it.take(MAX_NAME)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nombre (opcional)") },
                placeholder = { Text(QuickEntry.DEFAULT_NAME) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraSmall,
            )
            NumberField(
                form.kcal, { form = form.copy(kcal = it) }, "Energía", Modifier.fillMaxWidth(), suffix = "kcal",
                isError = visible.kcal != null || visible.empty, supportingText = visible.kcal,
            )
            NumberField(
                form.protein, { form = form.copy(protein = it) }, "Proteínas (opcional)", Modifier.fillMaxWidth(), suffix = "g",
                isError = visible.protein != null || visible.empty, supportingText = visible.protein,
            )
            NumberField(
                form.carbs, { form = form.copy(carbs = it) }, "Hidratos (opcional)", Modifier.fillMaxWidth(), suffix = "g",
                isError = visible.carbs != null || visible.empty, supportingText = visible.carbs,
            )
            NumberField(
                form.fat, { form = form.copy(fat = it) }, "Grasa (opcional)", Modifier.fillMaxWidth(), suffix = "g",
                isError = visible.fat != null || visible.empty, supportingText = visible.fat,
            )
            if (visible.empty) {
                Text(
                    "Indica al menos las kcal o algún macro.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    val totals = form.toTotals()
                    if (totals == null) {
                        showErrors = true
                        return@Button
                    }
                    saving = true
                    error = null
                    scope.launch {
                        try {
                            if (editing) {
                                check(diaryStore.updateQuick(route.entryId!!, meal, form.resolvedName(), totals)) {
                                    "La entrada ya no existe"
                                }
                            } else {
                                diaryStore.add(
                                    QuickEntry.new(date, meal, form.name, totals.kcal, totals.proteinG, totals.carbsG, totals.fatG),
                                    createdAtMillis = kotlin.time.Clock.System.now().toEpochMilliseconds(),
                                )
                            }
                            navigator.popToDiary()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = "No se pudo guardar. Inténtalo de nuevo."
                            saving = false
                        }
                    }
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (editing) "Guardar cambios" else "Añadir al diario") }
        }
    }
}

private const val MAX_NAME = 80
