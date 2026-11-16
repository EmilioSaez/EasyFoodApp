package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.luxion.fitarchiveapp.ui.components.EasyFoodFilterChip
import app.luxion.fitarchiveapp.ui.navigation.AddSheetTarget
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.navigation.Route
import app.luxion.fitarchiveapp.ui.theme.Spacing

/**
 * HUECO B3 · listas de la hoja «Añadir»: Recientes · Favoritos · Plantillas · Recetas.
 * Fase 0: cuatro chips que abren las pantallas existentes (Recientes y Favoritos, la pestaña Alimentos; Plantillas, su
 * lista de plantillas; Recetas, la lista de recetas). La sesión B (o D para plantillas y recetas) pinta aquí las listas reales.
 */
@Composable
fun AddSheetLists(target: AddSheetTarget, navigator: Navigator) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        val add = Route.Add(meal = target.meal, date = target.date)
        EasyFoodFilterChip(selected = false, onClick = { navigator.push(add) }, label = { Text("Recientes") })
        EasyFoodFilterChip(selected = false, onClick = { navigator.push(add) }, label = { Text("Favoritos") })
        EasyFoodFilterChip(selected = false, onClick = { navigator.push(add.copy(templates = true)) }, label = { Text("Plantillas") })
        EasyFoodFilterChip(selected = false, onClick = { navigator.push(Route.Recipes) }, label = { Text("Recetas") })
    }
}
