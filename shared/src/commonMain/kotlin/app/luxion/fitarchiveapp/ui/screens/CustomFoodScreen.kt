package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import app.luxion.fitarchiveapp.data.CustomFoodStore
import app.luxion.fitarchiveapp.model.CustomFood
import app.luxion.fitarchiveapp.ui.components.NumberField
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.navigation.Route
import app.luxion.fitarchiveapp.ui.theme.Spacing
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.launch

/**
 * Alimento propio (docs/design.md §2.5): crea, edita (si [foodId] viene informado) y borra un
 * alimento guardado con origen «Propio». Valores por 100 g.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
fun CustomFoodScreen(
    navigator: Navigator,
    route: Route.CustomFood,
    store: CustomFoodStore,
    foodId: Long? = null,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var form by remember { mutableStateOf(CustomFoodFormState()) }
    var existing by remember { mutableStateOf<CustomFood?>(null) }
    var loading by remember { mutableStateOf(foodId != null) }
    var showErrors by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(foodId) {
        if (foodId != null) {
            val food = store.find(foodId)
            if (food == null) {
                snackbar.showSnackbar("El alimento ya no existe.")
                navigator.pop()
            } else {
                existing = food
                form = food.toFormState()
            }
            loading = false
        }
    }

    val errors = validateCustomFood(form, requireAll = showErrors)
    val editing = foodId != null

    fun save() {
        showErrors = true
        val food = form.toNewCustomFood(brand = existing?.brand) ?: return
        saving = true
        scope.launch {
            try {
                val now = Clock.System.now().toEpochMilliseconds()
                val ok = if (foodId == null) {
                    store.create(food, now)
                    true
                } else {
                    store.update(foodId, food, now)
                }
                if (ok) navigator.pop() else snackbar.showSnackbar("El alimento ya no existe.")
            } catch (e: Exception) {
                snackbar.showSnackbar("No se pudo guardar. Inténtalo de nuevo.")
            } finally {
                saving = false
            }
        }
    }

    fun delete() {
        if (foodId == null) return
        saving = true
        scope.launch {
            try {
                store.delete(foodId)
                navigator.pop()
            } catch (e: Exception) {
                snackbar.showSnackbar("No se pudo borrar. Inténtalo de nuevo.")
            } finally {
                saving = false
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (editing) "Editar alimento" else "Alimento propio") },
                navigationIcon = {
                    IconButton(
                        onClick = { navigator.pop() },
                        modifier = Modifier.semantics { contentDescription = "Cerrar" },
                    ) { Text("✕", style = MaterialTheme.typography.titleLarge) }
                },
                actions = {
                    TextButton(onClick = ::save, enabled = !saving && !loading) { Text("Guardar") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            route.barcode?.let { code ->
                OutlinedTextField(
                    value = code,
                    onValueChange = {},
                    label = { Text("Código de barras") },
                    readOnly = true,
                    enabled = false,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = form.name,
                onValueChange = { form = form.copy(name = it) },
                label = { Text("Nombre") },
                isError = errors.name != null,
                supportingText = errors.name?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                OutlinedTextField(
                    value = form.servingLabel,
                    onValueChange = { form = form.copy(servingLabel = it) },
                    label = { Text("Porción") },
                    placeholder = { Text("1 rebanada") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    value = form.servingSize,
                    onValueChange = { form = form.copy(servingSize = it) },
                    label = "Peso porción",
                    suffix = "g",
                    isError = errors.servingSize != null,
                    supportingText = errors.servingSize,
                    modifier = Modifier.weight(1f),
                )
            }

            Text("Valores por 100 g", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            NumberRow(
                "Energía" to Field(form.kcal, "kcal", errors.kcal) { form = form.copy(kcal = it) },
                "Proteína" to Field(form.protein, "g", errors.protein) { form = form.copy(protein = it) },
            )
            NumberRow(
                "Hidratos" to Field(form.carbs, "g", errors.carbs) { form = form.copy(carbs = it) },
                "Grasas" to Field(form.fat, "g", errors.fat) { form = form.copy(fat = it) },
            )
            NumberRow(
                "Saturadas" to Field(form.saturated, "g", errors.saturated) { form = form.copy(saturated = it) },
                "Azúcares" to Field(form.sugars, "g", errors.sugars) { form = form.copy(sugars = it) },
            )
            NumberRow(
                "Fibra" to Field(form.fiber, "g", errors.fiber) { form = form.copy(fiber = it) },
                "Sal" to Field(form.salt, "g", errors.salt) { form = form.copy(salt = it) },
            )

            if (editing) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    enabled = !saving && !loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Borrar alimento", color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Borrar este alimento?") },
            text = { Text("Se eliminará «${form.name}» de tus alimentos propios.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; delete() }) {
                    Text("Borrar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

private class Field(val value: String, val suffix: String, val error: String?, val onChange: (String) -> Unit)

/** Fila de dos campos numéricos (rejilla de 2 columnas). */
@Composable
private fun NumberRow(left: Pair<String, Field>, right: Pair<String, Field>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        listOf(left, right).forEach { (label, f) ->
            NumberField(
                value = f.value,
                onValueChange = f.onChange,
                label = label,
                suffix = f.suffix,
                isError = f.error != null,
                supportingText = f.error,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
