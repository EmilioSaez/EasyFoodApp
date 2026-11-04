package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import app.luxion.fitarchiveapp.data.DiaryStore
import app.luxion.fitarchiveapp.data.ProductRepository
import app.luxion.fitarchiveapp.data.ProductResult
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.NewDiaryEntry
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Product
import app.luxion.fitarchiveapp.model.QuantityUnit
import app.luxion.fitarchiveapp.model.dataWarnings
import app.luxion.fitarchiveapp.ui.components.DataSource
import app.luxion.fitarchiveapp.ui.components.DoubtfulDataBanner
import app.luxion.fitarchiveapp.ui.components.MacroDot
import app.luxion.fitarchiveapp.ui.components.NutritionTable
import app.luxion.fitarchiveapp.ui.components.QuantitySelector
import app.luxion.fitarchiveapp.ui.components.SourceChip
import app.luxion.fitarchiveapp.ui.components.formatGrams
import app.luxion.fitarchiveapp.ui.components.formatKcal
import app.luxion.fitarchiveapp.ui.components.label
import app.luxion.fitarchiveapp.ui.components.parseNumber
import app.luxion.fitarchiveapp.ui.components.toRows
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.navigation.Route
import app.luxion.fitarchiveapp.ui.theme.EanTextStyle
import app.luxion.fitarchiveapp.ui.theme.Spacing
import app.luxion.fitarchiveapp.ui.theme.easyFoodColors
import kotlinx.coroutines.launch

/** Lo que muestra la ficha, venga de un producto (alta) o de una entrada del diario (edición). */
private data class FoodDetail(
    val barcode: String?,
    val name: String,
    val brand: String?,
    val nutrients: Nutrients,
    val servingSizeG: Double?,
    val servingLabel: String?,
    val source: DataSource?,
    val sourceNote: String?,
    val product: Product?,
)

private sealed interface LoadState {
    data object Loading : LoadState
    data object NotFound : LoadState
    data class Failed(val message: String) : LoadState
    data class Ready(val detail: FoodDetail, val initialMeal: MealType, val initialUnit: QuantityUnit, val initialAmount: Double) : LoadState
}

/**
 * Ficha de producto, en alta o en edición de una entrada (docs/design.md §2.4).
 * [today] devuelve la fecha ISO (`YYYY-MM-DD`) del día en que se añade la entrada.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    navigator: Navigator,
    route: Route.ProductDetail,
    repository: ProductRepository,
    diaryStore: DiaryStore,
    today: () -> String,
) {
    var state by remember(route) { mutableStateOf<LoadState>(LoadState.Loading) }
    var reloadKey by remember(route) { mutableStateOf(0) }

    LaunchedEffect(route, reloadKey) {
        state = LoadState.Loading
        state = try {
            load(route, repository, diaryStore)
        } catch (e: Exception) {
            LoadState.Failed(e.message ?: "No se pudo cargar el producto")
        }
    }

    val ready = state as? LoadState.Ready
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Producto") },
                navigationIcon = {
                    IconButton(
                        onClick = { navigator.pop() },
                        modifier = Modifier.semantics { contentDescription = "Atrás" },
                    ) { Text("←", style = MaterialTheme.typography.titleLarge) }
                },
                actions = {
                    val barcode = ready?.detail?.barcode
                    if (barcode != null) {
                        IconButton(
                            onClick = { navigator.push(Route.CustomFood(barcode, route.meal)) },
                            modifier = Modifier.semantics { contentDescription = "Copiar como alimento propio" },
                        ) { Text("✎", style = MaterialTheme.typography.titleLarge) }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                LoadState.Loading -> CenteredMessage("Cargando…")
                LoadState.NotFound -> CenteredMessage("Producto no encontrado", "Volver") { navigator.pop() }
                is LoadState.Failed -> CenteredMessage(s.message, "Reintentar") { reloadKey++ }
                is LoadState.Ready -> DetailContent(navigator, route, s, diaryStore, today)
            }
        }
    }
}

private suspend fun load(route: Route.ProductDetail, repository: ProductRepository, diaryStore: DiaryStore): LoadState {
    if (route.entryId != null) {
        val entry = diaryStore.find(route.entryId) ?: return LoadState.NotFound
        val detail = FoodDetail(
            barcode = entry.barcode,
            name = entry.name,
            brand = null,
            nutrients = entry.nutrients,
            servingSizeG = entry.servingSizeG,
            servingLabel = null,
            source = null,
            sourceNote = "Valores guardados al registrar la entrada",
            product = null,
        )
        return LoadState.Ready(detail, entry.meal, entry.quantity.unit, entry.quantity.amount)
    }
    val barcode = route.barcode ?: return LoadState.NotFound
    return when (val result = repository.getByBarcode(barcode)) {
        ProductResult.NotFound -> LoadState.NotFound
        is ProductResult.Failure -> LoadState.Failed("Sin conexión o error al consultar el producto")
        is ProductResult.Found -> {
            val p = result.product
            val note = when {
                result.stale -> "datos guardados, puede que estén desactualizados"
                result.fromCache -> "datos guardados en el dispositivo"
                else -> null
            }
            val detail = FoodDetail(
                barcode = p.barcode, name = p.name, brand = p.brand, nutrients = p.nutrients,
                servingSizeG = p.servingSizeG, servingLabel = p.servingLabel,
                source = DataSource.OpenFoodFacts, sourceNote = note, product = p,
            )
            val servings = p.servingSizeG != null
            LoadState.Ready(
                detail, route.meal,
                if (servings) QuantityUnit.SERVINGS else QuantityUnit.GRAMS,
                if (servings) 1.0 else 100.0,
            )
        }
    }
}

@Composable
private fun CenteredMessage(text: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().padding(Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterVertically),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null) OutlinedButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun DetailContent(
    navigator: Navigator,
    route: Route.ProductDetail,
    ready: LoadState.Ready,
    diaryStore: DiaryStore,
    today: () -> String,
) {
    val detail = ready.detail
    val editing = route.entryId != null
    val scope = rememberCoroutineScope()

    var meal by remember(ready) { mutableStateOf(ready.initialMeal) }
    var unit by remember(ready) { mutableStateOf(ready.initialUnit) }
    var amountText by remember(ready) { mutableStateOf(formatGrams(ready.initialAmount).replace(".", "")) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val servingG = detail.servingSizeG
    val amount = parseNumber(amountText)
    val quantity = quantityOrNull(amount, unit)
    val grams = quantity?.let { gramsFor(it.amount, it.unit, servingG) }
    val total = grams?.let { detail.nutrients.scaledTo(it) }
    val reasons = detail.product?.dataWarnings()?.reasons().orEmpty()

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(detail.name, style = MaterialTheme.typography.headlineSmall)
                    val meta = listOfNotNull(
                        detail.brand,
                        detail.servingLabel?.let { "porción $it" },
                    ).joinToString(" · ")
                    if (meta.isNotEmpty() || detail.barcode != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            if (meta.isNotEmpty()) {
                                Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            detail.barcode?.let {
                                Text(it, style = EanTextStyle.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    detail.source?.let { SourceChip(it) }
                }
            }
            if (reasons.isNotEmpty()) {
                item {
                    DoubtfulDataBanner(
                        reasons = reasons,
                        onCorrect = detail.barcode?.let { code -> { navigator.push(Route.CustomFood(code, route.meal)) } },
                    )
                }
            }
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            MealType.entries.forEach { m ->
                                FilterChip(selected = meal == m, onClick = { meal = m }, label = { Text(m.label) })
                            }
                        }
                        QuantitySelector(
                            unit = unit,
                            onUnitChange = { newUnit ->
                                // Mantiene los gramos equivalentes al cambiar de unidad.
                                val g = grams
                                if (g != null && newUnit != unit) {
                                    val converted = when (newUnit) {
                                        QuantityUnit.GRAMS -> g
                                        QuantityUnit.SERVINGS -> servingG?.let { g / it }
                                    }
                                    if (converted != null) amountText = formatGrams(converted).replace(".", "")
                                }
                                unit = newUnit
                            },
                            amountText = amountText,
                            onAmountTextChange = { amountText = it },
                            servingsEnabled = servingG != null,
                        )
                        if (servingG != null) {
                            val label = detail.servingLabel?.let { " ($it)" }.orEmpty()
                            Text(
                                "1 porción = ${formatGrams(servingG)} g$label",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                if (grams != null) "Total · ${formatGrams(grams)} g" else "Total",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                total?.kcal?.let { "${formatKcal(it)} kcal" } ?: "Sin dato",
                                style = MaterialTheme.typography.displaySmall,
                            )
                        }
                        val colors = MaterialTheme.easyFoodColors
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            MacroCell("Proteína", total?.proteinG, colors.macroProtein, Modifier.weight(1f))
                            MacroCell("Hidratos", total?.carbsG, colors.macroCarbs, Modifier.weight(1f))
                            MacroCell("Grasa", total?.fatG, colors.macroFat, Modifier.weight(1f))
                        }
                    }
                }
            }
            item { NutritionTable("Por 100 g", detail.nutrients.toRows()) }
            if (servingG != null) {
                item { NutritionTable("Por porción · ${formatGrams(servingG)} g", detail.nutrients.perServingRows(servingG)) }
            }
            item {
                val source = detail.source?.label
                val text = listOfNotNull(source?.let { "Fuente: $it" }, detail.sourceNote).joinToString(" · ")
                if (text.isNotEmpty()) {
                    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            val q = quantity ?: return@Button
                            saving = true
                            error = null
                            scope.launch {
                                try {
                                    if (editing) {
                                        diaryStore.update(route.entryId!!, meal, q)
                                    } else {
                                        diaryStore.add(
                                            NewDiaryEntry(
                                                date = today(),
                                                meal = meal,
                                                barcode = detail.barcode,
                                                name = detail.name,
                                                quantity = q,
                                                servingSizeG = servingG,
                                                nutrients = detail.nutrients,
                                            ),
                                            createdAtMillis = nowMillis(),
                                        )
                                    }
                                    navigator.popToDiary()
                                } catch (e: Exception) {
                                    error = "No se pudo guardar: ${e.message ?: "error desconocido"}"
                                    saving = false
                                }
                            }
                        },
                        enabled = quantity != null && !saving && (unit == QuantityUnit.GRAMS || servingG != null),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) { Text(if (editing) "Guardar cambios" else "Añadir a ${meal.label}") }
                    if (editing) {
                        OutlinedButton(
                            onClick = {
                                saving = true
                                scope.launch {
                                    try {
                                        diaryStore.delete(route.entryId!!)
                                        navigator.popToDiary()
                                    } catch (e: Exception) {
                                        error = "No se pudo borrar: ${e.message ?: "error desconocido"}"
                                        saving = false
                                    }
                                }
                            },
                            enabled = !saving,
                            modifier = Modifier.height(48.dp).semantics { contentDescription = "Borrar entrada" },
                        ) { Text("Borrar") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MacroCell(label: String, grams: Double?, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MacroDot(color)
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = Spacing.xs))
        }
        Text(
            grams?.let { "${formatGrams(it)} g" } ?: "Sin dato",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** Instante actual en ms; aislado para no depender de una API de reloj de plataforma. */
private fun nowMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
