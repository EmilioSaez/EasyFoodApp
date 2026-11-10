package app.luxion.fitarchiveapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.luxion.fitarchiveapp.ai.AiModule
import app.luxion.fitarchiveapp.ai.IosAiPreferences
import app.luxion.fitarchiveapp.ai.KeychainAiKeyVault
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
import app.luxion.fitarchiveapp.data.SqlDelightExerciseStore
import app.luxion.fitarchiveapp.data.SqlDelightWaterStore
import app.luxion.fitarchiveapp.data.SqlDelightWeightStore
import app.luxion.fitarchiveapp.data.generic.GenericFoodImporter
import app.luxion.fitarchiveapp.data.generic.GenericFoodSeeder
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.export.SqlDelightImportTarget
import app.luxion.fitarchiveapp.ui.App
import app.luxion.fitarchiveapp.ui.screens.MigrationErrorScreen
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSDate
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIViewController

/** Fecha de hoy en formato ISO (`YYYY-MM-DD`). */
private fun todayIso(): String = NSDateFormatter().apply { dateFormat = "yyyy-MM-dd" }.stringFromDate(NSDate())

/** Hora local actual en minutos desde medianoche. */
private fun nowMinutes(): Int {
    val parts = NSCalendar.currentCalendar.components(NSCalendarUnitHour or NSCalendarUnitMinute, fromDate = NSDate())
    return (parts.hour * 60 + parts.minute).toInt()
}

private fun directory(kind: ULong): String? =
    (NSSearchPathForDirectoriesInDomains(kind, NSUserDomainMask, true).firstOrNull() as? String)

/** Las copias van a Application Support, que entra en la copia de seguridad del dispositivo (iCloud / Finder). */
private fun backupDirectory(): String = "${directory(NSApplicationSupportDirectory) ?: ""}/EasyFood/backups"

/**
 * Ruta del archivo de la base. SQLDelight la elige el driver nativo según su versión (Documents o Application
 * Support, en la raíz o en `databases/`), así que se busca dónde existe ya; si es una instalación nueva se usa la
 * habitual. Si no se encuentra, la copia previa a migrar simplemente no se hace.
 */
private fun databasePath(): String {
    val bases = listOfNotNull(directory(NSDocumentDirectory), directory(NSApplicationSupportDirectory))
    val candidates = bases.flatMap { listOf("$it/databases/easyfood.db", "$it/easyfood.db") }
    return candidates.firstOrNull { NSFileManager.defaultManager.fileExistsAtPath(it) }
        ?: "${bases.firstOrNull() ?: ""}/databases/easyfood.db"
}

/** Punto de entrada para iOS: devuelve el `UIViewController` con la UI compartida (lo usa `iosApp`). */
@Suppress("FunctionName")
fun MainViewController(): UIViewController = ComposeUIViewController {
    val backupFiles = remember { fileBackupFiles(backupDirectory(), databasePath()) }
    val opened = remember {
        runCatching {
            MigrationBackup(backupFiles, { (NSDate().timeIntervalSince1970 * 1000).toLong() }, ::todayIso)
                .openProtected(EasyFoodDatabase.Schema.version) {
                    EasyFoodDatabase(NativeSqliteDriver(EasyFoodDatabase.Schema, "easyfood.db")).also {
                        // Una consulta fuerza la migración aquí, donde se puede deshacer si falla.
                        it.settingsQueries.selectMealsPerDay().executeAsOneOrNull()
                    }
                }
        }
    }
    val db = opened.getOrNull()
    if (db == null) {
        val error = opened.exceptionOrNull()
        MigrationErrorScreen((error as? MigrationFailedException)?.message ?: "Error al abrir la base de datos: ${error?.message}")
        return@ComposeUIViewController
    }
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
            waterStore = remember(db) { SqlDelightWaterStore(db.waterQueries) },
            templateStore = remember(db) { SqlDelightMealTemplateStore(db.mealTemplateQueries) },
            exerciseStore = remember(db) { SqlDelightExerciseStore(db.exerciseQueries) },
            importTarget = remember(db) {
                SqlDelightImportTarget(db.productQueries, db.diaryEntryQueries, db.customFoodQueries, db.weightEntryQueries, db.recipeQueries, db.settingsQueries, db.favoriteFoodQueries, waterQueries = db.waterQueries, templateQueries = db.mealTemplateQueries, exerciseQueries = db.exerciseQueries)
            },
            today = ::todayIso,
            nowMinutes = ::nowMinutes,
            backgroundRefresh = true,
            genericSeeder = remember(db) {
                GenericFoodSeeder(
                    GenericFoodImporter(db.productQueries),
                    nowMillis = { (NSDate().timeIntervalSince1970 * 1000).toLong() },
                )
            },
            ai = remember { AiModule.create(KeychainAiKeyVault(), IosAiPreferences(), today = ::todayIso) },
            backupFiles = backupFiles,
        )
    }
}
