package com.easyfood.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProductQueriesTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var queries: ProductQueries

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(driver)
        queries = EasyFoodDatabase(driver).productQueries
    }

    @AfterTest
    fun tearDown() = driver.close()

    private fun insert(barcode: String, name: String, kcal: Double? = 539.0) = queries.upsert(
        barcode = barcode, name = name, brand = "Ferrero", image_url = null,
        kcal = kcal, protein_g = 6.3, carbs_g = 57.5, sugars_g = 56.3, fat_g = 30.9,
        saturated_fat_g = 10.6, fiber_g = null, salt_g = 0.107,
        serving_size_g = 15.0, serving_label = "1 cucharada", source = "OPEN_FOOD_FACTS",
        cached_at = 1_700_000_000_000,
    )

    @Test
    fun insertAndReadProduct() {
        insert("3017620422003", "Nutella")

        val p = queries.selectByBarcode("3017620422003").executeAsOne()
        assertEquals("Nutella", p.name)
        assertEquals("Ferrero", p.brand)
        assertEquals(539.0, p.kcal)
        assertEquals(10.6, p.saturated_fat_g)
        assertNull(p.fiber_g)
        assertEquals(15.0, p.serving_size_g)
        assertEquals("OPEN_FOOD_FACTS", p.source)
        assertEquals(1_700_000_000_000, p.cached_at)
    }

    @Test
    fun upsertReplacesExistingBarcode() {
        insert("1", "Viejo")
        insert("1", "Nuevo", kcal = null)

        val all = queries.selectAll().executeAsList()
        assertEquals(1, all.size)
        assertEquals("Nuevo", all.single().name)
        assertNull(all.single().kcal)
    }

    @Test
    fun missingBarcodeReturnsNull() {
        assertNull(queries.selectByBarcode("0").executeAsOneOrNull())
    }
}
