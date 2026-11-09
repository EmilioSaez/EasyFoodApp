package app.luxion.fitarchiveapp.ui.navigation

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** Barra inferior: Diario · Alimentos · Recetas · Mi peso · Objetivos (indicador `secondaryContainer`). */
@Composable
fun EasyFoodNavigationBar(selected: TopLevelTab, navigator: Navigator) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        TopLevelTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { if (tab != selected) navigator.navigateTab(tab) },
                icon = { Text(tab.icon) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            )
        }
    }
}
