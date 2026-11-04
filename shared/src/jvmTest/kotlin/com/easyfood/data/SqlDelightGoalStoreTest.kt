package com.easyfood.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.easyfood.db.EasyFoodDatabase
import com.easyfood.domain.ActivityLevel
import com.easyfood.domain.Goal
import com.easyfood.domain.Sex
import com.easyfood.domain.TdeeCalculator
import com.easyfood.domain.UserProfile
import com.easyfood.model.DailyGoals
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SqlDelightGoalStoreTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var store: SqlDelightGoalStore

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(driver)
        store = SqlDelightGoalStore(EasyFoodDatabase(driver).dailyGoalQueries)
    }

    @AfterTest
    fun tearDown() = driver.close()

    @Test
    fun emptyWhenNothingSaved() = runTest {
        assertTrue(store.get().isEmpty)
    }

    @Test
    fun savesAndReadsAllGoals() = runTest {
        val goals = DailyGoals(kcal = 2200, proteinG = 150, carbsG = 250, fatG = 70)
        store.save(goals)
        assertEquals(goals, store.get())
    }

    @Test
    fun partialGoalsKeepNulls() = runTest {
        store.save(DailyGoals(kcal = 1800))
        assertEquals(DailyGoals(kcal = 1800), store.get())
    }

    @Test
    fun saveReplacesPreviousGoals() = runTest {
        store.save(DailyGoals(kcal = 2000, proteinG = 100))
        store.save(DailyGoals(carbsG = 200))
        assertEquals(DailyGoals(carbsG = 200), store.get())
    }

    @Test
    fun clearRemovesGoals() = runTest {
        store.save(DailyGoals(kcal = 2000))
        store.clear()
        assertTrue(store.get().isEmpty)
    }

    @Test
    fun rejectsInvalidValues() {
        assertFailsWith<IllegalArgumentException> { DailyGoals(kcal = 0) }
        assertFailsWith<IllegalArgumentException> { DailyGoals(proteinG = -1) }
        assertFailsWith<IllegalArgumentException> { DailyGoals(carbsG = -1) }
        assertFailsWith<IllegalArgumentException> { DailyGoals(fatG = -1) }
    }

    @Test
    fun buildsFromTdeeTargets() = runTest {
        val targets = TdeeCalculator.calculate(
            UserProfile(30, Sex.MALE, 80.0, 180.0, ActivityLevel.MODERATE, Goal.MAINTAIN),
        )
        val goals = DailyGoals.from(targets)
        store.save(goals)
        assertEquals(targets.targetKcal, store.get().kcal)
        assertEquals(targets.macros.proteinG, store.get().proteinG)
    }
}
