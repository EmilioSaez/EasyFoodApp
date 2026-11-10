package app.luxion.fitarchiveapp.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.domain.Goal
import app.luxion.fitarchiveapp.domain.Sex
import app.luxion.fitarchiveapp.domain.TdeeCalculator
import app.luxion.fitarchiveapp.domain.UserProfile
import app.luxion.fitarchiveapp.model.DailyGoals
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
    fun migrationFromV3KeepsGoals() = runTest {
        val old = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(old)
        // Estado v3: sin la columna `fixed`.
        old.execute(null, "DROP TABLE DailyGoalEntity", 0)
        old.execute(
            null,
            "CREATE TABLE DailyGoalEntity (id INTEGER NOT NULL PRIMARY KEY CHECK (id = 1), kcal INTEGER, " +
                "protein_g INTEGER, carbs_g INTEGER, fat_g INTEGER)",
            0,
        )
        old.execute(null, "INSERT INTO DailyGoalEntity VALUES (1, 2100, 150, 230, 70)", 0)

        EasyFoodDatabase.Schema.migrate(old, 3, EasyFoodDatabase.Schema.version)

        val migrated = SqlDelightGoalStore(EasyFoodDatabase(old).dailyGoalQueries)
        assertEquals(DailyGoals(2100, 150, 230, 70), migrated.get())
        old.close()
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
    fun goalSurvivesClosingAndReopeningTheDatabase() = runTest {
        val file = kotlin.io.path.createTempFile("easyfood-goal", ".db").toFile()
        try {
            val first = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
            EasyFoodDatabase.Schema.create(first)
            val goals = DailyGoals(kcal = 2400, proteinG = 160, carbsG = 260, fatG = 70)
            SqlDelightGoalStore(EasyFoodDatabase(first).dailyGoalQueries).save(goals)
            first.close()

            val second = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
            try {
                val reopened = SqlDelightGoalStore(EasyFoodDatabase(second).dailyGoalQueries)
                assertEquals(goals, reopened.get())
                reopened.save(DailyGoals(kcal = 2100))
                assertEquals(DailyGoals(kcal = 2100), reopened.get())
                assertEquals(1L, second.executeQuery(
                    null, "SELECT COUNT(*) FROM DailyGoalEntity", { c ->
                        app.cash.sqldelight.db.QueryResult.Value(if (c.next().value) c.getLong(0) else null)
                    }, 0,
                ).value)
            } finally {
                second.close()
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun onlyOneGoalRowCanExist() = runTest {
        store.save(DailyGoals(kcal = 2000))
        store.save(DailyGoals(kcal = 2500))
        val rows = driver.executeQuery(
            null, "SELECT COUNT(*) FROM DailyGoalEntity", { c ->
                app.cash.sqldelight.db.QueryResult.Value(if (c.next().value) c.getLong(0) else null)
            }, 0,
        ).value
        assertEquals(1L, rows)
        assertFailsWith<Exception> {
            driver.execute(null, "INSERT INTO DailyGoalEntity(id, kcal) VALUES (2, 1500)", 0)
        }
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
            UserProfile(30, Sex.MALE, 80.0, 180.0, Goal.MAINTAIN),
        )
        val goals = DailyGoals.from(targets)
        store.save(goals)
        assertEquals(targets.targetKcal, store.get().kcal)
        assertEquals(targets.macros.proteinG, store.get().proteinG)
    }
}
