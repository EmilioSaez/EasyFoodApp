package app.luxion.fitarchiveapp.android.widget

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import app.luxion.fitarchiveapp.android.DATABASE_NAME
import app.luxion.fitarchiveapp.android.createDatabase
import app.luxion.fitarchiveapp.data.SqlDelightDiaryStore
import app.luxion.fitarchiveapp.data.SqlDelightGoalStore
import app.luxion.fitarchiveapp.data.SqlDelightWaterStore
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.domain.DayWidgetSnapshot
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

/** Lee de la base de la app lo que enseña el widget. Solo lee: nunca crea ni migra la base. */
internal object WidgetData {
    private var database: EasyFoodDatabase? = null

    /** `null` si la base aún no existe o su versión no es la actual (la app debe abrirse antes para migrarla con copia). */
    @Synchronized
    fun load(context: Context): DayWidgetSnapshot? = try {
        val db = database ?: run {
            if (!schemaIsCurrent(context)) return null
            createDatabase(context, DATABASE_NAME).also { database = it }
        }
        runBlocking {
            val today = LocalDate.now().toString()
            val water = SqlDelightWaterStore(db.waterQueries)
            DayWidgetSnapshot.of(
                entries = SqlDelightDiaryStore(db.diaryEntryQueries).entriesForDate(today),
                goals = SqlDelightGoalStore(db.dailyGoalQueries).get(),
                waterMl = water.total(today),
                waterGoalMl = water.goalMl(),
            )
        }
    } catch (e: Exception) {
        null
    }

    private fun schemaIsCurrent(context: Context): Boolean {
        val file = context.getDatabasePath(DATABASE_NAME)
        if (!file.exists()) return false
        return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use {
            it.version.toLong() == EasyFoodDatabase.Schema.version
        }
    }
}
