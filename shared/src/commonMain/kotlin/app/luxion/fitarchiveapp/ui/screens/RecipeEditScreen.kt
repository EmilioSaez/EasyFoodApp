package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
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
import app.luxion.fitarchiveapp.data.ProductSearchStore
import app.luxion.fitarchiveapp.data.RecipeStore
import app.luxion.fitarchiveapp.domain.RecipeCalculator
import app.luxion.fitarchiveapp.ui.components.EmptyState
import app.luxion.fitarchiveapp.ui.components.EmptyStateAction
import app.luxion.fitarchiveapp.ui.components.EmptyStateTone
import app.luxion.fitarchiveapp.ui.components.NumberField
import app.luxion.fitarchiveapp.ui.components.formatGrams
import app.luxion.fitarchiveapp.ui.components.formatKcal
import app.luxion.fitarchiveapp.ui.components.parseNumber
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.navigation.PlatformBackHandler
import app.luxion.fitarchiveapp.ui.navigation.Route
import app.luxion.fitarchiveapp.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private sealed interface EditLoad {
    data object Loading : EditLoad
    data object NotFound : EditLoad
    data object Failed : EditLoad
    data class Ready(val form: RecipeFormState) : EditLoad
}

/** Cuadro de cantidad: añade [pick] como ingrediente nuevo o, con [index], cambia los gramos de uno existente. */
internal data class GramsDialog(
    val name: String,
    val initial: Double,
    val pick: FoodPick?,
    val index: Int?,
    /** Unidad que se muestra junto al campo: «g» o «porciones». */
    val suffix: String = "g",
)

/**
 * Crear o editar una receta (docs/design.md §2.8). Los ingredientes se buscan con el mismo buscador que
 * «Añadir» ([FoodSearch]) y se les pide la cantidad en gramos; los macros mostrados son la suma de los ingredientes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditScreen(
    navigator: Navigator,
    route: Route.RecipeEdit,
    recipeStore: RecipeStore,
    searchStore: ProductSearchStore?,
    customFoods: CustomFoodStore?,
) {
    var load by remember(route) {
        mutableStateOf<EditLoad>(if (route.recipeId == null) EditLoad.Ready(RecipeFormState()) else EditLoad.Loading)
    }
    var reload by remember(route) { mutableStateOf(0) }

    LaunchedEffect(route, reload) {
        val id = route.recipeId ?: return@LaunchedEffect
        load = EditLoad.Loading
        load = try {
            recipeStore.find(id)?.let { EditLoad.Ready(RecipeFormState.of(it)) } ?: EditLoad.NotFound
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            EditLoad.Failed
        }
    }

    when (val l = load) {
        EditLoad.Loading -> Scaffold(topBar = { EditTopBar("Receta", navigator) }) {}
        EditLoad.NotFound -> Scaffold(topBar = { EditTopBar("Receta", navigator) }) { padding ->
            EmptyState(
                icon = "?",
                title = "Receta no encontrada",
                message = "Puede que se haya borrado.",
                tone = EmptyStateTone.Warning,
                primaryAction = EmptyStateAction("Volver") { navigator.pop() },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
        EditLoad.Failed -> Scaffold(topBar = { EditTopBar("Receta", navigator) }) { padding ->
            EmptyState(
                icon = "!",
                title = "No se pudo leer la receta",
                message = "Ha fallado la lectura de los datos guardados.",
                tone = EmptyStateTone.Error,
                primaryAction = EmptyStateAction("Reintentar") { reload++ },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
        is EditLoad.Ready -> RecipeForm(navigator, route, l.form, recipeStore, searchStore, customFoods)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditTopBar(title: String, navigator: Navigator, onBack: () -> Unit = { navigator.pop() }, actions: @Composable () -> Unit = {}) {
    CenterAlignedTopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Atrás" }) {
                Text("←", style = MaterialTheme.typography.titleLarge)
            }
        },
        actions = { actions() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeForm(
    navigator: Navigator,
    route: Route.RecipeEdit,
    initial: RecipeFormState,
    recipeStore: RecipeStore,
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
    val editing = route.recipeId != null

    // «Atrás» del sistema: dentro del buscador cierra el buscador, no la pantalla.
    PlatformBackHandler(enabled = picking) { picking = false }

    if (picking) {
        Scaffold(topBar = { EditTopBar("Añadir ingrediente", navigator, onBack = { picking = false }) }) { padding ->
            FoodSearch(
                query = query,
                onQueryChange = { query = it },
                searchStore = searchStore,
                customFoods = customFoods,
                onPick = { pick ->
                    dialog = GramsDialog(pick.result.name, defaultIngredientGrams(pick), pick, null)
                },
                modifier = Modifier.padding(padding),
            )
        }
    } else {
        val totals = RecipeCalculator.totals(form.ingredients)
        Scaffold(
            topBar = {
                EditTopBar(if (editing) "Editar receta" else "Nueva receta", navigator) {
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
                            isError = submitted && form.nameError != null,
                            supportingText = if (submitted && form.nameError != null) { { Text(form.nameError!!) } } else null,
                            singleLine = true,
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item("servings") {
                        NumberField(
                            value = form.servingsText,
                            onValueChange = { form = form.copy(servingsText = it.filter { c -> c.isDigit() }) },
                            label = "Raciones (opcional)",
                            isError = form.servingsError != null,
                            supportingText = form.servingsError ?: "Para ver los macros de cada ración",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item("header") {
                        Text(
                            "Ingredientes · ${form.ingredients.size}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (form.ingredients.isEmpty()) {
                        item("empty") {
                            Text(
                                if (submitted) form.ingredientsError!! else "Aún no hay ingredientes. Busca alimentos de la base de datos y di cuántos gramos lleva cada uno.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (submitted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    itemsIndexed(form.ingredients) { index, ing ->
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(ing.name) },
                            supportingContent = {
                                Text("${formatGrams(ing.grams)} g · ${ing.totals.kcal?.let { "${formatKcal(it)} kcal" } ?: "sin dato"}")
                            },
                            trailingContent = {
                                IconButton(
                                    onClick = { form = form.removeIngredient(index) },
                                    modifier = Modifier.semantics { contentDescription = "Quitar ${ing.name}" },
                                ) { Text("✕") }
                            },
                            modifier = Modifier.clickable { dialog = GramsDialog(ing.name, ing.grams, null, index) },
                        )
                    }
                    item("add") {
                        OutlinedButton(onClick = { query = ""; picking = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("+ Añadir ingrediente")
                        }
                    }
                    if (form.ingredients.isNotEmpty()) {
                        item("total") {
                            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
                                Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    Text("Suma de los ingredientes", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        totals.summaryOrNull() ?: "Faltan datos nutricionales en algún ingrediente",
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
                                val recipe = form.toNewRecipe() ?: return@Button
                                saving = true
                                error = null
                                scope.launch {
                                    try {
                                        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
                                        val id = route.recipeId
                                        if (id == null) {
                                            recipeStore.create(recipe, now)
                                        } else if (!recipeStore.update(id, recipe, now)) {
                                            error = "La receta ya no existe."
                                            saving = false
                                            return@launch
                                        }
                                        navigator.pop()
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        error = "No se pudo guardar la receta. Inténtalo de nuevo."
                                        saving = false
                                    }
                                }
                            },
                        ) { Text(if (editing) "Guardar cambios" else "Guardar receta") }
                    }
                }
            }
        }
    }

    dialog?.let { d -> GramsDialogContent(d, onDismiss = { dialog = null }) { grams ->
        form = if (d.index != null) form.withGrams(d.index, grams) else form.addIngredient(ingredientOf(d.pick!!, grams))
        if (d.index == null) picking = false
        dialog = null
    } }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Borrar la receta?") },
            text = { Text("Se borra «${form.name.trim()}». Las entradas del diario que ya hayas añadido con ella no cambian.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val id = route.recipeId ?: return@TextButton
                    scope.launch {
                        try {
                            recipeStore.delete(id)
                            // Debajo está el detalle de la receta borrada: se sale de los dos.
                            navigator.pop()
                            navigator.pop()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = "No se pudo borrar la receta."
                        }
                    }
                }) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
internal fun GramsDialogContent(dialog: GramsDialog, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var text by remember(dialog) { mutableStateOf(formatGrams(dialog.initial).replace(".", "")) }
    val grams = parseNumber(text)?.takeIf { it > 0 && it.isFinite() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialog.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                NumberField(
                    value = text,
                    onValueChange = { text = it },
                    label = "Cantidad",
                    suffix = dialog.suffix,
                    isError = grams == null,
                    supportingText = if (grams == null) "Escribe un número mayor que 0" else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = grams != null, onClick = { onConfirm(grams!!) }) {
                Text(if (dialog.index == null) "Añadir" else "Guardar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
