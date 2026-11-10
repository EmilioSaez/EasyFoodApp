package app.luxion.fitarchiveapp

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.ai.AiModule
import app.luxion.fitarchiveapp.ai.JvmAiKeyVault
import app.luxion.fitarchiveapp.ai.JvmAiPreferences
import app.luxion.fitarchiveapp.backup.BackupFiles
import app.luxion.fitarchiveapp.backup.MigrationBackup
import app.luxion.fitarchiveapp.backup.MigrationFailedException
import app.luxion.fitarchiveapp.backup.fileBackupFiles
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
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.export.SqlDelightImportTarget
import app.luxion.fitarchiveapp.ui.App
import app.luxion.fitarchiveapp.ui.screens.MigrationErrorScreen
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/** Lee `PRAGMA user_version`: la versión de esquema de la BD (0 si nunca se ha guardado). */
private fun JdbcSqliteDriver.schemaVersion(): Long = executeQuery(
    null, "PRAGMA user_version", { c -> QueryResult.Value(if (c.next().value) c.getLong(0) else null) }, 0,
).value ?: 0L

/**
 * Abre la BD en `~/.easyfood/easyfood.db`. La versión del esquema se guarda en `PRAGMA user_version`: una BD nueva
 * crea el esquema y una existente se migra desde su versión. Las bases anteriores a este control no la tenían
 * (versión 0) y ya estaban en la v4, así que se migran desde ahí (las migraciones desde la 4 son idempotentes).
 *
 * Antes de migrar se copia el archivo en `~/.easyfood/backups` (se conservan las 3 últimas); si la migración falla, se
 * restaura esa copia y se lanza [MigrationFailedException].
 */
private fun openDatabase(files: BackupFiles): EasyFoodDatabase {
    val dbFile = File(DATA_DIR, "easyfood.db")
    val isNew = !dbFile.exists()
    val target = EasyFoodDatabase.Schema.version
    val migration = MigrationBackup(files, nowMillis = System::currentTimeMillis, today = { LocalDate.now().toString() })
    return migration.openProtected(target) {
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        try {
            if (isNew) {
                EasyFoodDatabase.Schema.create(driver)
            } else {
                val current = driver.schemaVersion().takeIf { it > 0 } ?: 4L
                if (current < target) EasyFoodDatabase.Schema.migrate(driver, current, target)
            }
            driver.execute(null, "PRAGMA user_version = $target", 0)
        } catch (e: Exception) {
            driver.close() // En Windows el archivo no se puede reemplazar mientras siga abierto.
            throw e
        }
        EasyFoodDatabase(driver)
    }
}

private val DATA_DIR: File = File(System.getProperty("user.home"), ".easyfood").apply { mkdirs() }

fun main() = application {
    val backupFiles = remember { fileBackupFiles(File(DATA_DIR, "backups").path, File(DATA_DIR, "easyfood.db").path) }
    val opened = remember { runCatching { openDatabase(backupFiles) } }
    val db = opened.getOrNull()
    if (db == null) {
        val error = opened.exceptionOrNull()
        Window(onCloseRequest = ::exitApplication, title = "EasyFood") {
            MigrationErrorScreen((error as? MigrationFailedException)?.message ?: "Error al abrir la base de datos: ${error?.message}")
        }
        return@application
    }
    val repository = remember(db) {
        ProductRepository(
            remote = OpenFoodFactsClient(),
            local = SqlDelightProductStore(db.productQueries),
            nowMillis = System::currentTimeMillis,
        )
    }
    // Clave y ajustes de IA en ~/.easyfood/ai/, aparte de easyfood.db (que es lo que se exporta).
    val aiModule = remember { AiModule.create(JvmAiKeyVault.default(), JvmAiPreferences.default(), today = { LocalDate.now().toString() }) }
    Window(onCloseRequest = ::exitApplication, title = "EasyFood") {
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
            waterStore = remember(db) { SqlDelightWaterStore(db.waterQueries) },
            templateStore = remember(db) { SqlDelightMealTemplateStore(db.mealTemplateQueries) },
            importTarget = remember(db) {
                SqlDelightImportTarget(db.productQueries, db.diaryEntryQueries, db.customFoodQueries, db.weightEntryQueries, db.recipeQueries, db.settingsQueries, db.favoriteFoodQueries, waterQueries = db.waterQueries, templateQueries = db.mealTemplateQueries, exerciseQueries = db.exerciseQueries)
            },
            today = { LocalDate.now().toString() },
            nowMinutes = { LocalTime.now().let { it.hour * 60 + it.minute } },
            backgroundRefresh = true,
            genericSeeder = remember(db) {
                GenericFoodSeeder(GenericFoodImporter(db.productQueries), nowMillis = System::currentTimeMillis)
            },
            ai = aiModule,
            backupFiles = backupFiles,
        )
    }
}
