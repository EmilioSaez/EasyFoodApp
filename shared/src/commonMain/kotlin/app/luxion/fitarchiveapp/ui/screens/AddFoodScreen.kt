package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.data.CustomFoodStore
import app.luxion.fitarchiveapp.data.ProductSearchStore
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.scanner.isBarcodeScannerSupported
import app.luxion.fitarchiveapp.ui.components.EmptyStateAction
import app.luxion.fitarchiveapp.ui.components.label
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.navigation.Route
import app.luxion.fitarchiveapp.ui.theme.Spacing

/**
 * Añadir alimento (docs/design.md §2.2): el buscador compartido ([FoodSearch]) más accesos rápidos al
 * escáner y a los alimentos propios.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodScreen(
    navigator: Navigator,
    route: Route.Add,
    searchStore: ProductSearchStore? = null,
    customFoods: CustomFoodStore? = null,
) {
    var meal by remember { mutableStateOf(route.meal) }
    var query by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Añadir a") },
                navigationIcon = {
                    IconButton(
                        onClick = { navigator.pop() },
                        modifier = Modifier.semantics { contentDescription = "Atrás" },
                    ) { Text("←", style = MaterialTheme.typography.titleLarge) }
                },
                actions = { MealPicker(meal) { meal = it } },
            )
        },
    ) { padding ->
        FoodSearch(
            query = query,
            onQueryChange = { query = it },
            searchStore = searchStore,
            customFoods = customFoods,
            onPick = { navigator.push(Route.ProductDetail(barcode = it.result.key, meal = meal)) },
            modifier = Modifier.padding(padding),
            emptyPrimary = EmptyStateAction("Crear alimento propio") { navigator.push(Route.CustomFood(meal = meal)) },
            emptySecondary = EmptyStateAction("Escanear código") { navigator.push(Route.Scanner(meal)) },
            quickActions = {
                QuickActions(
                    onScan = { navigator.push(Route.Scanner(meal)) },
                    onCustom = { navigator.push(Route.CustomFood(meal = meal)) },
                    modifier = Modifier.padding(horizontal = Spacing.l),
                )
            },
        )
    }
}

@Composable
private fun MealPicker(meal: MealType, onSelect: (MealType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.padding(end = Spacing.s)) {
            Text("${meal.label} ▾")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MealType.entries.forEach { m ->
                DropdownMenuItem(text = { Text(m.label) }, onClick = { onSelect(m); open = false })
            }
        }
    }
}

/** Escanear · Código EAN · Alimento propio. En escritorio no hay escáner: «Código EAN» pasa a destacado. */
@Composable
private fun QuickActions(onScan: () -> Unit, onCustom: () -> Unit, modifier: Modifier = Modifier) {
    val tall = Modifier.height(72.dp)
    val primary = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
    val neutral = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        if (isBarcodeScannerSupported) {
            Button(onClick = onScan, modifier = tall.weight(1f), shape = MaterialTheme.shapes.medium, colors = primary) {
                Text("Escanear")
            }
        }
        // El escáner y la entrada manual de EAN comparten pantalla (docs/design.md §2.3).
        Button(
            onClick = onScan,
            modifier = tall.weight(1f),
            shape = MaterialTheme.shapes.medium,
            colors = if (isBarcodeScannerSupported) neutral else primary,
        ) { Text("Código EAN") }
        Button(onClick = onCustom, modifier = tall.weight(1f), shape = MaterialTheme.shapes.medium, colors = neutral) {
            Text("Alimento propio")
        }
    }
}
