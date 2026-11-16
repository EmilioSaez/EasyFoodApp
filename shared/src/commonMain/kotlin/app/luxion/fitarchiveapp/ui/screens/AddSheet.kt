package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.luxion.fitarchiveapp.ui.components.label
import app.luxion.fitarchiveapp.ui.navigation.AddSheetTarget
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.theme.Spacing

/**
 * Hoja «Añadir» (docs/design.md §2.22): la única puerta de entrada de comida. Se abre con `navigator.openAddSheet(meal, date)`
 * y la pinta `App`. Es un contenedor con tres huecos, cada uno en su fichero para que dos sesiones no se pisen:
 * [AddSheetSearch] (buscador), [AddSheetActions] (escanear, foto del plato, etiqueta, entrada rápida) y [AddSheetLists]
 * (recientes, favoritos, plantillas, recetas). En la Fase 0 enlazan con las pantallas existentes.
 * Cualquier `navigator.push(...)` desde un hueco cierra la hoja.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(target: AddSheetTarget, navigator: Navigator) {
    ModalBottomSheet(
        onDismissRequest = navigator::closeAddSheet,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier.padding(horizontal = Spacing.screen).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text("Añadir", style = MaterialTheme.typography.headlineMedium)
            Text(
                target.meal?.let { "a ${it.label}" } ?: "a la comida que toca",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AddSheetSearch(target, navigator)
            AddSheetActions(target, navigator)
            AddSheetLists(target, navigator)
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}
