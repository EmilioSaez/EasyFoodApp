package app.luxion.fitarchiveapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.luxion.fitarchiveapp.data.OpenFoodFactsClient
import app.luxion.fitarchiveapp.data.ProductRepository
import app.luxion.fitarchiveapp.data.SqlDelightCustomFoodStore
import app.luxion.fitarchiveapp.data.SqlDelightFavoriteStore
import app.luxion.fitarchiveapp.data.SqlDelightDiaryStore
import app.luxion.fitarchiveapp.data.SqlDelightGoalStore
import app.luxion.fitarchiveapp.data.SqlDelightProductSearchStore
import app.luxion.fitarchiveapp.data.SqlDelightProductStore
import app.luxion.fitarchiveapp.data.SqlDelightRecipeStore
import app.luxion.fitarchiveapp.data.SqlDelightSettingsStore
import app.luxion.fitarchiveapp.data.SqlDelightWeightStore
import app.luxion.fitarchiveapp.data.generic.GenericFoodImporter
import app.luxion.fitarchiveapp.data.generic.GenericFoodSeeder
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.export.SqlDelightImportTarget
import app.luxion.fitarchiveapp.ui.App
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIViewController

/** Fecha de hoy en formato ISO (`YYYY-MM-DD`). */
private fun todayIso(): String = NSDateFormatter().apply { dateFormat = "yyyy-MM-dd" }.stringFromDate(NSDate())

/** Hora local actual en minutos desde medianoche. */
private fun nowMinutes(): Int {
    val parts = NSCalendar.currentCalendar.components(NSCalendarUnitHour or NSCalendarUnitMinute, fromDate = NSDate())
    return (parts.hour * 60 + parts.minute).toInt()
}

/** Punto de entrada para iOS: devuelve el `UIViewController` con la UI compartida (lo usa `iosApp`). */
@Suppress("FunctionName")
fun MainViewController(): UIViewController = ComposeUIViewController {
    val db = remember { EasyFoodDatabase(NativeSqliteDriver(EasyFoodDatabase.Schema, "easyfood.db")) }
    val repository = remember(db) {
        ProductRepository(
            remote = OpenFoodFactsClient(),
            local = SqlDelightProductStore(db.productQueries),
            nowMillis = { (NSDate().timeIntervalSince1970 * 1000).toLong() },
        )
    }
    Box(Modifier.safeDrawingPadding()) {
        App(
            repository = repository,
            diaryStore = remember(db) { SqlDelightDiaryStore(db.diaryEntryQueries) },
            goalStore = remember(db) { SqlDelightGoalStore(db.dailyGoalQueries) },
            searchStore = remember(db) { SqlDelightProductSearchStore(db.productSearchQueries) },
            customFoodStore = remember(db) { SqlDelightCustomFoodStore(db.customFoodQueries) },
            weightStore = remember(db) { SqlDelightWeightStore(db.weightEntryQueries) },
            recipeStore = remember(db) { SqlDelightRecipeStore(db.recipeQueries) },
            settingsStore = remember(db) { SqlDelightSettingsStore(db.settingsQueries) },
            favoriteStore = remember(db) { SqlDelightFavoriteStore(db.favoriteFoodQueries) },
            importTarget = remember(db) {
                SqlDelightImportTarget(db.productQueries, db.diaryEntryQueries, db.customFoodQueries, db.weightEntryQueries, db.recipeQueries, db.settingsQueries, db.favoriteFoodQueries)
            },
            today = ::todayIso,
            nowMinutes = ::nowMinutes,
            genericSeeder = remember(db) {
                GenericFoodSeeder(
                    GenericFoodImporter(db.productQueries),
                    nowMillis = { (NSDate().timeIntervalSince1970 * 1000).toLong() },
                )
            },
        )
    }
}
