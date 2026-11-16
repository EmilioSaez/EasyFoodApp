package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.data.DiaryStore
import app.luxion.fitarchiveapp.data.MealTemplateStore
import app.luxion.fitarchiveapp.data.addTemplate
import app.luxion.fitarchiveapp.model.MealTemplate
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.ui.components.EmptyState
import app.luxion.fitarchiveapp.ui.components.EmptyStateAction
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.navigation.Route
import app.luxion.fitarchiveapp.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Pestaña «Plantillas» de «Guardado» (§2.23): carga las plantillas y las añade al diario («Añadir» de un toque en la
 * comida y el día por defecto; tocar la tarjeta abre [AddTemplateDialog] para elegir otra comida o fecha). Al añadir
 * vuelve al Diario.
 */
@Composable
fun TemplatesTab(
    navigator: Navigator,
    templateStore: MealTemplateStore,
    diaryStore: DiaryStore,
    today: () -> String,
    mealChoices: MealChoices,
) {
    var templates by remember { mutableStateOf<List<MealTemplate>?>(null) }
    var adding by remember { mutableStateOf<MealTemplate?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(templateStore) {
        templates = try {
            templateStore.all()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }
    val add: (MealTemplate, String, MealType) -> Unit = { template, date, meal ->
        scope.launch {
            try {
                diaryStore.addTemplate(template, date, meal, kotlin.time.Clock.System.now().toEpochMilliseconds())
                adding = null
                navigator.popToDiary()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "No se pudo añadir la plantilla. Inténtalo de nuevo."
            }
        }
    }

    TemplateListSection(
        templates = templates,
        onAdd = { t -> error = null; add(t, today(), mealChoices.defaultMeal()) },
        onChoose = { t -> error = null; adding = t },
        onEdit = { t -> navigator.push(Route.TemplateEdit(t.id)) },
        onNew = { navigator.push(Route.TemplateEdit()) },
        error = error.takeIf { adding == null },
    )
    adding?.let { t ->
        AddTemplateDialog(
            template = t,
            today = today(),
            initialMeal = mealChoices.defaultMeal(),
            mealChoices = mealChoices,
            error = error,
            onDismiss = { adding = null; error = null },
            onConfirm = { date, meal -> add(t, date, meal) },
        )
    }
}

/**
 * «Plantillas»: tus comidas de siempre, una tarjeta por plantilla. «Añadir» las apunta de un toque en la comida y el día
 * por defecto; tocar la tarjeta abre el diálogo para elegir otra comida o fecha; ✎ las edita (renombrar, cambiar
 * cantidades, borrar). Se usa en «Guardado» y en la pestaña Alimentos. [templates] `null` = aún cargando.
 */
@Composable
fun TemplateListSection(
    templates: List<MealTemplate>?,
    onAdd: (MealTemplate) -> Unit,
    onChoose: (MealTemplate) -> Unit,
    onEdit: (MealTemplate) -> Unit,
    onNew: () -> Unit,
    error: String? = null,
) {
    when {
        templates == null -> Unit
        templates.isEmpty() -> EmptyState(
            icon = "☰",
            title = "Aún no tienes plantillas",
            message = "Guarda «mi desayuno de siempre» con «Plantilla» en una comida del Diario, o créala aquí.",
            primaryAction = EmptyStateAction("Nueva plantilla", onNew),
            modifier = Modifier.fillMaxWidth(),
        )
        else -> LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.xs, bottom = Spacing.bottomInset),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item("header") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "PLANTILLAS · ${templates.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onNew) { Text("Nueva") }
                }
            }
            error?.let {
                item("error") { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
            items(templates, key = { it.id }) { t -> TemplateCard(t, onAdd, onChoose, onEdit) }
            item("new") {
                FilledTonalButton(onClick = onNew, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Nueva plantilla") }
            }
        }
    }
}

@Composable
private fun TemplateCard(
    t: MealTemplate,
    onAdd: (MealTemplate) -> Unit,
    onChoose: (MealTemplate) -> Unit,
    onEdit: (MealTemplate) -> Unit,
) {
    Surface(
        onClick = { onChoose(t) },
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(t.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.listDetail(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                t.itemsPreview(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Button(
                    onClick = { onAdd(t) },
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp).semantics { contentDescription = "Añadir ${t.name} al diario" },
                ) { Text("Añadir") }
                IconButton(
                    onClick = { onEdit(t) },
                    modifier = Modifier.semantics { contentDescription = "Editar ${t.name}" },
                ) { Text("✎", style = MaterialTheme.typography.titleLarge) }
            }
        }
    }
}
