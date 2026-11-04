package com.easyfood.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.easyfood.db.EasyFoodDatabase
import com.easyfood.model.CustomFood
import com.easyfood.model.FoodSource
import com.easyfood.model.NewCustomFood
import com.easyfood.model.Nutrients
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightCustomFoodStoreTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var store: SqlDelightCustomFoodStore

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(driver)
        store = SqlDelightCustomFoodStore(EasyFoodDatabase(driver).customFoodQueries)
    }

    @AfterTest
    fun tearDown() = driver.close()

    private val pan = NewCustomFood(
        name = "Pan casero", brand = "Horno de casa",
        nutrients = Nutrients(250.0, 8.0, 48.0, 2.5, saturatedFatG = 0.5, sugarsG = 3.0, fiberG = 2.5, saltG = 1.1),
        servingSizeG = 40.0, servingLabel = "1 rebanada",
    )

    @Test
    fun createAndFindRoundTripsAllFields() = runTest {
        val id = store.create(pan, 100L)
        assertEquals(
            CustomFood(id, pan.name, pan.brand, pan.nutrients, 40.0, "1 rebanada", 100L, 100L),
            store.find(id),
        )
        assertEquals(FoodSource.CUSTOM, store.find(id)!!.source)
    }

    @Test
    fun optionalFieldsStayNull() = runTest {
        val min = NewCustomFood("Manzana", nutrients = Nutrients(52.0, 0.3, 14.0, 0.2))
        val found = store.find(store.create(min, 1L))!!
        assertNull(found.brand)
        assertNull(found.servingSizeG)
        assertNull(found.nutrients.fiberG)
    }

    @Test
    fun missingFoodIsNull() = runTest {
        assertNull(store.find(99))
    }

    @Test
    fun allIsSortedByNameIgnoringCase() = runTest {
        store.create(pan.copy(name = "zumo"), 1L)
        store.create(pan.copy(name = "Arroz"), 2L)
        store.create(pan.copy(name = "bizcocho"), 3L)
        assertEquals(listOf("Arroz", "bizcocho", "zumo"), store.all().map { it.name })
    }

    @Test
    fun updateReplacesDataAndKeepsCreatedAt() = runTest {
        val id = store.create(pan, 100L)
        val edited = pan.copy(name = "Pan integral", brand = null, servingSizeG = null, servingLabel = null)
        assertTrue(store.update(id, edited, 200L))
        val found = store.find(id)!!
        assertEquals("Pan integral", found.name)
        assertNull(found.brand)
        assertNull(found.servingSizeG)
        assertEquals(100L, found.createdAtMillis)
        assertEquals(200L, found.updatedAtMillis)
    }

    @Test
    fun updateMissingReturnsFalse() = runTest {
        assertFalse(store.update(99, pan, 1L))
    }

    @Test
    fun deleteRemovesAndReportsExistence() = runTest {
        val id = store.create(pan, 1L)
        assertTrue(store.delete(id))
        assertNull(store.find(id))
        assertFalse(store.delete(id))
    }

    @Test
    fun trimsTextAndDropsBlankOptionals() = runTest {
        val id = store.create(pan.copy(name = "  Pan  ", brand = "  ", servingLabel = ""), 1L)
        val found = store.find(id)!!
        assertEquals("Pan", found.name)
        assertNull(found.brand)
        assertNull(found.servingLabel)
    }

    @Test
    fun validatesInput() {
        val ok = Nutrients(1.0, 1.0, 1.0, 1.0)
        assertFailsWith<IllegalArgumentException> { NewCustomFood("  ", nutrients = ok) }
        assertFailsWith<IllegalArgumentException> { NewCustomFood("x", nutrients = ok.copy(kcal = null)) }
        assertFailsWith<IllegalArgumentException> { NewCustomFood("x", nutrients = ok.copy(proteinG = -1.0)) }
        assertFailsWith<IllegalArgumentException> { NewCustomFood("x", nutrients = ok.copy(saltG = -0.1)) }
        assertFailsWith<IllegalArgumentException> { NewCustomFood("x", nutrients = ok, servingSizeG = 0.0) }
    }
}
