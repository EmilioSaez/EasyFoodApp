package app.luxion.fitarchiveapp.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.domain.ReminderSettings
import app.luxion.fitarchiveapp.model.MealType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SqlDelightSettingsRemindersTest {
    private fun count(driver: JdbcSqliteDriver) = driver.executeQuery(
        null, "SELECT COUNT(*) FROM SettingsEntity", { c -> QueryResult.Value(if (c.next().value) c.getLong(0) else null) }, 0,
    ).value

    private fun freshDriver() = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { EasyFoodDatabase.Schema.create(it) }

    @Test
    fun nullUntilConfiguredThenSavedAlongsideMealsPerDay() = runTest {
        val driver = freshDriver()
        val store = SqlDelightSettingsStore(EasyFoodDatabase(driver).settingsQueries)
        assertNull(store.reminders())

        store.setMealsPerDay(5)
        val config = ReminderSettings(enabled = true, mealTimes = mapOf(MealType.LUNCH to 13 * 60), water = true)
        store.setReminders(config)

        assertEquals(config, store.reminders())
        assertEquals(5, store.mealsPerDay())
        // Cambiar las comidas no borra los recordatorios.
        store.setMealsPerDay(3)
        assertEquals(config, store.reminders())
        assertEquals(1L, count(driver))
        driver.close()
    }

    @Test
    fun remindersBeforeChoosingMealsDoNotSkipTheFirstTimeScreen() = runTest {
        val driver = freshDriver()
        val store = SqlDelightSettingsStore(EasyFoodDatabase(driver).settingsQueries)
        store.setReminders(ReminderSettings(enabled = true))
        assertNull(store.mealsPerDay())
        driver.close()
    }

    @Test
    fun corruptJsonReadsAsNull() = runTest {
        val driver = freshDriver()
        val db = EasyFoodDatabase(driver)
        db.settingsQueries.upsertReminders("{roto")
        assertNull(SqlDelightSettingsStore(db.settingsQueries).reminders())
        driver.close()
    }
}
