package app.luxion.fitarchiveapp.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import app.luxion.fitarchiveapp.data.OpenFoodFactsClient
import app.luxion.fitarchiveapp.data.ProductRepository
import app.luxion.fitarchiveapp.data.SqlDelightCustomFoodStore
import app.luxion.fitarchiveapp.data.SqlDelightFavoriteStore
import app.luxion.fitarchiveapp.data.SqlDelightMealTemplateStore
import app.luxion.fitarchiveapp.data.SqlDelightDiaryStore
import app.luxion.fitarchiveapp.data.SqlDelightGoalStore
import app.luxion.fitarchiveapp.data.SqlDelightProductSearchStore
import app.luxion.fitarchiveapp.data.SqlDelightProductStore
import app.luxion.fitarchiveapp.data.SqlDelightRecipeStore
import app.luxion.fitarchiveapp.data.SqlDelightSettingsStore
import app.luxion.fitarchiveapp.data.SqlDelightWaterStore
import app.luxion.fitarchiveapp.data.SqlDelightWeightStore
import app.luxion.fitarchiveapp.data.generic.GenericFoodImporter
import app.luxion.fitarchiveapp.data.generic.GenericFoodSeeder
import app.luxion.fitarchiveapp.export.SqlDelightImportTarget
import app.luxion.fitarchiveapp.ui.App
import java.time.LocalDate
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    private val database by lazy { createDatabase(applicationContext) }
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
        SqlDelightImportTarget(database.productQueries, database.diaryEntryQueries, database.customFoodQueries, database.weightEntryQueries, database.recipeQueries, database.settingsQueries, database.favoriteFoodQueries, waterQueries = database.waterQueries, templateQueries = database.mealTemplateQueries)
    }
    private val genericSeeder by lazy {
        GenericFoodSeeder(GenericFoodImporter(database.productQueries), nowMillis = System::currentTimeMillis)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                )
            }
        }
    }
}
