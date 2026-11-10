package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.luxion.fitarchiveapp.model.MealTemplate
import app.luxion.fitarchiveapp.ui.components.EmptyState
import app.luxion.fitarchiveapp.ui.components.EmptyStateAction
import app.luxion.fitarchiveapp.ui.theme.Spacing

/**
 * «Plantillas» de la pestaña Alimentos: tus comidas de siempre. «Añadir» las apunta de un toque en la comida y el día por
 * defecto; tocar la fila abre el diálogo para elegir otra comida o fecha; ✎ las edita (renombrar, cambiar cantidades, borrar).
 * [templates] `null` = aún cargando.
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
        else -> LazyColumn(Modifier.fillMaxSize()) {
            item("header") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.l),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "PLANTILLAS · ${templates.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onNew) { Text("Nueva") }
                }
            }
            error?.let {
                item("error") {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = Spacing.l),
                    )
                }
            }
            items(templates, key = { it.id }) { t ->
                ListItem(
                    modifier = Modifier.clickable { onChoose(t) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(t.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text("${t.listDetail()}\n${t.itemsPreview()}", maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            OutlinedButton(
                                onClick = { onAdd(t) },
                                modifier = Modifier.semantics { contentDescription = "Añadir ${t.name} al diario" },
                            ) { Text("Añadir") }
                            IconButton(
                                onClick = { onEdit(t) },
                                modifier = Modifier.semantics { contentDescription = "Editar ${t.name}" },
                            ) { Text("✎", style = MaterialTheme.typography.titleLarge) }
                        }
                    },
                )
            }
        }
    }
}
