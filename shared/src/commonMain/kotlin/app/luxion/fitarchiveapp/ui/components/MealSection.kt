package app.luxion.fitarchiveapp.ui.components

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.luxion.fitarchiveapp.ui.theme.Spacing

/**
 * Tarjeta de una comida (Desayuno, Comida…): título en Sora 18 con «Copiar» y «Plantilla» (guardar como plantilla) justo a su derecha
 * (si hay [onCopy] / [onSaveTemplate]), resumen debajo
 * y el «+» cuadrado de 36 dp en la cabecera (docs/design.md §2.14). [entries] recibe las filas ([DiaryEntryRow]);
 * si [isEmpty], se muestra [emptyText] en su lugar.
 */
@Composable
fun MealSection(
    title: String,
    summary: String,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    onCopy: (() -> Unit)? = null,
    onSaveTemplate: (() -> Unit)? = null,
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
            Modifier.fillMaxWidth().padding(start = Spacing.l, end = Spacing.xs, top = Spacing.s, bottom = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 24.sp))
                    if (onCopy != null) {
                        TextButton(onClick = onCopy, modifier = Modifier.semantics { contentDescription = "Copiar $title" }) { Text("Copiar") }
                    }
                    if (onSaveTemplate != null) {
                        TextButton(
                            onClick = onSaveTemplate,
                            modifier = Modifier.semantics { contentDescription = "Guardar $title como plantilla" },
                        ) { Text("Plantilla") }
                    }
                }
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // «+» cuadrado primary de 36 dp (zona táctil de 48): añade a esta comida.
            Box(
                Modifier.size(48.dp)
                    .clickable(role = Role.Button, onClick = onAdd)
                    .semantics { contentDescription = "Añadir a $title" },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center,
                ) { Text("+", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary) }
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
    }
}
