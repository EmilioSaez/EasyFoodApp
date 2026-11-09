package app.luxion.fitarchiveapp.ui.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Barra inferior estándar: Diario · Alimentos · Recetas · Mi peso · Objetivos (indicador transparente, seleccionado en `primary`). */
@Composable
fun EasyFoodNavigationBar(selected: TopLevelTab, navigator: Navigator) {
    val scheme = MaterialTheme.colorScheme
    NavigationBar(containerColor = scheme.surfaceContainerLow) {
        TopLevelTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { if (tab != selected) navigator.navigateTab(tab) },
                icon = { Text(tab.icon) },
                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall, maxLines = 1) }, // sin el tracking de labelMedium: «Objetivos» no cabía
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = scheme.primary,
                    selectedTextColor = scheme.primary,
                    unselectedIconColor = scheme.onSurfaceVariant,
                    unselectedTextColor = scheme.onSurfaceVariant,
                    indicatorColor = Color.Transparent,
                ),
            )
        }
    }
}
