package app.luxion.fitarchiveapp.android

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.luxion.fitarchiveapp.backup.BackupFiles
import app.luxion.fitarchiveapp.backup.MigrationBackup
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import java.time.LocalDate

/**
 * Base de datos de la app. Usa un SQLite empaquetado (requery): el del sistema Android no incluye el
 * módulo FTS5, que necesita la búsqueda de alimentos (`ProductFts`). [name] `null` = en memoria.
 *
 * Con [backups] (y [name] no nulo), si el esquema va a cambiar de versión se copia antes el archivo de la base y la
 * migración se aplica en el acto, no en la primera consulta: si falla, se restaura la copia y se lanza
 * [app.luxion.fitarchiveapp.backup.MigrationFailedException].
 */
fun createDatabase(context: Context, name: String? = "easyfood.db", backups: BackupFiles? = null): EasyFoodDatabase {
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
