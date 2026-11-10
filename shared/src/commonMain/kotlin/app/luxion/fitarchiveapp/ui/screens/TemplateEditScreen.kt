package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.luxion.fitarchiveapp.data.CustomFoodStore
import app.luxion.fitarchiveapp.data.MealTemplateStore
import app.luxion.fitarchiveapp.data.ProductSearchStore
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import app.luxion.fitarchiveapp.model.TemplateItem
import app.luxion.fitarchiveapp.ui.components.EmptyState
import app.luxion.fitarchiveapp.ui.components.EmptyStateAction
import app.luxion.fitarchiveapp.ui.components.EmptyStateTone
import app.luxion.fitarchiveapp.ui.components.formatKcal
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.navigation.PlatformBackHandler
import app.luxion.fitarchiveapp.ui.navigation.Route
import app.luxion.fitarchiveapp.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private sealed interface TemplateLoad {
    data object Loading : TemplateLoad
    data object NotFound : TemplateLoad
    data object Failed : TemplateLoad
    data class Ready(val form: TemplateFormState) : TemplateLoad
}

/**
 * Crear o editar una plantilla de comida (docs/design.md §2.17): nombre y alimentos con su cantidad. Los alimentos se
 * buscan con el mismo buscador que «Añadir» y las recetas ([FoodSearch]); tocar uno cambia su cantidad. Editando se
 * puede renombrar y borrar la plantilla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditScreen(
    navigator: Navigator,
    route: Route.TemplateEdit,
    templateStore: MealTemplateStore,
    searchStore: ProductSearchStore?,
    customFoods: CustomFoodStore?,
) {
    var load by remember(route) { mutableStateOf<TemplateLoad>(TemplateLoad.Loading) }
    var reload by remember(route) { mutableStateOf(0) }

    LaunchedEffect(route, reload) {
        load = TemplateLoad.Loading
        load = try {
            val all = templateStore.all()
            val id = route.templateId
            val others = all.filter { it.id != id }.mapTo(mutableSetOf()) { it.name }
            if (id == null) {
                TemplateLoad.Ready(TemplateFormState(otherNames = others))
            } else {
                all.firstOrNull { it.id == id }?.let { TemplateLoad.Ready(TemplateFormState.of(it, others)) } ?: TemplateLoad.NotFound
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TemplateLoad.Failed
        }
    }

    when (val l = load) {
        TemplateLoad.Loading -> Scaffold(topBar = { EditTopBar("Plantilla", navigator) }) {}
        TemplateLoad.NotFound -> Scaffold(topBar = { EditTopBar("Plantilla", navigator) }) { padding ->
            EmptyState(
                icon = "?",
                title = "Plantilla no encontrada",
                message = "Puede que se haya borrado.",
                tone = EmptyStateTone.Warning,
                primaryAction = EmptyStateAction("Volver") { navigator.pop() },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
        TemplateLoad.Failed -> Scaffold(topBar = { EditTopBar("Plantilla", navigator) }) { padding ->
            EmptyState(
                icon = "!",
                title = "No se pudo leer la plantilla",
                message = "Ha fallado la lectura de los datos guardados.",
                tone = EmptyStateTone.Error,
                primaryAction = EmptyStateAction("Reintentar") { reload++ },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
        is TemplateLoad.Ready -> TemplateForm(navigator, route, l.form, templateStore, searchStore, customFoods)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateForm(
    navigator: Navigator,
    route: Route.TemplateEdit,
    initial: TemplateFormState,
    templateStore: MealTemplateStore,
    searchStore: ProductSearchStore?,
    customFoods: CustomFoodStore?,
) {
    val scope = rememberCoroutineScope()
    var form by remember(initial) { mutableStateOf(initial) }
    var picking by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var dialog by remember { mutableStateOf<GramsDialog?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val editing = route.templateId != null

    // «Atrás» del sistema: dentro del buscador cierra el buscador, no la pantalla.
    PlatformBackHandler(enabled = picking) { picking = false }

    if (picking) {
        Scaffold(topBar = { EditTopBar("Añadir alimento", navigator, onBack = { picking = false }) }) { padding ->
            FoodSearch(
                query = query,
                onQueryChange = { query = it },
                searchStore = searchStore,
                customFoods = customFoods,
                onPick = { pick -> dialog = GramsDialog(pick.result.name, defaultIngredientGrams(pick), pick, null) },
                modifier = Modifier.padding(padding),
            )
        }
    } else {
        val totals = templateTotals(form.items)
        Scaffold(
            topBar = {
                EditTopBar(if (editing) "Editar plantilla" else "Nueva plantilla", navigator) {
                    if (editing) {
                        TextButton(onClick = { confirmDelete = true }) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(Spacing.screen),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    item("name") {
                        OutlinedTextField(
                            value = form.name,
                            onValueChange = { form = form.copy(name = it) },
                            label = { Text("Nombre") },
                            isError = (submitted || form.name.isNotEmpty()) && form.nameError != null,
                            supportingText = if ((submitted || form.name.isNotEmpty()) && form.nameError != null) {
                                { Text(form.nameError!!) }
                            } else {
                                null
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item("header") {
                        Text(
                            "Alimentos · ${form.items.size}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (form.items.isEmpty()) {
                        item("empty") {
                            Text(
                                if (submitted) form.itemsError!! else "Aún no hay alimentos. Busca alimentos de la base de datos y di qué cantidad llevas de cada uno.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (submitted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    itemsIndexed(form.items) { index, item ->
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(item.name) },
                            supportingContent = {
                                Text("${item.quantityText()} · ${item.totals.kcal?.let { "${formatKcal(it)} kcal" } ?: "sin dato"}")
                            },
                            trailingContent = {
                                IconButton(
                                    onClick = { form = form.removeItem(index) },
                                    modifier = Modifier.semantics { contentDescription = "Quitar ${item.name}" },
                                ) { Text("✕") }
                            },
                            modifier = Modifier.clickable {
                                dialog = GramsDialog(item.name, item.quantity.amount, null, index, item.unitSuffix())
                            },
                        )
                    }
                    item("add") {
                        OutlinedButton(onClick = { query = ""; picking = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("+ Añadir alimento")
                        }
                    }
                    if (form.items.isNotEmpty()) {
                        item("total") {
                            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
                                Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    Text("Suma de los alimentos", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        totals.summaryOrNull() ?: "Faltan datos nutricionales en algún alimento",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                }
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        Button(
                            enabled = !saving,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                submitted = true
                                val template = form.toNewTemplate() ?: return@Button
                                saving = true
                                error = null
                                scope.launch {
                                    try {
                                        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
                                        val id = route.templateId
                                        if (id == null) {
                                            templateStore.create(template, now)
                                        } else if (!templateStore.update(id, template, now)) {
                                            error = "La plantilla ya no existe."
                                            saving = false
                                            return@launch
                                        }
                                        navigator.pop()
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        error = "No se pudo guardar la plantilla. Inténtalo de nuevo."
                                        saving = false
                                    }
                                }
                            },
                        ) { Text(if (editing) "Guardar cambios" else "Guardar plantilla") }
                    }
                }
            }
        }
    }

    dialog?.let { d ->
        GramsDialogContent(d, onDismiss = { dialog = null }) { amount ->
            form = if (d.index != null) {
                form.withAmount(d.index, amount)
            } else {
                val pick = d.pick!!
                form.addItem(TemplateItem(pick.barcode, pick.result.name, Quantity(amount, QuantityUnit.GRAMS), pick.servingSizeG, pick.nutrients))
            }
            if (d.index == null) picking = false
            dialog = null
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Borrar la plantilla?") },
            text = { Text("Se borra «${form.name.trim()}». Lo que ya hayas añadido al diario con ella no cambia.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val id = route.templateId ?: return@TextButton
                    scope.launch {
                        try {
                            templateStore.delete(id)
                            navigator.pop()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = "No se pudo borrar la plantilla."
                        }
                    }
                }) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}
