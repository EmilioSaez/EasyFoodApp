package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.plate.PlateItem
import app.luxion.fitarchiveapp.plate.PlatePresenter
import app.luxion.fitarchiveapp.plate.PlateSource
import app.luxion.fitarchiveapp.plate.PlateState
import app.luxion.fitarchiveapp.ui.components.NumberField
import app.luxion.fitarchiveapp.ui.components.formatGrams
import app.luxion.fitarchiveapp.ui.components.formatKcal
import app.luxion.fitarchiveapp.ui.components.label
import app.luxion.fitarchiveapp.ui.components.parseNumber
import app.luxion.fitarchiveapp.ui.theme.Spacing
import app.luxion.fitarchiveapp.ai.AiModels
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private sealed interface PlateDialog {
    data class Change(val item: PlateItem) : PlateDialog
    data object Add : PlateDialog
    data object Manual : PlateDialog
}

/** Pantalla de revisión editable de «Foto del plato»: nada se guarda hasta «Añadir al diario». */
@Composable
internal fun PlateReviewContent(
    state: PlateState.Review,
    presenter: PlatePresenter,
    meal: MealType,
    onMeal: (MealType) -> Unit,
    mealChoices: MealChoices,
    date: String,
    modelId: String,
    onConfirm: () -> Unit,
) {
    var dialog by remember { mutableStateOf<PlateDialog?>(null) }
    val review = state.review
    val totals = review.totals
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            item {
                Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text("Revisa lo que ha visto la IA", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Las cantidades son estimaciones a ojo y pueden fallar un 20–40 %. Corrígelas con lo que sepas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.note?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val modelLabel = AiModels.find(modelId)?.label ?: modelId
                    Text(
                        "Modelo: $modelLabel. Para más precisión puedes elegir otro en Ajustes de IA.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (review.items.isEmpty()) {
                item {
                    Text(
                        "No queda ningún alimento. Añade alguno o vuelve atrás.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(Spacing.l),
                    )
                }
            }
            items(review.items, key = { it.id }) { item ->
                PlateItemCard(
                    item = item,
                    onGrams = { presenter.setGrams(item.id, it) },
                    onChange = { dialog = PlateDialog.Change(item) },
                    onRemove = { presenter.remove(item.id) },
                    modifier = Modifier.padding(horizontal = Spacing.l),
                )
            }
            item {
                Row(Modifier.padding(horizontal = Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    OutlinedButton(onClick = { dialog = PlateDialog.Add }) { Text("Añadir alimento") }
                    OutlinedButton(onClick = { dialog = PlateDialog.Manual }) { Text("Entrada rápida") }
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(
                    "Total: ${formatKcal(totals.kcal)} kcal · P ${formatGrams(totals.proteinG)} g · H ${formatGrams(totals.carbsG)} g · G ${formatGrams(totals.fatG)} g",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (review.estimatedCount > 0) {
                    Text(
                        "${review.estimatedCount} alimento(s) sin coincidencia en tu base: sus valores son estimados por la IA.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (totals.missingKcal > 0) {
                    Text(
                        "${totals.missingKcal} alimento(s) sin kcal conocidas no suman al total.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                MealChips(meal, mealChoices, onMeal)
                Button(
                    onClick = onConfirm,
                    enabled = review.items.isNotEmpty() && !state.saving,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Añadir al diario · ${meal.label} · ${IsoDate.long(date)}") }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        is PlateDialog.Change -> FoodSearchDialog(
            title = "Cambiar «${d.item.name}»",
            initial = d.item.alternatives,
            search = presenter::search,
            onPick = { presenter.link(d.item.id, it); dialog = null },
            onDismiss = { dialog = null },
        )
        PlateDialog.Add -> FoodSearchDialog(
            title = "Añadir alimento",
            initial = emptyList(),
            search = presenter::search,
            onPick = { presenter.addLinked(it, it.servingSizeG ?: 100.0); dialog = null },
            onDismiss = { dialog = null },
        )
        PlateDialog.Manual -> ManualEntryDialog(
            onConfirm = { name, totalsValue -> presenter.addManual(name, totalsValue); dialog = null },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun PlateItemCard(
    item: PlateItem,
    onGrams: (Double) -> Unit,
    onChange: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val manual = item.source is PlateSource.Manual
    var gramsText by remember(item.id) { mutableStateOf(formatGrams(item.grams)) }
    val t = item.totals
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium)
            val origin = when (val s = item.source) {
                is PlateSource.Linked -> "De tu base · ${s.pick.result.source.label}" + if (item.looseMatch) " · coincidencia aproximada, revísala" else ""
                is PlateSource.Estimated -> "ESTIMADO por la IA (sin coincidencia en tu base)"
                is PlateSource.Manual -> "Entrada rápida"
            }
            Text(
                origin,
                style = MaterialTheme.typography.labelMedium,
                color = if (item.isEstimated || item.looseMatch) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val detail = listOfNotNull(
                item.portion,
                item.confidence?.let { "confianza ${it.label}" },
            ).joinToString(" · ")
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                (t.kcal?.let { "${formatKcal(it)} kcal" } ?: "Sin kcal") +
                    (if (t.kcal != null && item.isEstimated) " (estimado)" else "") +
                    t.proteinG?.let { " · P ${formatGrams(it)} g" }.orEmpty() +
                    t.carbsG?.let { " · H ${formatGrams(it)} g" }.orEmpty() +
                    t.fatG?.let { " · G ${formatGrams(it)} g" }.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                if (!manual) {
                    NumberField(
                        gramsText,
                        { text ->
                            gramsText = text
                            parseNumber(text)?.takeIf { it > 0 && it.isFinite() && it <= 5000 }?.let(onGrams)
                        },
                        "Cantidad",
                        Modifier.weight(1f),
                        suffix = "g",
                        isError = parseNumber(gramsText)?.let { it <= 0 || it > 5000 } ?: true,
                    )
                }
                if (!manual) TextButton(onClick = onChange) { Text("Cambiar") }
                TextButton(onClick = onRemove) { Text("Quitar") }
            }
        }
    }
}

/** Busca en la base (propios, genéricos y productos consultados) y devuelve el elegido. */
@Composable
private fun FoodSearchDialog(
    title: String,
    initial: List<FoodPick>,
    search: suspend (String) -> List<FoodPick>,
    onPick: (FoodPick) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(initial) }
    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = initial
            return@LaunchedEffect
        }
        delay(300)
        results = try {
            search(query)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Buscar alimento") },
                    singleLine = true,
                )
                if (results.isEmpty()) {
                    Text(
                        if (query.isBlank()) "Escribe para buscar en tu base." else "Sin resultados.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                results.forEach { p ->
                    TextButton(onClick = { onPick(p) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(p.result.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${p.result.source.label} · " + (p.result.kcalPer100g?.let { "${formatKcal(it)} kcal/100 g" } ?: "sin kcal"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Entrada rápida escrita a mano (nombre opcional, kcal y macros), como la de «Entrada rápida». */
@Composable
private fun ManualEntryDialog(onConfirm: (String, app.luxion.fitarchiveapp.model.Nutrients) -> Unit, onDismiss: () -> Unit) {
    var form by remember { mutableStateOf(QuickEntryFormState()) }
    var showErrors by remember { mutableStateOf(false) }
    val errors = validateQuickEntry(form)
    val visible = if (showErrors) errors else errors.copy(empty = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Entrada rápida") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { form = form.copy(name = it.take(80)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nombre (opcional)") },
                    singleLine = true,
                )
                NumberField(form.kcal, { form = form.copy(kcal = it) }, "Energía", Modifier.fillMaxWidth(), suffix = "kcal", isError = visible.kcal != null || visible.empty, supportingText = visible.kcal)
                NumberField(form.protein, { form = form.copy(protein = it) }, "Proteínas (opcional)", Modifier.fillMaxWidth(), suffix = "g", isError = visible.protein != null, supportingText = visible.protein)
                NumberField(form.carbs, { form = form.copy(carbs = it) }, "Hidratos (opcional)", Modifier.fillMaxWidth(), suffix = "g", isError = visible.carbs != null, supportingText = visible.carbs)
                NumberField(form.fat, { form = form.copy(fat = it) }, "Grasa (opcional)", Modifier.fillMaxWidth(), suffix = "g", isError = visible.fat != null, supportingText = visible.fat)
                if (visible.empty) Text("Indica al menos las kcal o algún macro.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val totals = form.toTotals()
                if (totals == null) showErrors = true else onConfirm(form.resolvedName(), totals)
            }) { Text("Añadir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
