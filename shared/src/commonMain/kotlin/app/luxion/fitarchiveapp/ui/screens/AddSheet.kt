package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.luxion.fitarchiveapp.data.CustomFoodStore
import app.luxion.fitarchiveapp.data.DiaryStore
import app.luxion.fitarchiveapp.data.FavoriteStore
import app.luxion.fitarchiveapp.data.MealTemplateStore
import app.luxion.fitarchiveapp.data.ProductSearchStore
import app.luxion.fitarchiveapp.data.RecipeStore
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.ui.components.EasyFoodFilterChip
import app.luxion.fitarchiveapp.ui.components.label
import app.luxion.fitarchiveapp.ui.navigation.AddSheetTarget
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Almacenes y reloj que necesita la hoja «Añadir»; los aporta `App`. Los `null` desactivan la parte que dependa de ellos. */
class AddSheetDeps(
    val searchStore: ProductSearchStore?,
    val customFoods: CustomFoodStore?,
    val diaryStore: DiaryStore?,
    val favoriteStore: FavoriteStore?,
    val templateStore: MealTemplateStore?,
    val recipeStore: RecipeStore?,
    val today: () -> String,
    val mealChoices: MealChoices,
)

/** Pestañas de listas de la hoja. */
enum class AddListMode(val label: String) {
    Recents("Recientes"),
    Favorites("Favoritos"),
    Templates("Plantillas"),
    Recipes("Recetas"),
}

/**
 * Estado compartido por los tres huecos de la hoja: texto buscado, comida elegida, pestaña de listas y favoritos.
 * [date] es el día al que se apunta todo lo que se añada desde la hoja (el de [AddSheetTarget.date], o hoy).
 */
@Stable
class AddSheetModel(val target: AddSheetTarget, val deps: AddSheetDeps, private val scope: CoroutineScope) {
    var query by mutableStateOf("")
    var meal: MealType by mutableStateOf(target.meal ?: deps.mealChoices.defaultMeal())
    var mode by mutableStateOf(AddListMode.Recents)
    val date: String = target.date ?: deps.today()

    /** `null` = aún cargando. */
    var favorites by mutableStateOf<List<FoodResult>?>(null)
        private set
    var favoriteKeys by mutableStateOf<Set<String>>(emptySet())
        private set
    /** Sube al marcar o desmarcar: la hoja relee los favoritos. */
    var favoriteVersion by mutableStateOf(0)
        private set

    val favoritesEnabled: Boolean get() = deps.favoriteStore != null

    suspend fun loadFavorites() {
        try {
            val keys = deps.favoriteStore?.keys().orEmpty()
            favoriteKeys = keys.toSet()
            favorites = favoriteFoodResults(keys, deps.favoriteStore?.products().orEmpty(), deps.customFoods?.all().orEmpty())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            favorites = emptyList()
        }
    }

    fun toggleFavorite(r: FoodResult) {
        val store = deps.favoriteStore ?: return
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

/**
 * Hoja «Añadir» (docs/design.md §2.22): la única puerta de entrada de comida. Se abre con `navigator.openAddSheet(meal, date)`
 * y la pinta `App`. Cabecera con la comida y el día, selector de comida, y tres huecos, cada uno en su fichero:
 * [AddSheetSearch] (buscador con resultados), [AddSheetActions] (escanear, foto del plato, etiqueta, entrada rápida) y
 * [AddSheetLists] (recientes, favoritos, plantillas, recetas). Con texto en el buscador se ven los resultados en lugar de
 * acciones y listas. Cualquier `navigator.push(...)` desde un hueco cierra la hoja.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(target: AddSheetTarget, navigator: Navigator, deps: AddSheetDeps) {
    val scope = rememberCoroutineScope()
    val model = remember(target) { AddSheetModel(target, deps, scope) }
    LaunchedEffect(model, model.favoriteVersion) { model.loadFavorites() }
    ModalBottomSheet(
        onDismissRequest = navigator::closeAddSheet,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier.padding(horizontal = Spacing.screen).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Añadir", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "a ${model.meal.label} · ${model.dayLabel()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = navigator::closeAddSheet,
                    modifier = Modifier.semantics { contentDescription = "Cerrar" },
                ) { Text("✕", style = MaterialTheme.typography.titleLarge) }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                deps.mealChoices.options(target.meal).forEach { m ->
                    EasyFoodFilterChip(selected = model.meal == m, onClick = { model.meal = m }, label = { Text(m.label) })
                }
            }
            AddSheetSearch(model, navigator)
            if (model.query.isBlank()) {
                AddSheetSuggestions(model, navigator)
                AddSheetActions(model, navigator)
                AddSheetLists(model, navigator)
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

/** «hoy», «ayer», «mañana» o «4 nov» respecto a hoy. */
internal fun AddSheetModel.dayLabel(): String {
    val today = deps.today()
    return when (date) {
        today -> "hoy"
        IsoDate.plusDays(today, -1) -> "ayer"
        IsoDate.plusDays(today, 1) -> "mañana"
        else -> formatDayMonth(date)
    }
}
