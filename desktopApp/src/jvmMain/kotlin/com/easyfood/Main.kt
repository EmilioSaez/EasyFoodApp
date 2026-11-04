package com.easyfood

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.easyfood.data.OpenFoodFactsClient
import com.easyfood.data.DiaryStore
import com.easyfood.data.ProductRepository
import com.easyfood.data.SqlDelightDiaryStore
import com.easyfood.data.SqlDelightProductStore
import com.easyfood.db.EasyFoodDatabase
import com.easyfood.ui.App
import java.io.File

/** Abre la BD en `~/.easyfood/easyfood.db` (creando el esquema la primera vez). */
private fun createStores(): Pair<ProductRepository, DiaryStore> {
    val dir = File(System.getProperty("user.home"), ".easyfood").apply { mkdirs() }
    val dbFile = File(dir, "easyfood.db")
    val isNew = !dbFile.exists()
    val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
    if (isNew) EasyFoodDatabase.Schema.create(driver)
    val database = EasyFoodDatabase(driver)
    val repository = ProductRepository(
        remote = OpenFoodFactsClient(),
        local = SqlDelightProductStore(database.productQueries),
        nowMillis = System::currentTimeMillis,
    )
    return repository to SqlDelightDiaryStore(database.diaryEntryQueries)
}

fun main() = application {
    val (repository, diaryStore) = remember { createStores() }
    Window(onCloseRequest = ::exitApplication, title = "EasyFood") {
        App(repository, diaryStore, today = { java.time.LocalDate.now().toString() })
    }
}
