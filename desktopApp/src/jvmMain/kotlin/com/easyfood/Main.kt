package com.easyfood

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.easyfood.data.OpenFoodFactsClient
import com.easyfood.data.ProductRepository
import com.easyfood.data.SqlDelightDiaryStore
import com.easyfood.data.SqlDelightGoalStore
import com.easyfood.data.SqlDelightProductStore
import com.easyfood.db.EasyFoodDatabase
import com.easyfood.ui.App
import java.io.File
import java.time.LocalDate

/** Abre la BD en `~/.easyfood/easyfood.db` (creando el esquema la primera vez). */
private fun createDatabase(): EasyFoodDatabase {
    val dir = File(System.getProperty("user.home"), ".easyfood").apply { mkdirs() }
    val dbFile = File(dir, "easyfood.db")
    val isNew = !dbFile.exists()
    val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
    if (isNew) EasyFoodDatabase.Schema.create(driver)
    return EasyFoodDatabase(driver)
}

fun main() = application {
    val database = remember { createDatabase() }
    val repository = remember {
        ProductRepository(
            remote = OpenFoodFactsClient(),
            local = SqlDelightProductStore(database.productQueries),
            nowMillis = System::currentTimeMillis,
        )
    }
    Window(onCloseRequest = ::exitApplication, title = "EasyFood") {
        App(
            repository,
            remember { SqlDelightDiaryStore(database.diaryEntryQueries) },
            remember { SqlDelightGoalStore(database.dailyGoalQueries) },
            today = { LocalDate.now().toString() },
        )
    }
}
