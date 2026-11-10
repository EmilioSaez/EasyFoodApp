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
import androidx.compose.ui.unit.sp
import app.luxion.fitarchiveapp.data.DiaryStore
import app.luxion.fitarchiveapp.data.FavoriteKeys
import app.luxion.fitarchiveapp.data.FavoriteStore
import app.luxion.fitarchiveapp.data.CustomFoodStore
import app.luxion.fitarchiveapp.data.ProductRepository
import app.luxion.fitarchiveapp.data.generic.GenericFoodFormat
import app.luxion.fitarchiveapp.data.ProductResult
import app.luxion.fitarchiveapp.data.RefreshResult
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.NewDiaryEntry
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Product
import app.luxion.fitarchiveapp.model.ProductCorrection
import app.luxion.fitarchiveapp.model.QuantityUnit
import app.luxion.fitarchiveapp.model.dataWarnings
import app.luxion.fitarchiveapp.ui.components.DataSource
import app.luxion.fitarchiveapp.ui.components.CorrectedChip
import app.luxion.fitarchiveapp.ui.components.DoubtfulChip
import app.luxion.fitarchiveapp.ui.components.DoubtfulDataBanner
import app.luxion.fitarchiveapp.ui.components.StackedMacroBar
import app.luxion.fitarchiveapp.ui.components.MacroDot
import app.luxion.fitarchiveapp.ui.components.NutritionTable
import app.luxion.fitarchiveapp.ui.components.QuantitySelector
import app.luxion.fitarchiveapp.ui.components.SourceChip
import app.luxion.fitarchiveapp.ui.components.formatGrams
import app.luxion.fitarchiveapp.ui.components.formatKcal
import app.luxion.fitarchiveapp.ui.components.label
import app.luxion.fitarchiveapp.ui.components.parseNumber
import app.luxion.fitarchiveapp.ui.components.toRows
import app.luxion.fitarchiveapp.ui.components.EmptyState
import app.luxion.fitarchiveapp.ui.components.FavoriteStar
import app.luxion.fitarchiveapp.ui.components.EmptyStateAction
import app.luxion.fitarchiveapp.ui.components.EmptyStateTone
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.navigation.Route
import app.luxion.fitarchiveapp.ui.theme.EanTextStyle
import app.luxion.fitarchiveapp.ui.theme.Spacing
import app.luxion.fitarchiveapp.ui.theme.easyFoodColors
import kotlinx.coroutines.CancellationException
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
    /** Id del alimento propio, si la ficha es de uno (permite editarlo). */
    val customFoodId: Long? = null,
    /** Seguimiento del refresco desde Open Food Facts (solo productos de OFF). */
    val refreshedAtMillis: Long? = null,
    val changeSummary: String? = null,
    val changedAtMillis: Long? = null,
) {
    /** Código de barras real para mostrar o copiar; la clave interna de un genérico (`generic:<id>`) no lo es. */
    val displayBarcode: String? get() = barcode?.takeUnless { it.startsWith(GenericFoodFormat.KEY_PREFIX) }
}

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
    customFoods: CustomFoodStore,
    favoriteStore: FavoriteStore,
    today: () -> String,
    mealChoices: MealChoices,
) {
    var state by remember(route) { mutableStateOf<LoadState>(LoadState.Loading) }
    var reloadKey by remember(route) { mutableStateOf(0) }
    var refreshing by remember(route) { mutableStateOf(false) }
    var refreshMessage by remember(route) { mutableStateOf<String?>(null) }

    LaunchedEffect(route, reloadKey) {
        // Al recargar tras un refresco se mantiene la ficha en pantalla (no parpadea ni pierde la cantidad escrita).
        if (state !is LoadState.Ready) state = LoadState.Loading
        state = try {
            load(route, repository, diaryStore, customFoods, mealChoices.defaultMeal())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            LoadState.Failed("No se pudo cargar el producto.")
        }
    }

    val ready = state as? LoadState.Ready
    // Clave de favorito de la ficha: la de navegación (EAN, `generic:<id>` o `custom:<id>`); una entrada del diario de
    // un alimento propio no guarda su id, así que no tiene estrella.
    val favoriteKey = ready?.detail?.let { d -> d.customFoodId?.let(FavoriteKeys::custom) ?: d.barcode }
    var favorite by remember(favoriteKey) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val refreshProduct: () -> Unit = refresh@{
        val code = ready?.detail?.barcode ?: return@refresh
        if (refreshing) return@refresh
        refreshing = true
        refreshMessage = null
        scope.launch {
            try {
                refreshMessage = refreshMessageFor(repository.refresh(code))
                reloadKey++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                refreshMessage = "No se pudo actualizar. Inténtalo de nuevo."
            } finally {
                refreshing = false
            }
        }
    }
    LaunchedEffect(favoriteKey) {
        favorite = try {
            favoriteKey != null && favoriteKey in favoriteStore.keys()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }
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
                    if (favoriteKey != null) {
                        FavoriteStar(
                            favorite = favorite,
                            onToggle = {
                                val target = !favorite
                                scope.launch {
                                    try {
                                        favoriteStore.setFavorite(favoriteKey, target, kotlin.time.Clock.System.now().toEpochMilliseconds())
                                        favorite = target
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        // Sin poder guardar, la estrella se queda como estaba.
                                    }
                                }
                            },
                            foodName = ready?.detail?.name.orEmpty(),
                        )
                    }
                    val barcode = ready?.detail?.displayBarcode
                    val customId = ready?.detail?.customFoodId
                    if (customId != null) {
                        IconButton(
                            onClick = { navigator.push(Route.CustomFood(meal = route.meal, foodId = customId)) },
                            modifier = Modifier.semantics { contentDescription = "Editar alimento propio" },
                        ) { Text("✎", style = MaterialTheme.typography.titleLarge) }
                    } else if (barcode != null) {
                        IconButton(
                            onClick = {
                                val d = ready?.detail ?: return@IconButton
                                val prefill = customFoodFormStateOf(d.name, d.servingLabel, d.servingSizeG, d.nutrients)
                                navigator.push(Route.CustomFood(barcode, route.meal, prefill = prefill, brand = d.brand))
                            },
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
                LoadState.NotFound -> EmptyState(
                    icon = "?",
                    title = "Producto no encontrado",
                    message = "No hay datos para este producto. Puede que se haya borrado.",
                    tone = EmptyStateTone.Warning,
                    primaryAction = EmptyStateAction("Volver") { navigator.pop() },
                    modifier = Modifier.fillMaxSize(),
                )
                is LoadState.Failed -> EmptyState(
                    icon = "!",
                    title = "Sin conexión",
                    message = s.message,
                    tone = EmptyStateTone.Warning,
                    primaryAction = EmptyStateAction("Reintentar") { reloadKey++ },
                    secondaryAction = EmptyStateAction("Volver") { navigator.pop() },
                    modifier = Modifier.fillMaxSize(),
                )
                is LoadState.Ready -> DetailContent(
                    navigator, route, s, repository, diaryStore, today, mealChoices,
                    refreshing = refreshing, refreshMessage = refreshMessage, onRefresh = refreshProduct,
                    onProductChanged = { reloadKey++ },
                )
            }
        }
    }
}

private suspend fun load(
    route: Route.ProductDetail,
    repository: ProductRepository,
    diaryStore: DiaryStore,
    customFoods: CustomFoodStore,
    defaultMeal: MealType,
): LoadState {
    if (route.entryId != null) {
        val entry = diaryStore.find(route.entryId) ?: return LoadState.NotFound
        val detail = FoodDetail(
            barcode = entry.barcode,
            name = entry.name,
            brand = null,
            nutrients = entry.nutrients,
            servingSizeG = entry.servingSizeG,
            servingLabel = null,
            source = entry.dataSource(),
            sourceNote = "Valores guardados al registrar la entrada",
            product = null,
        )
        return LoadState.Ready(detail, entry.meal, entry.quantity.unit, entry.quantity.amount)
    }
    val barcode = route.barcode ?: return LoadState.NotFound
    val meal = route.meal ?: defaultMeal
    if (barcode.startsWith(CUSTOM_FOOD_KEY_PREFIX)) return loadCustomFood(barcode, meal, customFoods)
    return when (val result = repository.getByBarcode(barcode)) {
        ProductResult.NotFound -> LoadState.NotFound
        is ProductResult.Failure -> LoadState.Failed("Open Food Facts no responde y no hay una copia guardada. Comprueba la conexión.")
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
                source = if (p.barcode.startsWith(GenericFoodFormat.KEY_PREFIX)) DataSource.Generic else DataSource.OpenFoodFacts,
                sourceNote = note, product = p,
                refreshedAtMillis = result.refreshedAtMillis, changeSummary = result.changeSummary,
                changedAtMillis = result.changedAtMillis,
            )
            val servings = p.servingSizeG != null
            LoadState.Ready(
                detail, meal,
                if (servings) QuantityUnit.SERVINGS else QuantityUnit.GRAMS,
                if (servings) 1.0 else 100.0,
            )
        }
    }
}

/** Ficha de un alimento propio: se guarda en el diario sin código de barras (así se reconoce como «Propio»). */
private suspend fun loadCustomFood(key: String, meal: MealType, customFoods: CustomFoodStore): LoadState {
    val id = key.removePrefix(CUSTOM_FOOD_KEY_PREFIX).toLongOrNull() ?: return LoadState.NotFound
    val food = customFoods.find(id) ?: return LoadState.NotFound
    val servings = food.servingSizeG != null
    val detail = FoodDetail(
        barcode = null, name = food.name, brand = food.brand, nutrients = food.nutrients,
        servingSizeG = food.servingSizeG, servingLabel = food.servingLabel,
        source = DataSource.Custom, sourceNote = null, product = null, customFoodId = id,
    )
    return LoadState.Ready(
        detail, meal,
        if (servings) QuantityUnit.SERVINGS else QuantityUnit.GRAMS,
        if (servings) 1.0 else 100.0,
    )
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
    repository: ProductRepository,
    diaryStore: DiaryStore,
    today: () -> String,
    mealChoices: MealChoices,
    refreshing: Boolean,
    refreshMessage: String?,
    onRefresh: () -> Unit,
    onProductChanged: () -> Unit,
) {
    val detail = ready.detail
    val editing = route.entryId != null
    val scope = rememberCoroutineScope()

    var meal by remember(ready) { mutableStateOf(ready.initialMeal) }
    var unit by remember(ready) { mutableStateOf(ready.initialUnit) }
    var amountText by remember(ready) { mutableStateOf(formatGrams(ready.initialAmount).replace(".", "")) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var correctionBusy by remember { mutableStateOf(false) }
    var correctionError by remember { mutableStateOf<String?>(null) }

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
                    if (meta.isNotEmpty() || detail.displayBarcode != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            if (meta.isNotEmpty()) {
                                Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            detail.displayBarcode?.let {
                                Text(it, style = EanTextStyle.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (detail.source != null || reasons.isNotEmpty() || detail.product?.isCorrected == true) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                            detail.source?.let { SourceChip(it) }
                            if (reasons.isNotEmpty()) DoubtfulChip()
                            if (detail.product?.isCorrected == true) CorrectedChip()
                        }
                    }
                }
            }
            if (reasons.isNotEmpty()) {
                item {
                    DoubtfulDataBanner(
                        reasons = reasons,
                        onCorrect = detail.displayBarcode?.let { code -> { navigator.push(Route.CustomFood(code, route.meal)) } },
                    )
                }
            }
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
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
                                // Sora 44 (display de §1.7, un punto por debajo de displayMedium).
                                style = MaterialTheme.typography.displayMedium.copy(fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-1.5).sp),
                            )
                        }
                        StackedMacroBar(total?.proteinG ?: 0.0, total?.carbsG ?: 0.0, total?.fatG ?: 0.0)
                    }
                }
            }
            item { NutritionTable("Por 100 g", detail.nutrients.toRows()) }
            if (servingG != null) {
                item { NutritionTable("Por porción · ${formatGrams(servingG)} g", detail.nutrients.perServingRows(servingG)) }
            }
            if (!editing && detail.source == DataSource.OpenFoodFacts && detail.product != null) {
                item { RefreshSection(detail, refreshing, refreshMessage, onRefresh) }
            }
            detail.product?.let { product ->
                item {
                    ProductCorrectionSection(
                        product = product,
                        busy = correctionBusy,
                        error = correctionError,
                        onSave = { nutrients ->
                            correctionBusy = true
                            correctionError = null
                            scope.launch {
                                try {
                                    repository.correct(ProductCorrection(product.barcode, nutrients, nowMillis()))
                                    onProductChanged()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    correctionError = "No se pudo guardar la corrección. Inténtalo de nuevo."
                                } finally {
                                    correctionBusy = false
                                }
                            }
                        },
                        onRevert = {
                            correctionBusy = true
                            correctionError = null
                            scope.launch {
                                try {
                                    repository.revertCorrection(product.barcode)
                                    onProductChanged()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    correctionError = "No se pudo volver al original. Inténtalo de nuevo."
                                } finally {
                                    correctionBusy = false
                                }
                            }
                        },
                    )
                }
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
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    mealChoices.options(ready.initialMeal).forEach { m ->
                        FilterChip(selected = meal == m, onClick = { meal = m }, label = { Text(m.label) })
                    }
                }
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
                                                date = route.date ?: today(),
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
                                    error = "No se pudo guardar. Inténtalo de nuevo."
                                    saving = false
                                }
                            }
                        },
                        enabled = quantity != null && !saving && (unit == QuantityUnit.GRAMS || servingG != null),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        val kcalText = total?.kcal?.let { " · ${formatKcal(it)} kcal" }.orEmpty()
                        Text(if (editing) "Guardar cambios$kcalText" else "Añadir$kcalText")
                    }
                    if (editing) {
                        OutlinedButton(
                            onClick = {
                                saving = true
                                scope.launch {
                                    try {
                                        diaryStore.delete(route.entryId!!)
                                        navigator.popToDiary()
                                    } catch (e: Exception) {
                                        error = "No se pudo borrar. Inténtalo de nuevo."
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

/** «Actualizado hoy / hace N días», último cambio nutricional relevante y botón de refresco manual. */
@Composable
private fun RefreshSection(detail: FoodDetail, refreshing: Boolean, message: String?, onRefresh: () -> Unit) {
    val now = nowMillis()
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(
                detail.refreshedAtMillis?.let { "Datos de Open Food Facts ${updatedAgo(now, it)}" }
                    ?: "Datos de Open Food Facts",
                style = MaterialTheme.typography.bodyMedium,
            )
            val changed = detail.changedAtMillis
            if (detail.changeSummary != null && changed != null) {
                Text(
                    "Cambió ${updatedAgo(now, changed).removePrefix("actualizado ")}: ${detail.changeSummary}. " +
                        "Tus entradas anteriores del diario conservan los valores con los que las registraste.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            OutlinedButton(
                onClick = onRefresh,
                enabled = !refreshing,
                modifier = Modifier.semantics { contentDescription = "Actualizar desde Open Food Facts" },
            ) { Text(if (refreshing) "Actualizando…" else "Actualizar datos") }
        }
    }
}

@Composable
internal fun MacroCell(label: String, grams: Double?, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
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

/** Mensaje al usuario tras un refresco manual. */
internal fun refreshMessageFor(result: RefreshResult): String = when (result) {
    is RefreshResult.Updated ->
        result.change?.let { "Valores actualizados: $it (por 100 g)." } ?: "Datos al día: sin cambios nutricionales relevantes."
    RefreshResult.Rejected -> "Open Food Facts devuelve datos incompletos; se conservan los que tenías."
    RefreshResult.NotFound -> "El producto ya no está en Open Food Facts; se conservan los datos guardados."
    RefreshResult.Skipped -> "Este producto no se puede actualizar."
    is RefreshResult.Failure -> "Sin conexión con Open Food Facts. Inténtalo más tarde."
}

/** «actualizado hoy» / «actualizado hace 3 días» respecto a [now]. */
internal fun updatedAgo(now: Long, then: Long): String {
    val days = ((now - then) / 86_400_000L).coerceAtLeast(0)
    return when (days) {
        0L -> "actualizado hoy"
        1L -> "actualizado ayer"
        else -> "actualizado hace $days días"
    }
}

/** Instante actual en ms; aislado para no depender de una API de reloj de plataforma. */
private fun nowMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
