package app.luxion.easyfoodapp.android

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.luxion.easyfoodapp.backup.BackupFiles
import app.luxion.easyfoodapp.backup.MigrationBackup
import app.luxion.easyfoodapp.db.EasyFoodDatabase
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import java.time.LocalDate

/** Nombre del archivo de la base de la app (lo comparten la actividad y el widget). */
const val DATABASE_NAME = "easyfood.db"

/**
 * Base de datos de la app. Usa un SQLite empaquetado (requery): el del sistema Android no incluye el
 * módulo FTS5, que necesita la búsqueda de alimentos (`ProductFts`). [name] `null` = en memoria.
 *
 * Con [backups] (y [name] no nulo), si el esquema va a cambiar de versión se copia antes el archivo de la base y la
 * migración se aplica en el acto, no en la primera consulta: si falla, se restaura la copia y se lanza
 * [app.luxion.easyfoodapp.backup.MigrationFailedException].
 */
fun createDatabase(context: Context, name: String? = DATABASE_NAME, backups: BackupFiles? = null): EasyFoodDatabase {
    fun open(): Pair<AndroidSqliteDriver, EasyFoodDatabase> {
        val driver = AndroidSqliteDriver(
            EasyFoodDatabase.Schema,
            context.applicationContext,
            name,
            factory = RequerySQLiteOpenHelperFactory(),
        )
        return driver to EasyFoodDatabase(driver)
    }
    if (backups == null || name == null) return open().second

    val migration = MigrationBackup(backups, nowMillis = System::currentTimeMillis, today = { LocalDate.now().toString() })
    return migration.openProtected(EasyFoodDatabase.Schema.version) {
        val (driver, database) = open()
        try {
            // El driver abre (y migra) la base en la primera consulta: se fuerza aquí para poder deshacer si falla.
            database.settingsQueries.selectMealsPerDay().executeAsOneOrNull()
        } catch (e: Exception) {
            driver.close()
            throw e
        }
        database
    }
}
