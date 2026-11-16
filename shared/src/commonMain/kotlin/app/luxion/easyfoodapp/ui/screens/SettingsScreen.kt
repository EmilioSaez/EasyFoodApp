package app.luxion.easyfoodapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.luxion.easyfoodapp.ui.navigation.EasyFoodNavigationBar
import app.luxion.easyfoodapp.ui.navigation.Navigator
import app.luxion.easyfoodapp.ui.navigation.Route
import app.luxion.easyfoodapp.ui.navigation.TopLevelTab
import app.luxion.easyfoodapp.ui.theme.Spacing

/** Secciones de «Ajustes» (docs/design.md §2.23): cada una agrupa filas que abren la pantalla que ya tenía esa función. */
data class SettingsSection(val title: String, val links: List<YouLink>)

fun settingsSections(): List<SettingsSection> = listOf(
    SettingsSection(
        "Recordatorios",
        listOf(YouLink("Recordatorios", "Avisos de comidas, de pesarte y de beber agua", Route.Reminders)),
    ),
    SettingsSection(
        "IA (tu clave)",
        listOf(YouLink("Ajustes de IA", "Clave de API, modelo, límite diario y uso", Route.AiSettings)),
    ),
    SettingsSection(
        "Datos",
        listOf(YouLink("Datos y copias de seguridad", "Exportar e importar (JSON, QR, red local) y copias automáticas", Route.Data)),
    ),
)

/** Ajustes: recordatorios, IA y datos/copias. Se abre desde «Tú». */
@Composable
fun SettingsScreen(navigator: Navigator) {
    Scaffold(
        topBar = { ScreenHeader("Ajustes", onBack = { navigator.pop() }) },
        bottomBar = { EasyFoodNavigationBar(TopLevelTab.You, navigator) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            settingsSections().forEach { section ->
                SectionLabel(section.title, Modifier.padding(top = Spacing.md))
                YouLinkList(section.links, onOpen = { navigator.push(it.route) })
            }
        }
    }
}
