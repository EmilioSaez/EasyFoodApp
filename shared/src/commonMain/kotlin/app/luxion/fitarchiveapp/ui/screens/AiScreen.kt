package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.luxion.fitarchiveapp.ai.AiModule
import app.luxion.fitarchiveapp.ui.components.EmptyState
import app.luxion.fitarchiveapp.ui.components.EmptyStateAction
import app.luxion.fitarchiveapp.ui.navigation.EasyFoodNavigationBar
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.navigation.Route
import app.luxion.fitarchiveapp.ui.navigation.TopLevelTab

/** Estado de la pestaña «IA» según los ajustes. */
enum class AiTabStatus { NeedsKey, Disabled, Ready }

/** Marcador de la pestaña vacía: sin clave pide configurarla; las funciones llegarán en PRs siguientes. */
fun aiTabStatus(hasKey: Boolean, enabled: Boolean, consentAccepted: Boolean): AiTabStatus = when {
    !hasKey -> AiTabStatus.NeedsKey
    !enabled || !consentAccepted -> AiTabStatus.Disabled
    else -> AiTabStatus.Ready
}

/** Pestaña «IA»: por ahora un placeholder con el estado de la configuración (docs/design.md §2.16). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiScreen(navigator: Navigator, module: AiModule) {
    var status by remember { mutableStateOf<AiTabStatus?>(null) }
    LaunchedEffect(module) {
        val s = module.settings.load()
        status = aiTabStatus(module.service.hasKey(), s.enabled, s.consentAccepted)
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("IA") },
                actions = {
                    IconButton(
                        onClick = { navigator.push(Route.AiSettings) },
                        modifier = Modifier.semantics { contentDescription = "Ajustes de IA" },
                    ) { Text("⚙", style = MaterialTheme.typography.titleLarge) }
                },
            )
        },
        bottomBar = { EasyFoodNavigationBar(TopLevelTab.Ai, navigator) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            val open = EmptyStateAction("Ajustes de IA") { navigator.push(Route.AiSettings) }
            when (status) {
                null -> Unit
                AiTabStatus.NeedsKey -> EmptyState(
                    icon = "✦",
                    title = "Configura tu clave",
                    message = "Pega tu clave de API de Anthropic para activar la IA. Se guarda solo en este dispositivo y, sin ella, el resto de la app funciona igual.",
                    primaryAction = open,
                )
                AiTabStatus.Disabled -> EmptyState(
                    icon = "✦",
                    title = "La IA está desactivada",
                    message = "Ya hay una clave guardada. Actívala en los ajustes de IA cuando quieras usarla.",
                    primaryAction = open,
                )
                AiTabStatus.Ready -> EmptyState(
                    icon = "✦",
                    title = "IA lista",
                    message = "Las funciones (etiqueta por foto, consejos y foto del plato) llegarán pronto.",
                    secondaryAction = open,
                )
            }
        }
    }
}
