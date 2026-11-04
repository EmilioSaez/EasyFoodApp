package app.luxion.fitarchiveapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.luxion.fitarchiveapp.data.GoalStore
import app.luxion.fitarchiveapp.data.OpenFoodFactsClient
import app.luxion.fitarchiveapp.data.ProductRepository
import app.luxion.fitarchiveapp.data.SqlDelightGoalStore
import app.luxion.fitarchiveapp.data.SqlDelightProductStore
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.ui.App
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIViewController

private fun createStores(): Pair<ProductRepository, GoalStore> {
    val driver = NativeSqliteDriver(EasyFoodDatabase.Schema, "easyfood.db")
    val db = EasyFoodDatabase(driver)
    val repository = ProductRepository(
        remote = OpenFoodFactsClient(),
        local = SqlDelightProductStore(db.productQueries),
        nowMillis = { (NSDate().timeIntervalSince1970 * 1000).toLong() },
    )
    return repository to SqlDelightGoalStore(db.dailyGoalQueries)
}

/** Punto de entrada para iOS: devuelve el `UIViewController` con la UI compartida (lo usa `iosApp`). */
@Suppress("FunctionName")
fun MainViewController(): UIViewController = ComposeUIViewController {
    val (repository, goalStore) = remember { createStores() }
    Box(Modifier.safeDrawingPadding()) { App(repository, goalStore) }
}
