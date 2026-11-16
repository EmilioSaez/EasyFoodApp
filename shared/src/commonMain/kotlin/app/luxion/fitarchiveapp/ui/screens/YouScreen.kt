package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.data.DiaryStore
import app.luxion.fitarchiveapp.data.GoalStore
import app.luxion.fitarchiveapp.data.WeightStore
import app.luxion.fitarchiveapp.domain.PeriodRange
import app.luxion.fitarchiveapp.domain.PeriodSummary
import app.luxion.fitarchiveapp.domain.SummaryPeriod
import app.luxion.fitarchiveapp.model.WeightEntry
import app.luxion.fitarchiveapp.model.WeightGoal
import app.luxion.fitarchiveapp.ui.components.DailyKcalChart
import app.luxion.fitarchiveapp.ui.components.formatKcal
import app.luxion.fitarchiveapp.ui.navigation.EasyFoodNavigationBar
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.navigation.Route
import app.luxion.fitarchiveapp.ui.navigation.TopLevelTab
import app.luxion.fitarchiveapp.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Entrada de una lista de «Tú» o de «Ajustes»: a dónde lleva y cómo se llama. */
data class YouLink(val title: String, val subtitle: String, val route: Route)

/**
 * Filas de «Tú» bajo las tarjetas de peso y resumen. Recetas y plantillas, y Coach IA, las rediseña la sesión D; el
 * peso y el resumen se abren desde sus tarjetas ([YOU_CARD_ROUTES]) y recordatorios, IA y datos desde «Ajustes».
 */
fun youLinks(goalsSubtitle: String = "kcal, macros, actividad, comidas y agua"): List<YouLink> = listOf(
    YouLink("Objetivos", goalsSubtitle, Route.Goals),
    YouLink("Coach IA", "¿Qué como?, ¿Cómo voy? y preguntas libres", Route.Ai),
    YouLink("Recetas y plantillas", "Tus recetas y tus comidas guardadas", Route.Recipes),
    YouLink("Ajustes", "Recordatorios, IA, datos y copias", Route.Settings),
)

/** Destinos que se abren desde las tarjetas de «Tú» (no son filas de la lista). */
val YOU_CARD_ROUTES: List<Route> = listOf(Route.Weight, Route.Summary)

/**
 * «Tú» (docs/design.md §2.23): tarjeta de peso (con registro directo), tarjeta de resumen semanal/mensual y la lista de
 * accesos. [mealsPerDay] solo adorna el subtítulo de Objetivos.
 */
@Composable
fun YouScreen(
    navigator: Navigator,
    weightStore: WeightStore,
    goalStore: GoalStore,
    diaryStore: DiaryStore,
    today: () -> String,
    mealsPerDay: Int? = null,
) {
    val todayIso = today()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var entries by remember { mutableStateOf<List<WeightEntry>>(emptyList()) }
    var weightGoal by remember { mutableStateOf<WeightGoal?>(null) }
    var weightLoaded by remember { mutableStateOf(false) }
    var weightPeriod by remember { mutableStateOf(WeightPeriod.ONE_MONTH) }
    var formOpen by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var goalKcal by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(weightStore, goalStore, reload) {
        try {
            entries = weightStore.all()
            weightGoal = weightStore.goal()
            goalKcal = goalStore.get().kcal
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            snackbar.showSnackbar("No se pudieron leer los datos guardados.")
        } finally {
            weightLoaded = true
        }
    }

    Scaffold(
        topBar = { ScreenHeader("Tú", onBack = null) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { EasyFoodNavigationBar(TopLevelTab.You, navigator) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            YouCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.lg)) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Tu peso", Modifier.weight(1f))
                        TextButton(onClick = { navigator.push(Route.Weight) }) { Text("Historial de peso") }
                    }
                    WeightOverview(
                        entries = entries,
                        loaded = weightLoaded,
                        goal = weightGoal,
                        todayIso = todayIso,
                        period = weightPeriod,
                        onPeriod = { weightPeriod = it },
                        showGoal = true,
                    )
                    Button(
                        onClick = { formOpen = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) { Text("Registrar peso") }
                }
            }

            SummaryCardForYou(navigator, diaryStore, goalStore, weightStore, todayIso, reload)

            YouLinkList(
                youLinks(goalsSubtitle = goalsSubtitle(goalKcal, mealsPerDay)),
                onOpen = { navigator.push(it.route) },
            )
        }
    }

    if (formOpen) {
        WeightEntryDialog(
            weightStore = weightStore,
            entries = entries,
            todayIso = todayIso,
            editing = null,
            onDismiss = { formOpen = false },
            onSaved = { message ->
                formOpen = false
                reload++
                scope.launch { snackbar.showSnackbar(message) }
            },
            onError = { message -> scope.launch { snackbar.showSnackbar(message) } },
        )
    }
}

/** «2.100 kcal · 4 comidas» (lo que se sepa del objetivo y de las comidas al día). */
internal fun goalsSubtitle(kcal: Int?, mealsPerDay: Int?): String {
    val parts = listOfNotNull(
        kcal?.let { "${formatKcal(it.toDouble())} kcal" },
        mealsPerDay?.let { "$it comidas" },
    )
    return if (parts.isEmpty()) "Fija tu objetivo diario de kcal y macros" else parts.joinToString(" · ")
}

private sealed interface YouSummaryLoad {
    data object Loading : YouSummaryLoad
    data object Failed : YouSummaryLoad
    data class Ready(val summary: PeriodSummary) : YouSummaryLoad
}

/** Tarjeta «Esta semana / Este mes» de «Tú»: medias, días cumplidos y barras por día; abre el Resumen completo. */
@Composable
private fun SummaryCardForYou(
    navigator: Navigator,
    diaryStore: DiaryStore,
    goalStore: GoalStore,
    weightStore: WeightStore,
    todayIso: String,
    reload: Int,
) {
    var period by remember { mutableStateOf(SummaryPeriod.WEEK) }
    var load by remember { mutableStateOf<YouSummaryLoad>(YouSummaryLoad.Loading) }
    val range = remember(period, todayIso) { PeriodRange.of(period, todayIso) }
    LaunchedEffect(range, reload, diaryStore, goalStore, weightStore) {
        load = try {
            YouSummaryLoad.Ready(loadPeriodSummary(range, todayIso, diaryStore, goalStore, weightStore))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            YouSummaryLoad.Failed
        }
    }

    YouCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                SectionLabel(if (period == SummaryPeriod.WEEK) "Esta semana" else "Este mes", Modifier.weight(1f))
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                    SummaryPeriod.entries.forEachIndexed { i, option ->
                        SegmentedButton(
                            selected = option == period,
                            onClick = { period = option },
                            shape = SegmentedButtonDefaults.itemShape(i, SummaryPeriod.entries.size),
                        ) { Text(if (option == SummaryPeriod.WEEK) "Sem" else "Mes") }
                    }
                }
            }
            when (val state = load) {
                YouSummaryLoad.Loading -> Unit
                YouSummaryLoad.Failed -> Text(
                    "No se pudo cargar el resumen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is YouSummaryLoad.Ready -> {
                    val summary = state.summary
                    val averages = summary.averages
                    if (summary.isEmpty || averages == null) {
                        Text(
                            "Aún no hay comidas apuntadas en este periodo. Cuando las haya, aquí verás tus medias y tus días cumplidos.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                            Column(Modifier.weight(1f)) {
                                Text(formatKcal(averages.kcal), style = MaterialTheme.typography.displaySmall)
                                val goal = summary.goalKcal
                                Text(
                                    if (goal != null) "kcal de media · ${formatKcalDelta(averages.kcal - goal)} frente al objetivo" else "kcal de media",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            val completed = summary.completedDays
                            if (completed != null) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("$completed/${summary.judgedDays}", style = MaterialTheme.typography.displaySmall)
                                    Text("días cumplidos", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        DailyKcalChart(summary.days, summary.range.period, summary.goalKcal)
                    }
                }
            }
            TextButton(
                onClick = { navigator.push(Route.Summary) },
                modifier = Modifier.semantics { contentDescription = "Ver resumen completo" },
            ) { Text("Ver resumen completo") }
        }
    }
}
