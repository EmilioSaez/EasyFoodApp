package app.luxion.fitarchiveapp.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.luxion.fitarchiveapp.data.SqlDelightGoalStore
import app.luxion.fitarchiveapp.data.SqlDelightProductSearchStore
import app.luxion.fitarchiveapp.data.generic.GenericFoodImporter
import app.luxion.fitarchiveapp.model.DailyGoals
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Con el SQLite del sistema, crear el esquema falla («no such module: fts5») y la app no carga el diario. */
@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    @Test
    fun schemaWithFts5CreatesAndSearchesOnTheDevice() = runBlocking {
        val db = createDatabase(InstrumentationRegistry.getInstrumentation().targetContext, name = null)
        val tsv = listOf(
            "#easyfood-generic-foods v1",
            "id\tname\tkcal\tprotein_g\tcarbs_g\tfat_g\tsaturated_fat_g\tsugars_g\tfiber_g\tsalt_g",
            "1\tApple pie\t237\t1.9\t34\t11\t\t16\t1.6\t0.5",
        ).joinToString("\n")
        GenericFoodImporter(db.productQueries).import(tsv, nowMillis = 1L)

        val found = SqlDelightProductSearchStore(db.productSearchQueries).search("app")

        assertEquals(listOf("Apple pie"), found.map { it.name })
    }

    @Test
    fun goalsSaveAndReloadOnTheDevice() = runBlocking {
        val db = createDatabase(InstrumentationRegistry.getInstrumentation().targetContext, name = null)
        val store = SqlDelightGoalStore(db.dailyGoalQueries)
        val goals = DailyGoals(kcal = 2759, proteinG = 128, carbsG = 389, fatG = 77)

        store.save(goals)

        assertEquals(goals, store.get())
    }
}
