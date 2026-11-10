package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TopAppBar
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.data.CustomFoodStore
import app.luxion.fitarchiveapp.data.DiaryStore
import app.luxion.fitarchiveapp.data.FavoriteStore
import app.luxion.fitarchiveapp.data.MealTemplateStore
import app.luxion.fitarchiveapp.data.addTemplate
import app.luxion.fitarchiveapp.model.MealTemplate
import app.luxion.fitarchiveapp.data.ProductSearchStore
import app.luxion.fitarchiveapp.ui.components.EmptyState
import app.luxion.fitarchiveapp.ui.components.EmptyStateAction
import app.luxion.fitarchiveapp.ui.components.FoodResultRow
import app.luxion.fitarchiveapp.ui.components.label
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.navigation.Route
import app.luxion.fitarchiveapp.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Pestaña Alimentos / añadir alimento (docs/design.md §2.2): el buscador compartido ([FoodSearch]) sin filtros más
 * accesos rápidos «Recientes · Favoritos · Plantillas · Escáner · Alimento propio». Sin texto se ven los recientes del diario, los
 * favoritos o las plantillas de comida (docs/design.md §2.17), según el acceso elegido; cada fila lleva su estrella de favorito. La comida solo
 * viene preseleccionada ([Route.Add.meal]) si se llega desde el «+» de una sección del Diario; si no, se elige en la ficha.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodScreen(
    navigator: Navigator,
    route: Route.Add,
    searchStore: ProductSearchStore? = null,
    customFoods: CustomFoodStore? = null,
    diaryStore: DiaryStore? = null,
    favoriteStore: FavoriteStore? = null,
    templateStore: MealTemplateStore? = null,
    today: (() -> String)? = null,
    mealChoices: MealChoices? = null,
) {
    var query by remember { mutableStateOf("") }
    // null = aún cargando; lista vacía = sin recientes.
    var recents by remember { mutableStateOf<List<FoodResult>?>(null) }
    var mode by remember { mutableStateOf(if (route.templates) QuickMode.Templates else QuickMode.Recents) }
    // Plantillas de comida: null = aún cargando. Solo hay acceso si la app aporta almacén, fecha de hoy y comidas.
    val templatesEnabled = templateStore != null && today != null && mealChoices != null
    var templates by remember { mutableStateOf<List<MealTemplate>?>(null) }
    var addingTemplate by remember { mutableStateOf<MealTemplate?>(null) }
    var templateError by remember { mutableStateOf<String?>(null) }
    // null = aún cargando; lista vacía = sin favoritos. [favoriteVersion] fuerza la recarga tras marcar o desmarcar.
    var favorites by remember { mutableStateOf<List<FoodResult>?>(null) }
    var favoriteKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var favoriteVersion by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(favoriteStore, customFoods, favoriteVersion) {
        try {
            val keys = favoriteStore?.keys().orEmpty()
            favoriteKeys = keys.toSet()
            favorites = favoriteFoodResults(keys, favoriteStore?.products().orEmpty(), customFoods?.all().orEmpty())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            favorites = emptyList()
        }
    }
    val toggleFavorite: ((FoodResult) -> Unit)? = favoriteStore?.let { store ->
        { r: FoodResult ->
            scope.launch {
                try {
                    store.setFavorite(r.key, r.key !in favoriteKeys, kotlin.time.Clock.System.now().toEpochMilliseconds())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Sin poder guardar, la estrella se queda como estaba al recargar.
                }
                favoriteVersion++
            }
        }
    }

    LaunchedEffect(templateStore, mode) {
        if (templateStore == null || mode != QuickMode.Templates) return@LaunchedEffect
        templates = try {
            templateStore.all()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }
    // Escribe la plantilla en el diario y vuelve a él; si falla, avisa sin salir.
    val addTemplate: (MealTemplate, String, app.luxion.fitarchiveapp.model.MealType) -> Unit = { template, date, meal ->
        if (diaryStore != null) {
            scope.launch {
                try {
                    diaryStore.addTemplate(template, date, meal, kotlin.time.Clock.System.now().toEpochMilliseconds())
                    addingTemplate = null
                    navigator.popToDiary()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    templateError = "No se pudo añadir la plantilla. Inténtalo de nuevo."
                }
            }
        }
    }

    LaunchedEffect(diaryStore, customFoods) {
        recents = try {
            val entries = diaryStore?.recentFoods(RECENT_FOODS_LIMIT).orEmpty()
            recentFoodResults(entries, customFoods?.all().orEmpty())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(
                        onClick = { navigator.pop() },
                        modifier = Modifier.semantics { contentDescription = "Atrás" },
                    ) { Text("←", style = MaterialTheme.typography.titleLarge) }
                },
            )
        },
    ) { padding ->
        FoodSearch(
            query = query,
            onQueryChange = { query = it },
            searchStore = searchStore,
            customFoods = customFoods,
            onPick = { navigator.push(Route.ProductDetail(barcode = it.result.key, meal = route.meal)) },
            modifier = Modifier.padding(padding),
            emptyPrimary = EmptyStateAction("Crear alimento propio") { navigator.push(Route.CustomFood(meal = route.meal)) },
            emptySecondary = EmptyStateAction("Escanear código") { navigator.push(Route.Scanner(route.meal)) },
            header = {
                Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s)) {
                    Text("Alimentos", style = MaterialTheme.typography.headlineLarge)
                    route.meal?.let {
                        Text(
                            "Añadiendo a ${it.label}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            quickActions = {
                QuickActions(
                    mode = mode,
                    showTemplates = templatesEnabled,
                    onRecent = { query = ""; mode = QuickMode.Recents; if (route.templates) navigator.replace(route.copy(templates = false)) },
                    onFavorites = { query = ""; mode = QuickMode.Favorites; if (route.templates) navigator.replace(route.copy(templates = false)) },
                    onTemplates = {
                        query = ""; templateError = null; mode = QuickMode.Templates
                        if (!route.templates) navigator.replace(route.copy(templates = true))
                    },
                    onScan = { navigator.push(Route.Scanner(route.meal)) },
                    onCustom = { navigator.push(Route.CustomFood(meal = route.meal)) },
                    modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
                )
            },
            idleContent = {
                val onPick = { r: FoodResult -> navigator.push(Route.ProductDetail(barcode = r.key, meal = route.meal)) }
                if (mode == QuickMode.Templates && templatesEnabled) {
                    TemplateListSection(
                        templates = templates,
                        onAdd = { t -> templateError = null; addTemplate(t, today!!(), route.meal ?: mealChoices!!.defaultMeal()) },
                        onChoose = { t -> templateError = null; addingTemplate = t },
                        onEdit = { t -> navigator.push(Route.TemplateEdit(t.id)) },
                        onNew = { navigator.push(Route.TemplateEdit()) },
                        error = templateError.takeIf { addingTemplate == null },
                    )
                } else if (mode == QuickMode.Favorites) {
                    FoodListSection(
                        foods = favorites,
                        header = "FAVORITOS",
                        emptyIcon = "☆",
                        emptyTitle = "Aún no tienes favoritos",
                        emptyMessage = "Toca la estrella ☆ de un alimento, en los resultados o en su ficha, para tenerlo aquí.",
                        favoriteKeys = favoriteKeys,
                        onToggleFavorite = toggleFavorite,
                        onPick = onPick,
                    )
                } else {
                    FoodListSection(
                        foods = recents,
                        header = "ÚLTIMOS ALIMENTOS",
                        emptyIcon = "◷",
                        emptyTitle = "Aún no hay recientes",
                        emptyMessage = "Los alimentos que apuntes en el diario aparecerán aquí. Busca uno o usa el escáner.",
                        favoriteKeys = favoriteKeys,
                        onToggleFavorite = toggleFavorite,
                        onPick = onPick,
                    )
                }
            },
            favoriteKeys = favoriteKeys,
            onToggleFavorite = toggleFavorite,
        )
    }

    addingTemplate?.let { t ->
        AddTemplateDialog(
            template = t,
            today = today!!(),
            initialMeal = route.meal ?: mealChoices!!.defaultMeal(),
            mealChoices = mealChoices!!,
            error = templateError,
            onDismiss = { addingTemplate = null; templateError = null },
            onConfirm = { date, meal -> addTemplate(t, date, meal) },
        )
    }
}

/** Qué se ve bajo el buscador cuando no hay texto. */
private enum class QuickMode { Recents, Favorites, Templates }

/** Lista de alimentos bajo el buscador (recientes o favoritos); [foods] `null` = aún cargando. */
@Composable
private fun FoodListSection(
    foods: List<FoodResult>?,
    header: String,
    emptyIcon: String,
    emptyTitle: String,
    emptyMessage: String,
    favoriteKeys: Set<String>,
    onToggleFavorite: ((FoodResult) -> Unit)?,
    onPick: (FoodResult) -> Unit,
) {
    when {
        foods == null -> Unit
        foods.isEmpty() -> EmptyState(
            icon = emptyIcon,
            title = emptyTitle,
            message = emptyMessage,
            modifier = Modifier.fillMaxWidth(),
        )
        else -> LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text(
                    "$header · ${foods.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
                )
            }
            items(foods, key = { it.key }) { r ->
                FoodResultRow(
                    name = r.name,
                    detail = r.detail,
                    source = r.source,
                    kcalPer100g = r.kcalPer100g,
                    onClick = { onPick(r) },
                    favorite = onToggleFavorite?.let { r.key in favoriteKeys },
                    onToggleFavorite = { onToggleFavorite?.invoke(r) },
                )
            }
        }
    }
}

/**
 * Recientes · Favoritos · Plantillas · Escáner · Alimento propio. «Escáner» abre [ScannerScreen], que con cámara escanea y siempre ofrece la
 * entrada manual de EAN; en escritorio, sin cámara, solo queda esa entrada manual.
 */
@Composable
private fun QuickActions(
    mode: QuickMode,
    showTemplates: Boolean,
    onRecent: () -> Unit,
    onFavorites: () -> Unit,
    onTemplates: () -> Unit,
    onScan: () -> Unit,
    onCustom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tall = Modifier.height(72.dp)
    val primary = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
    val neutral = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    val pad = PaddingValues(horizontal = 4.dp)
    @Composable
    fun RowScope.Tile(label: String, onClick: () -> Unit, colors: ButtonColors) {
        Button(
            onClick = onClick,
            modifier = tall.weight(1f),
            shape = MaterialTheme.shapes.medium,
            colors = colors,
            contentPadding = pad,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Tile("Recientes", onRecent, if (mode == QuickMode.Recents) primary else neutral)
        Tile("Favoritos", onFavorites, if (mode == QuickMode.Favorites) primary else neutral)
        if (showTemplates) Tile("Plantillas", onTemplates, if (mode == QuickMode.Templates) primary else neutral)
        Tile("Escáner", onScan, neutral)
        Tile("Alimento propio", onCustom, neutral)
    }
}
