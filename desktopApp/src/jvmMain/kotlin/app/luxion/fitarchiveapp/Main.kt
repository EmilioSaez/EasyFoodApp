package app.luxion.fitarchiveapp

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.data.OpenFoodFactsClient
import app.luxion.fitarchiveapp.data.ProductRepository
import app.luxion.fitarchiveapp.data.SqlDelightCustomFoodStore
import app.luxion.fitarchiveapp.data.SqlDelightDiaryStore
import app.luxion.fitarchiveapp.data.SqlDelightGoalStore
import app.luxion.fitarchiveapp.data.SqlDelightProductSearchStore
import app.luxion.fitarchiveapp.data.SqlDelightProductStore
import app.luxion.fitarchiveapp.data.SqlDelightWeightStore
import app.luxion.fitarchiveapp.data.generic.GenericFoodImporter
import app.luxion.fitarchiveapp.data.generic.GenericFoodSeeder
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.export.SqlDelightImportTarget
import app.luxion.fitarchiveapp.ui.App
import java.io.File
import java.time.LocalDate

/** Abre la BD en `~/.easyfood/easyfood.db` (creando el esquema la primera vez y migrándolo después). */
private fun openDatabase(): EasyFoodDatabase {
    val dir = File(System.getProperty("user.home"), ".easyfood").apply { mkdirs() }
    val dbFile = File(dir, "easyfood.db")
    val isNew = !dbFile.exists()
    val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
    if (isNew) {
        EasyFoodDatabase.Schema.create(driver)
    } else {
        // Las bases existentes no llevan versión de esquema: se aplica la migración 3 -> 4 (peso), que es
        // idempotente (CREATE TABLE IF NOT EXISTS), así que no importa si la tabla ya existía.
        EasyFoodDatabase.Schema.migrate(driver, 3, 4)
    }
    return EasyFoodDatabase(driver)
}

fun main() = application {
    val db = remember { openDatabase() }
    val repository = remember(db) {
        ProductRepository(
            remote = OpenFoodFactsClient(),
            local = SqlDelightProductStore(db.productQueries),
            nowMillis = System::currentTimeMillis,
        )
    }
    Window(onCloseRequest = ::exitApplication, title = "EasyFood") {
        App(
            repository = repository,
            diaryStore = remember(db) { SqlDelightDiaryStore(db.diaryEntryQueries) },
            goalStore = remember(db) { SqlDelightGoalStore(db.dailyGoalQueries) },
            searchStore = remember(db) { SqlDelightProductSearchStore(db.productSearchQueries) },
            customFoodStore = remember(db) { SqlDelightCustomFoodStore(db.customFoodQueries) },
            weightStore = remember(db) { SqlDelightWeightStore(db.weightEntryQueries) },
            importTarget = remember(db) {
                SqlDelightImportTarget(db.productQueries, db.diaryEntryQueries, db.customFoodQueries, db.weightEntryQueries)
            },
            today = { LocalDate.now().toString() },
            genericSeeder = remember(db) {
                GenericFoodSeeder(GenericFoodImporter(db.productQueries), nowMillis = System::currentTimeMillis)
            },
        )
    }
}
