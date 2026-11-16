package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.ui.navigation.AddSheetTarget
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.navigation.Route
import app.luxion.fitarchiveapp.ui.theme.Spacing

/**
 * HUECO B2 · acciones de la hoja «Añadir»: Escanear, Foto del plato (IA), Etiqueta (IA) y Entrada rápida.
 * Fase 0: cuatro mosaicos que abren las pantallas existentes. La sesión B los rediseña (iconos, estados de IA sin clave).
 */
@Composable
fun AddSheetActions(target: AddSheetTarget, navigator: Navigator) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ActionTile("Escanear", "Código de barras") { navigator.push(Route.Scanner(target.meal, target.date)) }
            ActionTile("Foto del plato", "IA") { navigator.push(Route.PlatePhoto(target.meal, target.date)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ActionTile("Etiqueta", "IA") { navigator.push(Route.LabelPhoto(meal = target.meal)) }
            ActionTile("Entrada rápida", "Solo kcal y macros") { navigator.push(Route.QuickEntry(target.meal, target.date)) }
        }
    }
}

@Composable
private fun RowScope.ActionTile(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.weight(1f).heightIn(min = 76.dp).clickable(onClick = onClick),
    ) {
        Column(Modifier.fillMaxWidth().padding(Spacing.md)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
