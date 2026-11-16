package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.clickable
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
 * HUECO B1 · buscador de la hoja «Añadir». Fase 0: un campo falso que abre la pantalla de búsqueda existente (`Route.Add`).
 * La sesión B lo sustituye por el buscador real (texto, marca, supermercado, «solo mis productos»).
 */
@Composable
fun AddSheetSearch(target: AddSheetTarget, navigator: Navigator) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable {
            navigator.push(Route.Add(meal = target.meal, date = target.date))
        },
    ) {
        Text(
            "Busca un alimento o una marca",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
        )
    }
}
