package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.data.ExerciseStore
import app.luxion.fitarchiveapp.data.GoalStore
import app.luxion.fitarchiveapp.data.WaterStore
import app.luxion.fitarchiveapp.ui.components.ExercisePercentPicker
import app.luxion.fitarchiveapp.ui.components.WaterGoalRow
import app.luxion.fitarchiveapp.domain.Goal
import app.luxion.fitarchiveapp.domain.MealPlan
import app.luxion.fitarchiveapp.domain.Sex
import app.luxion.fitarchiveapp.domain.UserProfile
import app.luxion.fitarchiveapp.domain.StepsActivity
import app.luxion.fitarchiveapp.model.DailyGoals
import app.luxion.fitarchiveapp.steps.rememberStepsProvider
import app.luxion.fitarchiveapp.ui.components.MacroDot
import app.luxion.fitarchiveapp.ui.components.MealsPerDayPicker
import app.luxion.fitarchiveapp.ui.components.NumberField
import app.luxion.fitarchiveapp.ui.components.formatKcal
import app.luxion.fitarchiveapp.ui.components.parseNumber
import app.luxion.fitarchiveapp.ui.navigation.EasyFoodNavigationBar
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.navigation.Route
import app.luxion.fitarchiveapp.ui.navigation.TopLevelTab
import app.luxion.fitarchiveapp.ui.theme.Spacing
import app.luxion.fitarchiveapp.ui.theme.easyFoodColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Objetivos y TDEE (docs/design.md §2.6). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    navigator: Navigator,
    goalStore: GoalStore,
    today: () -> String = { "" },
    mealsPerDay: Int? = null,
    onMealsPerDayChange: (Int) -> Unit = {},
    waterStore: WaterStore? = null,
    exerciseStore: ExerciseStore? = null,
) {
    var saved by remember { mutableStateOf(DailyGoals()) }
    var sex by remember { mutableStateOf(Sex.MALE) }
    var ageText by remember { mutableStateOf("") }
    var weightText by remember { mutableStateOf("") }
    var heightText by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf(Goal.MAINTAIN) }
    var paceIndex by remember { mutableStateOf(GoalsPlanner.DEFAULT_PACE_INDEX) }
    var editing by remember { mutableStateOf(false) }
    // null = automático; texto (posiblemente vacío) = ajuste manual.
    var kcalText by remember { mutableStateOf<String?>(null) }
    var proteinText by remember { mutableStateOf<String?>(null) }
    var carbsText by remember { mutableStateOf<String?>(null) }
    var fatText by remember { mutableStateOf<String?>(null) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(goalStore) {
        try {
            saved = goalStore.get()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            snackbar.showSnackbar("No se pudieron leer los objetivos guardados.")
        }
    }

    val age = parseNumber(ageText)?.toInt()
    val weight = parseNumber(weightText)
    val height = parseNumber(heightText)
    val ageError = ageText.isNotEmpty() && age !in 10..120
    val weightError = weightText.isNotEmpty() && weight?.let { it in 20.0..500.0 } != true
    val heightError = heightText.isNotEmpty() && height?.let { it in 80.0..260.0 } != true
    val profile = if (age != null && weight != null && height != null && !ageError && !weightError && !heightError) {
        UserProfile(age, sex, weight, height, goal)
    } else null
    val stepsState = rememberStepsState(rememberStepsProvider(), today)
    val averageSteps = StepsActivity.averageSteps(stepsState.days, today())
    val plan = profile?.let { GoalsPlanner.plan(it, paceIndex, averageSteps?.first) }

    fun override(text: String?): Int? = text?.let { parseNumber(it)?.toInt() }?.takeIf { it >= 0 }
    val kcalOverride = override(kcalText)?.takeIf { it > 0 }
    val resolved = GoalsPlanner.resolve(
        plan, profile, saved, kcalOverride, override(proteinText), override(carbsText), override(fatText),
    )
    val custom = kcalOverride != null
    val hasManual = kcalText != null || proteinText != null || carbsText != null || fatText != null

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Objetivos") },
                actions = {
                    IconButton(
                        onClick = { navigator.push(Route.Reminders) },
                        modifier = Modifier.semantics { contentDescription = "Recordatorios" },
                    ) { Text("🔔", style = MaterialTheme.typography.titleMedium) }
                    IconButton(
                        onClick = { navigator.push(Route.Data) },
                        modifier = Modifier.semantics { contentDescription = "Datos: exportar e importar" },
                    ) { Text("⇅", style = MaterialTheme.typography.titleLarge) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                goalStore.save(resolved)
                                saved = resolved
                                snackbar.showSnackbar("Objetivos guardados")
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                snackbar.showSnackbar("No se pudieron guardar los objetivos. Inténtalo de nuevo.")
                            }
                        }
                    },
                    enabled = resolved.kcal != null,
                    modifier = Modifier.fillMaxWidth().padding(Spacing.l).heightIn(min = 48.dp),
                ) { Text("Guardar objetivos") }
                EasyFoodNavigationBar(TopLevelTab.Goals, navigator)
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            ResultCard(resolved.kcal, plan, custom, Modifier.padding(top = Spacing.s))

            if (plan?.limited == true) {
                Text(
                    "El ritmo elegido bajaría de tu metabolismo basal; se ha limitado a ${formatKcal(plan.targetKcal.toDouble())} kcal.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.easyFoodColors.warning,
                )
            }
            if (kcalOverride != null && plan != null && kcalOverride < plan.bmrKcal) {
                Text(
                    "Tu objetivo está por debajo de tu metabolismo basal (${formatKcal(plan.bmrKcal.toDouble())} kcal).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.easyFoodColors.warning,
                )
            }

            Section("Meta") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(Goal.LOSE to "Perder", Goal.MAINTAIN to "Mantener", Goal.GAIN to "Ganar")
                        .forEachIndexed { i, (value, label) ->
                            SegmentedButton(
                                selected = goal == value,
                                onClick = { goal = value },
                                shape = SegmentedButtonDefaults.itemShape(i, 3),
                            ) { Text(label) }
                        }
                }
                val paceEnabled = goal != Goal.MAINTAIN
                val pace = GoalsPlanner.PACES[paceIndex]
                Text(
                    if (paceEnabled) "Ritmo: ${pace.toString().replace('.', ',').removeSuffix(",0")} kg/sem · ≈ ${formatKcal(GoalsPlanner.paceKcal(paceIndex).toDouble())} kcal/día"
                    else "Sin ajuste: el objetivo es tu gasto total",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = paceIndex.toFloat(),
                    onValueChange = { paceIndex = it.toInt().coerceIn(GoalsPlanner.PACES.indices) },
                    valueRange = 0f..(GoalsPlanner.PACES.size - 1).toFloat(),
                    steps = GoalsPlanner.PACES.size - 2,
                    enabled = paceEnabled,
                )
            }

            Section("Tus datos") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    DataCell("Sexo", Modifier.weight(1f)) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            listOf(Sex.MALE to "Hombre", Sex.FEMALE to "Mujer").forEachIndexed { i, (value, label) ->
                                SegmentedButton(
                                    selected = sex == value,
                                    onClick = { sex = value },
                                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                                    icon = {},
                                    label = { Text(label, maxLines = 1) },
                                    contentPadding = PaddingValues(horizontal = Spacing.xs),
                                )
                            }
                        }
                    }
                    NumberField(ageText, { ageText = it }, "Edad", Modifier.weight(1f), suffix = "años", isError = ageError)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    NumberField(weightText, { weightText = it }, "Peso", Modifier.weight(1f), suffix = "kg", isError = weightError)
                    NumberField(heightText, { heightText = it }, "Altura", Modifier.weight(1f), suffix = "cm", isError = heightError)
                }
                if (ageError || weightError || heightError) {
                    Text(
                        "Edad 10–120 años · peso 20–500 kg · altura 80–260 cm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            StepsSection(stepsState, averageSteps)

            Section("Comidas al día") {
                // Se guarda al elegir, aparte de «Guardar objetivos». Sin elección previa, el Diario usa el preset por defecto.
                MealsPerDayPicker(mealsPerDay ?: MealPlan.DEFAULT_MEALS, onMealsPerDayChange)
            }

            if (exerciseStore != null) {
                Section("Ejercicio") {
                    // Se guarda al elegir, aparte de «Guardar objetivos»; el Diario suma ese porcentaje al objetivo del día.
                    ExercisePercentPicker(exerciseStore)
                }
            }

            if (waterStore != null) {
                Section("Agua") {
                    // Se guarda al pulsar, aparte de «Guardar objetivos»; el Diario la compara con el agua del día.
                    WaterGoalRow(waterStore)
                }
            }

            MacroSplitCard(
                resolved = resolved,
                editing = editing,
                hasManual = hasManual,
                onToggleEdit = { editing = !editing },
                onReset = {
                    kcalText = null; proteinText = null; carbsText = null; fatText = null
                },
                kcalValue = kcalText ?: resolved.kcal?.toString().orEmpty(),
                proteinValue = proteinText ?: resolved.proteinG?.toString().orEmpty(),
                carbsValue = carbsText ?: resolved.carbsG?.toString().orEmpty(),
                fatValue = fatText ?: resolved.fatG?.toString().orEmpty(),
                onKcal = { kcalText = it },
                onProtein = { proteinText = it },
                onCarbs = { carbsText = it },
                onFat = { fatText = it },
            )
        }
    }
}

/** Casilla de «Tus datos»: rótulo pequeño y el control debajo. */
@Composable
private fun DataCell(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun ResultCard(
    targetKcal: Int?,
    plan: GoalsPlan?,
    custom: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            val label = when {
                custom -> "Objetivo diario · Personalizado"
                else -> "Objetivo diario"
            }
            Text(label, style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    targetKcal?.let { formatKcal(it.toDouble()) } ?: "—",
                    style = MaterialTheme.typography.displayMedium,
                )
                Text("kcal", Modifier.padding(start = Spacing.s, bottom = Spacing.s), style = MaterialTheme.typography.titleMedium)
            }
            Row(Modifier.fillMaxWidth()) {
                Stat("Metabolismo basal", plan?.bmrKcal, Modifier.weight(1f))
                Stat("Gasto total (TDEE)", plan?.tdeeKcal, Modifier.weight(1f))
            }
            Text(
                "Mifflin-St Jeor: TMB = 10·kg + 6,25·cm − 5·edad + 5 (hombre) / −161 (mujer); TDEE = TMB × 1,2 + pasos",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun Stat(label: String, kcal: Int?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(
            kcal?.let { "${formatKcal(it.toDouble())} kcal" } ?: "—",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun MacroSplitCard(
    resolved: DailyGoals,
    editing: Boolean,
    hasManual: Boolean,
    onToggleEdit: () -> Unit,
    onReset: () -> Unit,
    kcalValue: String,
    proteinValue: String,
    carbsValue: String,
    fatValue: String,
    onKcal: (String) -> Unit,
    onProtein: (String) -> Unit,
    onCarbs: (String) -> Unit,
    onFat: (String) -> Unit,
) {
    val p = resolved.proteinG ?: 0
    val c = resolved.carbsG ?: 0
    val f = resolved.fatG ?: 0
    val (pp, cp, fp) = GoalsPlanner.macroPercents(p, c, f)
    val colors = MaterialTheme.easyFoodColors
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Reparto de macros", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (hasManual) TextButton(onClick = onReset) { Text("Restablecer") }
                TextButton(onClick = onToggleEdit) { Text(if (editing) "Listo" else "Editar") }
            }
            // Barra apilada de 12 dp (P/H/G).
            Row(Modifier.fillMaxWidth().height(12.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(6.dp))) {
                if (pp + cp + fp > 0) {
                    if (pp > 0) Row(Modifier.weight(pp.toFloat()).height(12.dp).background(colors.macroProtein)) {}
                    if (cp > 0) Row(Modifier.weight(cp.toFloat()).height(12.dp).background(colors.macroCarbs)) {}
                    if (fp > 0) Row(Modifier.weight(fp.toFloat()).height(12.dp).background(colors.macroFat)) {}
                }
            }
            MacroRow("Proteína", pp, resolved.proteinG, colors.macroProtein)
            MacroRow("Hidratos", cp, resolved.carbsG, colors.macroCarbs)
            MacroRow("Grasa", fp, resolved.fatG, colors.macroFat)

            if (resolved.proteinG != null && resolved.carbsG != null && resolved.fatG != null) {
                val sum = GoalsPlanner.macroKcal(p, c, f)
                val kcal = resolved.kcal
                if (kcal != null && kotlin.math.abs(sum - kcal) > kcal * 0.05) {
                    Text(
                        "Los macros suman ${formatKcal(sum.toDouble())} kcal, distinto del objetivo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.warning,
                    )
                }
            }

            if (editing) {
                NumberField(kcalValue, onKcal, "Objetivo", Modifier.fillMaxWidth(), suffix = "kcal")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    NumberField(proteinValue, onProtein, "Proteína", Modifier.weight(1f), suffix = "g")
                    NumberField(carbsValue, onCarbs, "Hidratos", Modifier.weight(1f), suffix = "g")
                    NumberField(fatValue, onFat, "Grasa", Modifier.weight(1f), suffix = "g")
                }
            }
        }
    }
}

@Composable
private fun MacroRow(label: String, percent: Int, grams: Int?, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        MacroDot(color)
        Text(label, Modifier.weight(1f).padding(start = Spacing.s), style = MaterialTheme.typography.bodyLarge)
        Text(
            "$percent %",
            Modifier.padding(end = Spacing.l),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            grams?.let { "$it g" } ?: "—",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
