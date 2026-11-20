package app.luxion.easyfoodapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.luxion.easyfoodapp.ui.theme.Spacing

/**
 * Tarjeta de una comida (Desayuno, Comida…): título (titleLarge), resumen debajo, menú «⋯» (copiar y guardar como
 * plantilla, si hay [onCopy] / [onSaveTemplate]) y el «+» redondo de 44 dp en la cabecera (docs/design.md §2.14 y §2.26).
 * [entries] recibe las filas ([DiaryEntryRow]); si [isEmpty], se muestra [emptyText] en su lugar.
 */
@Composable
fun MealSection(
    title: String,
    summary: String,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    onCopy: (() -> Unit)? = null,
    onSaveTemplate: (() -> Unit)? = null,
    /** «Mismo que ayer»: copia esta comida del día anterior; `null` = la opción no aparece. */
    onRepeat: (() -> Unit)? = null,
    isEmpty: Boolean = false,
    emptyText: String = "Nada apuntado todavía",
    entries: @Composable ColumnScope.() -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    OutlinedCard(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.l, end = Spacing.m, top = Spacing.m, bottom = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onCopy != null || onSaveTemplate != null || onRepeat != null) {
                Box {
                    RoundButton(
                        label = "⋯",
                        description = "Más acciones de $title",
                        container = MaterialTheme.colorScheme.surfaceContainerHigh,
                        content = MaterialTheme.colorScheme.onSurface,
                        onClick = { menuOpen = true },
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (onRepeat != null) {
                            DropdownMenuItem(text = { Text("Mismo que ayer") }, onClick = { menuOpen = false; onRepeat() })
                        }
                        if (onCopy != null) {
                            DropdownMenuItem(text = { Text("Copiar") }, onClick = { menuOpen = false; onCopy() })
                        }
                        if (onSaveTemplate != null) {
                            DropdownMenuItem(text = { Text("Guardar como plantilla") }, onClick = { menuOpen = false; onSaveTemplate() })
                        }
                    }
                }
            }
            RoundButton(
                label = "+",
                description = "Añadir a $title",
                container = MaterialTheme.colorScheme.primary,
                content = MaterialTheme.colorScheme.onPrimary,
                onClick = onAdd,
            )
        }
        if (isEmpty) {
            Text(
                emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.l, end = Spacing.l, bottom = Spacing.l),
            )
        } else {
            entries()
        }
    }
}

/** Botón redondo de 44 dp de la cabecera de una comida. */
@Composable
private fun RoundButton(label: String, description: String, container: Color, content: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp)
            .background(container, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(label, style = MaterialTheme.typography.titleLarge, color = content) }
}
