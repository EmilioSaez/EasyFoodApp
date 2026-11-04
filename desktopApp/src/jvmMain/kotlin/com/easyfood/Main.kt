package com.easyfood

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.easyfood.data.GoalStore
import com.easyfood.data.OpenFoodFactsClient
import com.easyfood.data.DiaryStore
import com.easyfood.data.ProductRepository
import com.easyfood.data.SqlDelightGoalStore
import com.easyfood.data.SqlDelightProductStore
import com.easyfood.db.EasyFoodDatabase
import com.easyfood.ui.App
import java.io.File
import java.time.LocalDate

/** Abre la BD en `~/.easyfood/easyfood.db` (creando el esquema la primera vez). */
private fun createStores(): Pair<ProductRepository, GoalStore> {
    val dir = File(System.getProperty("user.home"), ".easyfood").apply { mkdirs() }
    val dbFile = File(dir, "easyfood.db")
    val isNew = !dbFile.exists()
    val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
    if (isNew) EasyFoodDatabase.Schema.create(driver)
    val db = EasyFoodDatabase(driver)
    val repository = ProductRepository(
        remote = OpenFoodFactsClient(),
        local = SqlDelightProductStore(db.productQueries),
        nowMillis = System::currentTimeMillis,
    )
    return repository to SqlDelightGoalStore(db.dailyGoalQueries)
}

private fun createRepository(database: EasyFoodDatabase): ProductRepository = ProductRepository(
    remote = OpenFoodFactsClient(),
    local = SqlDelightProductStore(database.productQueries),
    nowMillis = System::currentTimeMillis,
)

fun main() = application {
    val (repository, goalStore) = remember { createStores() }
    Window(onCloseRequest = ::exitApplication, title = "EasyFood") {
        App(repository, goalStore)
    }
}
