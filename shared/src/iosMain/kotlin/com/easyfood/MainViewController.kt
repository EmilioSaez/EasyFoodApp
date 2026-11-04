package com.easyfood

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.easyfood.data.OpenFoodFactsClient
import com.easyfood.data.ProductRepository
import com.easyfood.data.CustomFoodStore
import com.easyfood.data.SqlDelightCustomFoodStore
import com.easyfood.data.SqlDelightProductStore
import com.easyfood.db.EasyFoodDatabase
import com.easyfood.ui.App
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIViewController

private fun createDatabase(): EasyFoodDatabase =
    EasyFoodDatabase(NativeSqliteDriver(EasyFoodDatabase.Schema, "easyfood.db"))

private fun createRepository(database: EasyFoodDatabase): ProductRepository = ProductRepository(
    remote = OpenFoodFactsClient(),
    local = SqlDelightProductStore(database.productQueries),
    nowMillis = { (NSDate().timeIntervalSince1970 * 1000).toLong() },
)

/** Punto de entrada para iOS: devuelve el `UIViewController` con la UI compartida (lo usa `iosApp`). */
@Suppress("FunctionName")
fun MainViewController(): UIViewController = ComposeUIViewController {
    val database = remember { createDatabase() }
    val repository = remember { createRepository(database) }
    val customFoodStore: CustomFoodStore = remember { SqlDelightCustomFoodStore(database.customFoodQueries) }
    Box(Modifier.safeDrawingPadding()) { App(repository, customFoodStore) }
}
