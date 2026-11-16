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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.navigation.Route
import app.luxion.fitarchiveapp.ui.theme.PillShape
import app.luxion.fitarchiveapp.ui.theme.Spacing
import app.luxion.fitarchiveapp.ui.theme.easyFoodColors

/**
 * HUECO B2 · acciones de la hoja «Añadir»: Escanear, Foto del plato (IA), Etiqueta (IA) y Entrada rápida, en mosaicos de
 * 2×2 con un icono, y «Crear alimento propio» debajo. Todas llevan la comida y el día de la hoja; las pantallas de IA
 * explican qué falta si la IA no está lista.
 */
@Composable
fun AddSheetActions(model: AddSheetModel, navigator: Navigator) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ActionTile("▥", "Escanear", "Código de barras", ai = false) { navigator.push(Route.Scanner(model.meal, model.date)) }
            ActionTile("◉", "Foto del plato", "Detecta alimentos", ai = true) { navigator.push(Route.PlatePhoto(model.meal, model.date)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ActionTile("▤", "Etiqueta", "Crea el alimento", ai = true) { navigator.push(Route.LabelPhoto(meal = model.meal)) }
            ActionTile("⚡", "Entrada rápida", "Solo kcal y macros", ai = false) { navigator.push(Route.QuickEntry(model.meal, model.date)) }
        }
        OutlinedButton(
            onClick = { navigator.push(Route.CustomFood(meal = model.meal)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = PillShape,
        ) { Text("Crear alimento propio") }
    }
}

@Composable
private fun RowScope.ActionTile(icon: String, title: String, subtitle: String, ai: Boolean, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.weight(1f).heightIn(min = 96.dp).clickable(onClick = onClick),
    ) {
        Column(Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(icon, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (ai) {
                    Surface(color = MaterialTheme.easyFoodColors.accent, shape = PillShape) {
                        Text(
                            "IA",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.easyFoodColors.onAccent,
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
                        )
                    }
                }
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
