package app.luxion.fitarchiveapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.ai.AiModule
import app.luxion.fitarchiveapp.data.CustomFoodStore
import app.luxion.fitarchiveapp.data.ExerciseStore
import app.luxion.fitarchiveapp.data.FavoriteStore
import app.luxion.fitarchiveapp.data.DiaryStore
import app.luxion.fitarchiveapp.data.GoalStore
import app.luxion.fitarchiveapp.data.ProductRefresher
import app.luxion.fitarchiveapp.data.ProductRepository
import app.luxion.fitarchiveapp.data.ProductSearchStore
import app.luxion.fitarchiveapp.data.MealTemplateStore
import app.luxion.fitarchiveapp.data.RecipeStore
import app.luxion.fitarchiveapp.data.SettingsStore
import app.luxion.fitarchiveapp.data.WaterStore
import app.luxion.fitarchiveapp.data.WeightStore
import app.luxion.fitarchiveapp.data.generic.GenericFoodSeeder
import app.luxion.fitarchiveapp.backup.BackupFiles
import app.luxion.fitarchiveapp.backup.BackupManager
import app.luxion.fitarchiveapp.export.DataExporter
import app.luxion.fitarchiveapp.export.DataImporter
import app.luxion.fitarchiveapp.export.ImportTarget
import app.luxion.fitarchiveapp.reminders.ReminderScheduler
import app.luxion.fitarchiveapp.reminders.ReminderSync
import app.luxion.fitarchiveapp.reminders.rememberReminderScheduler
import app.luxion.fitarchiveapp.ui.components.SeedProgressBanner
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.navigation.PlatformBackHandler
import app.luxion.fitarchiveapp.ui.navigation.Route
import app.luxion.fitarchiveapp.ui.screens.AddFoodScreen
import app.luxion.fitarchiveapp.ui.screens.AiScreen
import app.luxion.fitarchiveapp.ui.screens.AiSettingsScreen
import app.luxion.fitarchiveapp.ui.screens.CustomFoodScreen
import app.luxion.fitarchiveapp.ui.screens.DataPresenter
import app.luxion.fitarchiveapp.ui.screens.DataScreen
import app.luxion.fitarchiveapp.ui.screens.DiaryScreen
import app.luxion.fitarchiveapp.ui.screens.GoalsScreen
import app.luxion.fitarchiveapp.ui.screens.MealChoices
import app.luxion.fitarchiveapp.ui.screens.MealsSetupScreen
import app.luxion.fitarchiveapp.ui.screens.ProductDetailScreen
import app.luxion.fitarchiveapp.ui.screens.QuickEntryScreen
import app.luxion.fitarchiveapp.ui.screens.RecipeDetailScreen
import app.luxion.fitarchiveapp.ui.screens.RecipeEditScreen
import app.luxion.fitarchiveapp.ui.screens.RemindersScreen
import app.luxion.fitarchiveapp.ui.screens.RecipesScreen
import app.luxion.fitarchiveapp.ui.screens.ScannerScreen
import app.luxion.fitarchiveapp.ui.screens.TemplateEditScreen
import app.luxion.fitarchiveapp.ui.screens.SummaryScreen
import app.luxion.fitarchiveapp.ui.screens.WeightScreen
import app.luxion.fitarchiveapp.ui.theme.EasyFoodTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.launch

/**
 * Raíz de la app: tema + navegación entre pantallas (docs/design.md §4).
 * [today] devuelve la fecha ISO (`YYYY-MM-DD`) del día actual.
 * [nowMinutes] devuelve la hora local actual en minutos desde medianoche (para preseleccionar la comida).
 * [genericSeeder], si se indica, carga la base genérica en segundo plano al arrancar y muestra su progreso.
 * [ai] agrupa la clave y los ajustes de la IA (guardados fuera de la BD exportable); sin él, la IA queda sin clave.
 * [backgroundRefresh] activa el refresco oportunista de los productos de Open Food Facts usados (ver [ProductRefresher]).
 * [backupFiles], si se indica, activa la copia de seguridad periódica (y su sección en «Datos») en esa carpeta.
 */
@Composable
fun App(
    repository: ProductRepository,
    diaryStore: DiaryStore,
    goalStore: GoalStore,
    searchStore: ProductSearchStore,
    customFoodStore: CustomFoodStore,
    weightStore: WeightStore,
    recipeStore: RecipeStore,
    settingsStore: SettingsStore,
    favoriteStore: FavoriteStore,
    waterStore: WaterStore? = null,
    templateStore: MealTemplateStore? = null,
    exerciseStore: ExerciseStore? = null,
    importTarget: ImportTarget,
    today: () -> String,
    nowMinutes: () -> Int,
    genericSeeder: GenericFoodSeeder? = null,
    ai: AiModule? = null,
    backgroundRefresh: Boolean = false,
    reminderScheduler: ReminderScheduler = rememberReminderScheduler(),
    backupFiles: BackupFiles? = null,
) {
    EasyFoodTheme {
        Surface(Modifier.fillMaxSize()) {
            val navigator = remember { Navigator() }
            val aiModule = remember(ai) { ai ?: AiModule.inMemory(today) }
            val dataPresenter = remember(importTarget, backupFiles) {
                val exporter = DataExporter(importTarget) { kotlin.time.Clock.System.now().toEpochMilliseconds() }
                DataPresenter(
                    exporter = exporter,
                    importer = DataImporter(importTarget),
                    backups = backupFiles?.let {
                        BackupManager(
                            files = it,
                            export = exporter::exportToJson,
                            nowMillis = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
                            today = today,
                        )
                    },
                )
            }
            if (backgroundRefresh) {
                LaunchedEffect(repository) {
                    delay(STARTUP_REFRESH_DELAY_MS) // tras el arranque y la carga de la base genérica
                    try {
                        ProductRefresher(repository, pause = 500.milliseconds).run()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // el refresco es solo una mejora: sin red o con la BD ocupada, la app sigue igual
                    }
                }
            }
            dataPresenter.backups?.let { AutoBackup(it) }
            PlatformBackHandler(enabled = navigator.canGoBack) { navigator.pop() }
            val meals = rememberMealsSetting(settingsStore, navigator.current)
            var remindersRevision by remember { mutableIntStateOf(0) }
            // Reprograma los avisos al arrancar, al cambiar de pantalla (p. ej. tras registrar una comida) y al editar ajustes.
            // Con los recordatorios apagados no vuelve a leer la base en cada cambio de pantalla (salvo al salir de «Datos»,
            // que puede haber importado ajustes).
            val reminders = remember { ReminderSyncState() }
            LaunchedEffect(navigator.current, remindersRevision, meals.count) {
                val route = navigator.current
                val leftData = reminders.lastRoute == Route.Data
                reminders.lastRoute = route
                if (reminders.enabled == false && reminders.revision == remindersRevision && !leftData) return@LaunchedEffect
                try {
                    reminders.enabled = ReminderSync(settingsStore, diaryStore, weightStore, waterStore, reminderScheduler)
                        .sync(today(), nowMinutes())
                    reminders.revision = remindersRevision
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Los avisos son un extra: un fallo al programarlos no debe afectar a la app.
                }
            }

            // En escritorio, las mismas pantallas en una columna de 600 dp como máximo, centrada.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 600.dp).fillMaxSize()) {
                    if (genericSeeder != null) GenericSeedProgress(genericSeeder)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        val mealChoices = MealChoices(meals.count, nowMinutes)
                        if (!meals.loaded) {
                            // Leyendo los ajustes: sin pantalla todavía para no parpadear entre la configuración y el Diario.
                        } else if (meals.needsSetup) {
                            MealsSetupScreen(onConfirm = meals::choose, error = meals.error, saving = meals.saving)
                        } else when (val route = navigator.current) {
                            Route.Diary -> DiaryScreen(navigator, diaryStore, goalStore, weightStore, today(), meals.count, waterStore, templateStore, exerciseStore)
                            is Route.Add -> AddFoodScreen(
                                navigator,
                                route,
                                searchStore = searchStore,
                                customFoods = customFoodStore,
                                diaryStore = diaryStore,
                                favoriteStore = favoriteStore,
                                templateStore = templateStore,
                                today = today,
                                mealChoices = mealChoices,
                            )
                            is Route.QuickEntry -> QuickEntryScreen(navigator, route, diaryStore, today, mealChoices)
                            is Route.Scanner -> ScannerScreen(navigator, route, repository)
                            is Route.CustomFood -> CustomFoodScreen(navigator, route, customFoodStore, route.foodId)
                            is Route.ProductDetail -> ProductDetailScreen(navigator, route, repository, diaryStore, customFoodStore, favoriteStore, today, mealChoices)
                            Route.Goals -> GoalsScreen(navigator, goalStore, today, meals.count, meals::choose, waterStore, exerciseStore)
                            Route.Weight -> WeightScreen(navigator, weightStore, today)
                            Route.Recipes -> RecipesScreen(navigator, recipeStore)
                            is Route.RecipeDetail -> RecipeDetailScreen(navigator, route, recipeStore, diaryStore, today, mealChoices)
                            is Route.RecipeEdit -> RecipeEditScreen(navigator, route, recipeStore, searchStore, customFoodStore)
                            is Route.TemplateEdit -> if (templateStore != null) {
                                TemplateEditScreen(navigator, route, templateStore, searchStore, customFoodStore)
                            }
                            Route.Reminders -> RemindersScreen(navigator, settingsStore, reminderScheduler, meals.count) { remindersRevision++ }
                            Route.Summary -> SummaryScreen(navigator, diaryStore, goalStore, weightStore, today)
                            Route.Data -> DataScreen(navigator, dataPresenter, today)
                            Route.Ai -> AiScreen(navigator, aiModule)
                            Route.AiSettings -> AiSettingsScreen(navigator, aiModule, today)
                        }
                    }
                }
            }
        }
    }
}

/** Último estado conocido de los recordatorios, para no releer la base en cada cambio de pantalla si están apagados. */
private class ReminderSyncState {
    var enabled: Boolean? = null
    var revision: Int = -1
    var lastRoute: Route? = null
}

private const val STARTUP_REFRESH_DELAY_MS = 15_000L
/** Copia automática: una a los pocos segundos de abrir la app si toca y, si sigue abierta, se vuelve a mirar cada pocas horas. */
@Composable
private fun AutoBackup(backups: BackupManager) {
    LaunchedEffect(backups) {
        delay(STARTUP_DELAY_MILLIS)
        while (true) {
            backups.runIfDue()
            delay(RECHECK_MILLIS)
        }
    }
}

private const val STARTUP_DELAY_MILLIS = 5_000L
private const val RECHECK_MILLIS = 6L * 60 * 60 * 1000

/** Lanza la carga inicial de la base genérica (fuera del hilo de UI) y pinta su avance. */
@Composable
private fun GenericSeedProgress(seeder: GenericFoodSeeder) {
    LaunchedEffect(seeder) { seeder.ensureSeeded() }
    val state by seeder.state.collectAsState()
    SeedProgressBanner(state)
}

/**
 * Ajuste «comidas al día». Se lee al arrancar y de nuevo en cada cambio de pantalla (una importación puede
 * haberlo fijado). Si la lectura falla no se bloquea la app con la pantalla de primera vez: se usa el preset
 * por defecto.
 */
@Stable
private class MealsSetting(private val store: SettingsStore, private val scope: CoroutineScope) {
    var loaded by mutableStateOf(false)
        private set
    var count by mutableStateOf<Int?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var saving by mutableStateOf(false)
        private set
    private var readFailed = false

    val needsSetup: Boolean get() = loaded && count == null && !readFailed

    suspend fun reload() {
        try {
            count = store.mealsPerDay()
            readFailed = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            readFailed = true
        }
        loaded = true
    }

    /** Guarda la elección; el estado solo cambia si se ha podido guardar. */
    fun choose(value: Int) {
        if (saving) return
        saving = true
        error = null
        scope.launch {
            try {
                store.setMealsPerDay(value)
                count = value
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "No se pudo guardar. Inténtalo de nuevo."
            } finally {
                saving = false
            }
        }
    }
}

@Composable
private fun rememberMealsSetting(store: SettingsStore, route: Route): MealsSetting {
    val scope = rememberCoroutineScope()
    val setting = remember(store) { MealsSetting(store, scope) }
    LaunchedEffect(setting, route) { setting.reload() }
    return setting
}
