package app.luxion.fitarchiveapp.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.luxion.fitarchiveapp.ui.theme.easyFoodColors

/**
 * Barra inferior «Hoy · + · Tú» (docs/design.md §1.7 y §4). «Hoy» y «Tú» son destinos; el «+» central, en limón, no lo es:
 * abre la hoja «Añadir» ([Navigator.openAddSheet]). Las pantallas colgadas de «Tú» (peso, objetivos…) marcan [TopLevelTab.You].
 */
@Composable
fun EasyFoodNavigationBar(selected: TopLevelTab, navigator: Navigator) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().background(scheme.surfaceContainerLow)) {
        HorizontalDivider(color = scheme.outlineVariant)
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).height(72.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavTab(TopLevelTab.Today, selected, navigator)
            AddButton(onClick = { navigator.openAddSheet() })
            NavTab(TopLevelTab.You, selected, navigator)
        }
    }
}

@Composable
private fun NavTab(tab: TopLevelTab, selected: TopLevelTab, navigator: Navigator) {
    val scheme = MaterialTheme.colorScheme
    val isSelected = tab == selected
    val color = if (isSelected) scheme.primary else scheme.onSurfaceVariant
    Column(
        Modifier
            .widthIn(min = 96.dp)
            .height(64.dp)
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = isSelected, role = Role.Tab) {
                // Desde una pantalla colgada de «Tú» (p. ej. Peso), tocar «Tú» vuelve al centro.
                if (navigator.current != tab.route) navigator.navigateTab(tab)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(tab.icon, color = color, fontSize = 22.sp)
        Text(tab.label, color = color, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** Botón «+» cuadrado y redondeado en limón (color de marca). */
@Composable
private fun AddButton(onClick: () -> Unit) {
    val colors = MaterialTheme.easyFoodColors
    Box(
        Modifier
            .size(56.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(colors.accent)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Añadir" },
        contentAlignment = Alignment.Center,
    ) {
        Text("+", color = colors.onAccent, fontSize = 30.sp)
    }
}
