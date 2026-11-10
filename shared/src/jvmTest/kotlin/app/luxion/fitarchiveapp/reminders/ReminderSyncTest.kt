package app.luxion.fitarchiveapp.reminders

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.data.SqlDelightDiaryStore
import app.luxion.fitarchiveapp.data.SqlDelightSettingsStore
import app.luxion.fitarchiveapp.data.SqlDelightWaterStore
import app.luxion.fitarchiveapp.data.SqlDelightWeightStore
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.domain.PlannedReminder
import app.luxion.fitarchiveapp.domain.ReminderKind
import app.luxion.fitarchiveapp.domain.ReminderSettings
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.NewDiaryEntry
import app.luxion.fitarchiveapp.model.NewWeightEntry
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReminderSyncTest {
    private class FakeScheduler(var granted: Boolean = true, override val supported: Boolean = true) : ReminderScheduler {
        var last: List<PlannedReminder>? = null
        override suspend fun hasPermission() = granted
        override suspend fun requestPermission() = granted
        override fun replaceAll(reminders: List<PlannedReminder>) {
            last = reminders
        }
    }

    private val today = "2026-11-10" // martes
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { EasyFoodDatabase.Schema.create(it) }
    private val db = EasyFoodDatabase(driver)
    private val settings = SqlDelightSettingsStore(db.settingsQueries)
    private val diary = SqlDelightDiaryStore(db.diaryEntryQueries)
    private val weights = SqlDelightWeightStore(db.weightEntryQueries)
    private val water = SqlDelightWaterStore(db.waterQueries)

    private fun sync(scheduler: FakeScheduler) = ReminderSync(settings, diary, weights, water, scheduler)

    @Test
    fun nothingIsScheduledUntilEnabled() = runTest {
        val scheduler = FakeScheduler()
        sync(scheduler).sync(today, 0)
        assertEquals(emptyList(), scheduler.last)
    }

    @Test
    fun noPermissionOrUnsupportedPlatformSchedulesNothing() = runTest {
        settings.setReminders(ReminderSettings(enabled = true))
        val denied = FakeScheduler(granted = false)
        sync(denied).sync(today, 0)
        assertEquals(emptyList(), denied.last)
        val unsupported = FakeScheduler(supported = false)
        sync(unsupported).sync(today, 0)
        assertEquals(emptyList(), unsupported.last)
    }

    @Test
    fun registeringAMealRemovesTodaysReminderForIt() = runTest {
        settings.setMealsPerDay(3)
        settings.setReminders(ReminderSettings(enabled = true, weight = false))
        val scheduler = FakeScheduler()
        sync(scheduler).sync(today, 0)
        assertEquals(3, scheduler.last!!.count { it.date == today })

        diary.add(
            NewDiaryEntry(today, MealType.BREAKFAST, null, "Tostada", Quantity(50.0, QuantityUnit.GRAMS), null, Nutrients(250.0, 8.0, 45.0, 3.0)),
            1L,
        )
        sync(scheduler).sync(today, 0)
        val todays = scheduler.last!!.filter { it.date == today }
        assertEquals(2, todays.size)
        assertTrue(todays.none { it.title.contains("desayuno") })
    }

    @Test
    fun weighingRemovesTheWeeklyReminder() = runTest {
        settings.setReminders(ReminderSettings(enabled = true, meals = false, weight = true, weightDay = 5))
        val scheduler = FakeScheduler()
        sync(scheduler).sync(today, 0)
        assertEquals(listOf(ReminderKind.WEIGHT), scheduler.last!!.map { it.kind })

        weights.save(NewWeightEntry(today, 70.0), 1L)
        sync(scheduler).sync(today, 0)
        assertEquals(emptyList(), scheduler.last)
    }

    @Test
    fun reachingTheWaterGoalRemovesTodaysWaterReminder() = runTest {
        settings.setReminders(ReminderSettings(enabled = true, meals = false, weight = false, water = true, waterMinutes = 20 * 60))
        val scheduler = FakeScheduler()
        sync(scheduler).sync(today, 0)
        assertNotNull(scheduler.last!!.firstOrNull { it.date == today })

        water.add(today, water.goalMl(), 1L)
        sync(scheduler).sync(today, 0)
        assertTrue(scheduler.last!!.none { it.date == today })
    }
}
