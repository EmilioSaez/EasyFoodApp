package app.luxion.fitarchiveapp.android

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory

/**
 * Base de datos de la app. Usa un SQLite empaquetado (requery): el del sistema Android no incluye el
 * módulo FTS5, que necesita la búsqueda de alimentos (`ProductFts`). [name] `null` = en memoria.
 */
fun createDatabase(context: Context, name: String? = "easyfood.db"): EasyFoodDatabase =
    EasyFoodDatabase(
        AndroidSqliteDriver(
            EasyFoodDatabase.Schema,
            context.applicationContext,
            name,
            factory = RequerySQLiteOpenHelperFactory(),
        ),
    )
