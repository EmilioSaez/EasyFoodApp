package app.luxion.fitarchiveapp.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.NewDiaryEntry
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightSettingsStoreTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: EasyFoodDatabase
    private lateinit var store: SqlDelightSettingsStore

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(driver)
        db = EasyFoodDatabase(driver)
        store = SqlDelightSettingsStore(db.settingsQueries)
    }

    @AfterTest
    fun tearDown() = driver.close()

    private fun count(table: String): Long = driver.executeQuery(
        null, "SELECT COUNT(*) FROM $table", { c -> QueryResult.Value(if (c.next().value) c.getLong(0) else null) }, 0,
    ).value ?: 0L

    private fun entry(meal: MealType, name: String = "Arroz") = NewDiaryEntry(
        "2026-11-04", meal, null, name, Quantity(100.0, QuantityUnit.GRAMS), null, Nutrients(130.0, 2.7, 28.0, 0.3),
    )

    @Test
    fun nullUntilChosen() = runTest {
        assertNull(store.mealsPerDay())
    }

    @Test
    fun savesAndReplacesTheValueInASingleRow() = runTest {
        store.setMealsPerDay(5)
        assertEquals(5, store.mealsPerDay())
        store.setMealsPerDay(3)
        assertEquals(3, store.mealsPerDay())
        assertEquals(1L, count("SettingsEntity"))
    }

    @Test
    fun rejectsValuesOutsideThreeToSix() = runTest {
        assertFailsWith<IllegalArgumentException> { store.setMealsPerDay(2) }
        assertFailsWith<IllegalArgumentException> { store.setMealsPerDay(7) }
        assertNull(store.mealsPerDay())
    }

    @Test
    fun theDatabaseAlsoRejectsValuesOutsideTheRange() {
        assertFailsWith<Exception> { db.settingsQueries.upsertMealsPerDay(9) }
    }

    @Test
    fun valueSurvivesReopeningTheDatabase() = runTest {
        val file = kotlin.io.path.createTempFile("easyfood-settings", ".db").toFile()
        try {
            val first = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
            EasyFoodDatabase.Schema.create(first)
            SqlDelightSettingsStore(EasyFoodDatabase(first).settingsQueries).setMealsPerDay(6)
            first.close()
            val second = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
            try {
                assertEquals(6, SqlDelightSettingsStore(EasyFoodDatabase(second).settingsQueries).mealsPerDay())
            } finally {
                second.close()
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun diaryStoresTheNewMealTypes() = runTest {
        val diary = SqlDelightDiaryStore(db.diaryEntryQueries)
        MealType.entries.forEachIndexed { i, meal -> diary.add(entry(meal, "E$i"), i.toLong()) }
        assertEquals(MealType.entries.toSet(), diary.entriesForDate("2026-11-04").map { it.meal }.toSet())
    }

    @Test
    fun migrationFromV7CreatesSettingsAndKeepsTheDiaryIntact() = runTest {
        // Estado v7: diario con el CHECK antiguo (4 comidas), sin tabla de ajustes.
        driver.execute(null, "DROP TABLE SettingsEntity", 0)
        driver.execute(null, "DROP TABLE DiaryEntryEntity", 0)
        driver.execute(
            null,
            "CREATE TABLE DiaryEntryEntity (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, " +
                "meal TEXT NOT NULL CHECK (meal IN ('BREAKFAST', 'LUNCH', 'DINNER', 'SNACK')), barcode TEXT, " +
                "name TEXT NOT NULL, quantity REAL NOT NULL CHECK (quantity > 0), " +
                "unit TEXT NOT NULL CHECK (unit IN ('GRAMS', 'SERVINGS')), serving_size_g REAL, kcal_100g REAL, " +
                "protein_100g REAL, carbs_100g REAL, fat_100g REAL, created_at INTEGER NOT NULL)",
            0,
        )
        driver.execute(null, "CREATE INDEX diary_entry_date_idx ON DiaryEntryEntity(date, meal)", 0)
        driver.execute(
            null,
            "INSERT INTO DiaryEntryEntity VALUES (7, '2026-11-04', 'SNACK', '123', 'Galletas', 2, 'SERVINGS', 30, 480, 6, 70, 18, 55)",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO DiaryEntryEntity VALUES (9, '2026-11-04', 'BREAKFAST', NULL, 'Café', 200, 'GRAMS', NULL, NULL, NULL, NULL, NULL, 66)",
            0,
        )
        assertTrue(EasyFoodDatabase.Schema.version >= 8)

        // Idempotente: aplicarla dos veces da el mismo resultado.
        EasyFoodDatabase.Schema.migrate(driver, 7, EasyFoodDatabase.Schema.version)
        EasyFoodDatabase.Schema.migrate(driver, 7, EasyFoodDatabase.Schema.version)

        val migratedDb = EasyFoodDatabase(driver)
        val diary = SqlDelightDiaryStore(migratedDb.diaryEntryQueries)
        val rows = diary.entriesForDate("2026-11-04")
        assertEquals(listOf("Café", "Galletas"), rows.map { it.name }.sorted())
        val snack = rows.first { it.name == "Galletas" }
        assertEquals(7L, snack.id)
        assertEquals(MealType.SNACK, snack.meal)
        assertEquals(Quantity(2.0, QuantityUnit.SERVINGS), snack.quantity)
        assertEquals(30.0, snack.servingSizeG)
        assertEquals(55L, snack.createdAtMillis)
        // Ahora admite las comidas nuevas y los ids siguen creciendo.
        val id = diary.add(entry(MealType.LATE_SNACK, "Yogur"), 100L)
        assertTrue(id > 9)
        assertEquals(MealType.LATE_SNACK, diary.find(id)!!.meal)
        assertEquals(1L, driver.executeQuery(
            null, "SELECT COUNT(*) FROM sqlite_master WHERE name = 'diary_entry_date_idx'",
            { c -> QueryResult.Value(if (c.next().value) c.getLong(0) else null) }, 0,
        ).value)
        assertEquals(0L, driver.executeQuery(
            null, "SELECT COUNT(*) FROM sqlite_master WHERE name = 'DiaryEntryEntity_new'",
            { c -> QueryResult.Value(if (c.next().value) c.getLong(0) else null) }, 0,
        ).value)

        val settings = SqlDelightSettingsStore(migratedDb.settingsQueries)
        assertNull(settings.mealsPerDay()) // sin elegir: dispara la pantalla de primera vez
        settings.setMealsPerDay(4)
        assertEquals(4, settings.mealsPerDay())
    }
}
