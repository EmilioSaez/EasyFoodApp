package app.luxion.fitarchiveapp.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.ui.theme.Spacing

/**
 * Tarjeta de una comida (Desayuno, Comida…): título, resumen, entradas y botón ancho «+» y, si hay [onCopy], «Copiar» junto al título. [entries] recibe las filas ([DiaryEntryRow]);
 * si [isEmpty], se muestra [emptyText] en su lugar.
 */
@Composable
fun MealSection(
    title: String,
    summary: String,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    onCopy: (() -> Unit)? = null,
    isEmpty: Boolean = false,
    emptyText: String = "Nada apuntado todavía",
    entries: @Composable ColumnScope.() -> Unit = {},
) {
    OutlinedCard(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.l, end = Spacing.s, top = Spacing.m, bottom = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onCopy != null) {
                TextButton(onClick = onCopy, modifier = Modifier.semantics { contentDescription = "Copiar $title" }) { Text("Copiar") }
            }
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
        // Botón ancho «+»: añade a esta comida (docs/design.md §2.12).
        Button(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth().padding(Spacing.s).semantics { contentDescription = "Añadir a $title" },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            shape = MaterialTheme.shapes.small,
        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}
