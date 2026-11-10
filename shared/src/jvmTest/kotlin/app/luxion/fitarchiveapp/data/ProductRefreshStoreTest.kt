package app.luxion.fitarchiveapp.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.NewDiaryEntry
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Product
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProductRefreshStoreTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: EasyFoodDatabase
    private lateinit var store: SqlDelightProductStore

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(driver)
        db = EasyFoodDatabase(driver)
        store = SqlDelightProductStore(db.productQueries)
    }

    @AfterTest
    fun tearDown() = driver.close()

    private val milk = Product("111", "Leche", "Marca", null, Nutrients(63.0, 3.1, 4.7, 3.6))

    private fun count(sql: String): Long = driver.executeQuery(
        null, sql, { c -> QueryResult.Value(if (c.next().value) c.getLong(0) else null) }, 0,
    ).value ?: 0L

    private fun diaryEntry(code: String) = NewDiaryEntry(
        "2026-11-01", MealType.BREAKFAST, code, "Leche", Quantity(200.0, QuantityUnit.GRAMS), null, milk.nutrients,
    )

    @Test
    fun refreshUpdatesTheCacheAndRecordsTheChange() = runTest {
        store.save(milk, 10L)
        val newer = milk.copy(name = "Leche entera", nutrients = Nutrients(80.0, 3.1, 4.7, 3.6))
        assertEquals(true, store.saveRefreshed(newer, 500L, "Energía 63 → 80 kcal"))

        assertEquals(CachedProduct(newer, 500L), store.find("111"))
        assertEquals(ProductRefreshInfo(500L, 500L, "Energía 63 → 80 kcal"), store.refreshInfo("111"))
        // El índice de búsqueda sigue al nombre nuevo.
        assertEquals(listOf("111"), SqlDelightProductSearchStore(db.productSearchQueries).search("entera").map { it.barcode })
    }

    @Test
    fun refreshWithoutChangeKeepsThePreviousChangeNote() = runTest {
        store.save(milk, 10L)
        store.saveRefreshed(milk, 500L, "Energía 60 → 63 kcal")
        store.saveRefreshed(milk, 900L, null)
        assertEquals(ProductRefreshInfo(900L, 500L, "Energía 60 → 63 kcal"), store.refreshInfo("111"))
    }

    @Test
    fun refreshNeverTouchesGenericOrNonOffRows() = runTest {
        db.productQueries.upsert(
            "generic:1", "Manzana", null, null, 52.0, 0.3, 14.0, 10.0, 0.2, null, 2.4, null, null, null, "GENERIC", 1L,
        )
        val hacked = milk.copy(barcode = "generic:1", name = "Otra cosa")
        assertEquals(false, store.saveRefreshed(hacked, 500L, null))
        assertEquals("Manzana", store.find("generic:1")?.product?.name)
        assertNull(store.refreshInfo("generic:1"))
        store.markChecked("generic:1", 500L)
        assertNull(store.refreshInfo("generic:1"))
        assertEquals(false, store.saveRefreshed(milk, 500L, null)) // ni siquiera existe
    }

    @Test
    fun refreshDoesNotAlterPastDiaryEntries() = runTest {
        store.save(milk, 10L)
        val diary = SqlDelightDiaryStore(db.diaryEntryQueries)
        val id = diary.add(diaryEntry("111"), createdAtMillis = 1L)
        store.saveRefreshed(milk.copy(nutrients = Nutrients(90.0, 4.0, 5.0, 5.0)), 500L, "Energía 63 → 90 kcal")

        assertEquals(63.0, diary.find(id)?.nutrients?.kcal)
    }

    @Test
    fun candidatesAreUsedOffProductsNotCheckedRecentlyOldestFirst() = runTest {
        val day = 86_400_000L
        for ((code, at) in listOf("1" to 1 * day, "2" to 2 * day, "3" to 3 * day, "4" to 4 * day, "5" to 1 * day)) {
            store.save(milk.copy(barcode = code), at)
        }
        val diary = SqlDelightDiaryStore(db.diaryEntryQueries)
        // 1, 2 y 3 se usan en el diario; 4 es favorito; 5 no se usa.
        for (code in listOf("1", "2", "3")) diary.add(diaryEntry(code), 1L)
        SqlDelightFavoriteStore(db.favoriteFoodQueries).setFavorite("4", true, 1L)
        store.markChecked("1", 10 * day) // consultado hace poco

        assertEquals(listOf("2", "3"), store.refreshCandidates(staleBeforeMillis = 3 * day + 1, limit = 10))
        assertEquals(listOf("2"), store.refreshCandidates(staleBeforeMillis = 3 * day + 1, limit = 1))
    }

    @Test
    fun deletingAProductDropsItsRefreshRow() = runTest {
        store.save(milk, 10L)
        store.saveRefreshed(milk, 500L, null)
        db.productQueries.deleteByBarcode("111")
        assertEquals(0L, count("SELECT count(*) FROM ProductRefreshEntity"))
    }
}
