package app.luxion.easyfoodapp.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.luxion.easyfoodapp.ai.AiModule
import app.luxion.easyfoodapp.ai.AndroidAiPreferences
import app.luxion.easyfoodapp.ai.AndroidKeystoreKeyVault
import app.luxion.easyfoodapp.backup.MigrationFailedException
import app.luxion.easyfoodapp.backup.fileBackupFiles
import app.luxion.easyfoodapp.data.OpenFoodFactsClient
import app.luxion.easyfoodapp.data.ProductRepository
import app.luxion.easyfoodapp.data.SqlDelightCustomFoodStore
import app.luxion.easyfoodapp.data.SqlDelightFavoriteStore
import app.luxion.easyfoodapp.data.SqlDelightMealTemplateStore
import app.luxion.easyfoodapp.data.SqlDelightDiaryStore
import app.luxion.easyfoodapp.data.SqlDelightGoalStore
import app.luxion.easyfoodapp.data.SqlDelightProductSearchStore
import app.luxion.easyfoodapp.data.SqlDelightProductStore
import app.luxion.easyfoodapp.data.SqlDelightRecipeStore
import app.luxion.easyfoodapp.data.SqlDelightSettingsStore
import app.luxion.easyfoodapp.data.SqlDelightWaterStore
import app.luxion.easyfoodapp.data.SqlDelightWeightStore
import app.luxion.easyfoodapp.data.generic.GenericFoodImporter
import app.luxion.easyfoodapp.data.generic.GenericFoodSeeder
import app.luxion.easyfoodapp.data.supermarket.SupermarketImporter
import app.luxion.easyfoodapp.data.supermarket.SupermarketSeeder
import app.luxion.easyfoodapp.export.SqlDelightImportTarget
import app.luxion.easyfoodapp.android.widget.DailyWidget
import app.luxion.easyfoodapp.ui.App
import app.luxion.easyfoodapp.ui.navigation.Route
import app.luxion.easyfoodapp.ui.screens.MigrationErrorScreen
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    /** Copias de seguridad en el almacenamiento interno de la app (`files/backups`), junto a la base. */
    private val backupFiles by lazy {
        fileBackupFiles(File(filesDir, "backups").path, getDatabasePath(DATABASE_NAME).path)
    }
    private val database by lazy { createDatabase(applicationContext, DATABASE_NAME, backupFiles) }
    private val repository by lazy {
        ProductRepository(
            remote = OpenFoodFactsClient(),
            local = SqlDelightProductStore(database.productQueries),
            nowMillis = System::currentTimeMillis,
        )
    }
    private val diaryStore by lazy { SqlDelightDiaryStore(database.diaryEntryQueries) }
    private val goalStore by lazy { SqlDelightGoalStore(database.dailyGoalQueries) }
    private val weightStore by lazy { SqlDelightWeightStore(database.weightEntryQueries) }
    private val recipeStore by lazy { SqlDelightRecipeStore(database.recipeQueries) }
    private val settingsStore by lazy { SqlDelightSettingsStore(database.settingsQueries) }
    private val favoriteStore by lazy { SqlDelightFavoriteStore(database.favoriteFoodQueries) }
    private val templateStore by lazy { SqlDelightMealTemplateStore(database.mealTemplateQueries) }
    private val waterStore by lazy { SqlDelightWaterStore(database.waterQueries) }
    private val searchStore by lazy { SqlDelightProductSearchStore(database.productSearchQueries) }
    private val customFoodStore by lazy { SqlDelightCustomFoodStore(database.customFoodQueries) }
    private val importTarget by lazy {
        SqlDelightImportTarget(database.productQueries, database.diaryEntryQueries, database.customFoodQueries, database.weightEntryQueries, database.recipeQueries, database.settingsQueries, database.favoriteFoodQueries, waterQueries = database.waterQueries, templateQueries = database.mealTemplateQueries, exerciseQueries = database.exerciseQueries)
    }
    private val aiModule by lazy {
        AiModule.create(AndroidKeystoreKeyVault(applicationContext), AndroidAiPreferences(applicationContext), today = { LocalDate.now().toString() })
    }
    private val genericSeeder by lazy {
        GenericFoodSeeder(GenericFoodImporter(database.productQueries), nowMillis = System::currentTimeMillis)
    }

    private val supermarketSeeder by lazy {
        SupermarketSeeder(SupermarketImporter(database.productQueries), nowMillis = System::currentTimeMillis)
    }

    /** Destino pedido por el widget (o `null`); la app lo abre encima del Diario y lo olvida. */
    private var launchRoute: Route? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) launchRoute = routeFor(intent)
        // Abrir la base aquí (y no al pintar) permite mostrar un aviso si la actualización de la base falla.
        val failure = try {
            database
            null
        } catch (e: MigrationFailedException) {
            e.message
        } catch (e: Exception) {
            "Error al abrir la base de datos: ${e.message}"
        }
        if (failure != null) {
            setContent { Box(Modifier.safeDrawingPadding()) { MigrationErrorScreen(failure) } }
            return
        }
        setContent {
            Box(Modifier.safeDrawingPadding()) {
                App(
                    repository = repository,
                    diaryStore = diaryStore,
                    goalStore = goalStore,
                    searchStore = searchStore,
                    customFoodStore = customFoodStore,
                    weightStore = weightStore,
                    recipeStore = recipeStore,
                    settingsStore = settingsStore,
                    waterStore = waterStore,
                    templateStore = templateStore,
                    favoriteStore = favoriteStore,
                    importTarget = importTarget,
                    today = { LocalDate.now().toString() },
                    nowMinutes = { LocalTime.now().let { it.hour * 60 + it.minute } },
                    genericSeeder = genericSeeder,
                    supermarketSeeder = supermarketSeeder,
                    ai = aiModule,
                    backgroundRefresh = true,
                    backupFiles = backupFiles,
                    launchRoute = launchRoute,
                    onLaunchRouteHandled = { launchRoute = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeFor(intent)?.let { launchRoute = it }
    }

    /** Al salir de primer plano se refresca el widget: ya está registrado lo que se haya apuntado. */
    override fun onPause() {
        super.onPause()
        DailyWidget.requestRefresh(this)
    }

    private fun routeFor(intent: Intent?): Route? = when (intent?.getStringExtra(EXTRA_DESTINATION)) {
        DEST_QUICK -> Route.QuickEntry()
        // El «+ Vaso» y el agua viven en la tarjeta de agua del Diario.
        DEST_WATER, DEST_DIARY -> Route.Diary
        else -> null
    }

    companion object {
        /** Extra con el destino que pide el widget: [DEST_DIARY], [DEST_WATER] o [DEST_QUICK]. */
        const val EXTRA_DESTINATION = "app.luxion.easyfoodapp.android.DESTINATION"
        const val DEST_DIARY = "diary"
        const val DEST_WATER = "water"
        const val DEST_QUICK = "quick"
    }
}
