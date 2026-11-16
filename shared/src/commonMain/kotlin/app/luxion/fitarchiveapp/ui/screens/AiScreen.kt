package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.ai.AiAdviceSource
import app.luxion.fitarchiveapp.ai.AiModule
import app.luxion.fitarchiveapp.ai.AiPrivacy
import app.luxion.fitarchiveapp.data.DiaryStore
import app.luxion.fitarchiveapp.ui.components.EmptyState
import app.luxion.fitarchiveapp.ui.components.EmptyStateAction
import app.luxion.fitarchiveapp.ui.navigation.EasyFoodNavigationBar
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.navigation.Route
import app.luxion.fitarchiveapp.ui.navigation.TopLevelTab
import app.luxion.fitarchiveapp.ui.theme.Spacing

/** Estado de la pestaña «IA» según los ajustes. */
enum class AiTabStatus { NeedsKey, Disabled, Ready }

/** Estado de la pestaña: sin clave pide configurarla; con clave pero sin activar o sin aviso aceptado, activarla. */
fun aiTabStatus(hasKey: Boolean, enabled: Boolean, consentAccepted: Boolean): AiTabStatus = when {
    !hasKey -> AiTabStatus.NeedsKey
    !enabled || !consentAccepted -> AiTabStatus.Disabled
    else -> AiTabStatus.Ready
}

/**
 * Pantalla «Coach» (docs/design.md §2.16 y §2.24): sin clave o desactivada, un estado vacío; lista, las tarjetas «¿Qué como?»,
 * «¿Cómo voy?», el menú con la lista de la compra y la pregunta libre, más las acciones y tarjetas de otras funciones ([aiTabActions], [aiTabCards]).
 */
@Composable
fun AiScreen(
    navigator: Navigator,
    module: AiModule,
    source: AiAdviceSource,
    diaryStore: DiaryStore,
    today: () -> String,
    mealChoices: MealChoices,
    searchStore: app.luxion.fitarchiveapp.data.ProductSearchStore? = null,
    customFoods: app.luxion.fitarchiveapp.data.CustomFoodStore? = null,
) {
    val state = remember(module, source, diaryStore, searchStore, customFoods) {
        AiTabState(
            module = module,
            source = source,
            diary = diaryStore,
            today = today,
            defaultMeal = mealChoices::defaultMeal,
            nowMillis = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
            catalog = if (searchStore != null || customFoods != null) app.luxion.fitarchiveapp.plate.FoodCatalog(searchStore, customFoods) else null,
        )
    }
    LaunchedEffect(state) { state.refresh() }
    val env = remember(navigator, module, diaryStore, mealChoices) { AiTabEnvironment(navigator, module, diaryStore, today, mealChoices) }
    Scaffold(
        bottomBar = { EasyFoodNavigationBar(TopLevelTab.You, navigator) },
    ) { padding ->
        val open = EmptyStateAction("Ajustes de IA") { navigator.push(Route.AiSettings) }
        Column(Modifier.fillMaxSize().padding(padding)) {
            CoachHeader(
                subtitle = if (state.gate == AiTabStatus.Ready && !state.limitReached) {
                    "Hoy te quedan ${state.callsLeft} de ${state.dailyLimit} llamadas"
                } else {
                    null
                },
                onSettings = { navigator.push(Route.AiSettings) },
            )
            when (state.gate) {
                null -> Box(Modifier.fillMaxSize())
                AiTabStatus.NeedsKey -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = "✦",
                        title = "Configura tu clave",
                        message = "Pega tu clave de API de Anthropic para activar la IA. Se guarda solo en este dispositivo y, sin ella, el resto de la app funciona igual.",
                        primaryAction = open,
                    )
                }
                AiTabStatus.Disabled -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = "✦",
                        title = "La IA está desactivada",
                        message = "Ya hay una clave guardada. Actívala en los ajustes de IA cuando quieras usarla.",
                        primaryAction = open,
                    )
                }
                AiTabStatus.Ready -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.screen, vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    UsageBanner(state, open)
                    if (!state.advicePrivacyAccepted) PrivacyNotice(state)
                    if (aiTabActions.isNotEmpty()) ToolsRow(env)
                    AiSuggestCard(state, navigator)
                    AiReviewCard(state)
                    AiPlanCard(state)
                    AiAskCard(state)
                    aiTabCards.forEach { it.content(env) }
                    Text(
                        AI_DISCLAIMER,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Spacing.lg),
                    )
                }
            }
        }
    }
}

/** Cabecera «Coach» (docs/design.md §2.24): título de pantalla, llamadas que quedan hoy y acceso a los ajustes de IA. */
@Composable
private fun CoachHeader(subtitle: String?, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.sm, top = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Coach", style = MaterialTheme.typography.headlineMedium)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onSettings, modifier = Modifier.semantics { contentDescription = "Ajustes de IA" }) {
            Text("⚙", style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** Con el tope diario alcanzado, un aviso con acceso a los ajustes (las llamadas que quedan van en la cabecera). */
@Composable
private fun UsageBanner(state: AiTabState, open: EmptyStateAction) {
    if (state.limitReached) {
        AiCard("Límite diario alcanzado") {
            Text(
                "Has usado las ${state.dailyLimit} llamadas de hoy. Mañana se reinicia, o sube el límite en los ajustes de IA.",
                style = MaterialTheme.typography.bodyMedium,
            )
            FilledTonalButton(onClick = open.onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(open.label) }
        }
    }
}

/** Aviso de qué datos viajan en las sugerencias y consejos; hasta aceptarlo, las acciones de IA quedan deshabilitadas. */
@Composable
private fun PrivacyNotice(state: AiTabState) {
    AiCard("Antes de empezar") {
        Text(AiPrivacy.ADVICE_NOTICE, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = state::acceptAdvicePrivacy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Entendido") }
    }
}

/** Fila «Herramientas»: una acción por función registrada en [aiTabActions]. */
@Composable
private fun ToolsRow(env: AiTabEnvironment) {
    AiCard("Herramientas") {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            aiTabActions.forEach { action ->
                FilledTonalButton(onClick = { action.onClick(env) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("${action.icon} ${action.label}")
                }
            }
        }
    }
}
