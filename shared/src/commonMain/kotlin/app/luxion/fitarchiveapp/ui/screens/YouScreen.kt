package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.luxion.fitarchiveapp.ui.navigation.EasyFoodNavigationBar
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.navigation.Route
import app.luxion.fitarchiveapp.ui.navigation.TopLevelTab

/** Entrada de la lista de «Tú»: a dónde lleva y cómo se llama. Cada sesión de rediseño sustituye su fila por su pantalla nueva. */
data class YouLink(val title: String, val subtitle: String, val route: Route)

/**
 * Fase 0: lista provisional que mantiene accesible todo lo que antes tenía pestaña o icono (peso, resumen, objetivos, IA,
 * recetas, alimentos, recordatorios, ajustes de IA y datos). La sesión C (docs/design.md §2.22) la convierte en la pantalla «Tú» del diseño.
 */
fun youLinks(): List<YouLink> = listOf(
    YouLink("Mi peso", "Registro, gráfica y objetivo", Route.Weight),
    YouLink("Resumen", "Semana y mes", Route.Summary),
    YouLink("Objetivos", "kcal, macros, comidas y agua", Route.Goals),
    YouLink("Coach IA", "¿Qué como?, ¿Cómo voy? y preguntas", Route.Ai),
    YouLink("Recetas", "Tus recetas", Route.Recipes),
    YouLink("Alimentos", "Buscar, favoritos y plantillas", Route.Add()),
    YouLink("Recordatorios", "Comidas, peso y agua", Route.Reminders),
    YouLink("Ajustes de IA", "Clave, modelo y límite", Route.AiSettings),
    YouLink("Datos", "Exportar, importar y copias", Route.Data),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouScreen(navigator: Navigator) {
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Tú") }) },
        bottomBar = { EasyFoodNavigationBar(TopLevelTab.You, navigator) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(youLinks(), key = { it.title }) { link ->
                    ListItem(
                        headlineContent = { Text(link.title, style = MaterialTheme.typography.titleMedium) },
                        supportingContent = { Text(link.subtitle) },
                        trailingContent = { Text("›", style = MaterialTheme.typography.titleLarge) },
                        modifier = Modifier.clickable { navigator.push(link.route) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
