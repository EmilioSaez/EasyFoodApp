package com.easyfood.data.generic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class GenericFoodFormatTest {
    private val header = GenericFoodFormat.COLUMNS.joinToString("\t")

    private fun doc(vararg rows: String) =
        (listOf(GenericFoodFormat.MAGIC, "#source=test", header) + rows).joinToString("\n")

    @Test
    fun parsesRowsWithEmptyCellsAsNull() {
        val result = GenericFoodFormat.parse(doc("1\tApples, raw\t52\t0.26\t13.8\t0.17\t\t10.4\t2.4\t0"))
        val food = result.foods.single()
        assertEquals("1", food.id)
        assertEquals("Apples, raw", food.name)
        assertEquals(52.0, food.nutrients.kcal)
        assertNull(food.nutrients.saturatedFatG)
        assertEquals(10.4, food.nutrients.sugarsG)
        assertEquals(0, result.skipped)
        assertEquals("generic:1", food.toProduct().barcode)
    }

    @Test
    fun skipsMalformedRows() {
        val result = GenericFoodFormat.parse(
            doc(
                "1\tOk\t10\t1\t1\t1\t\t\t\t",
                "2\tMissing columns\t10",
                "3\tBad number\tabc\t1\t1\t1\t\t\t\t",
                "4\tNo kcal\t\t1\t1\t1\t\t\t\t",
                "5\tNegative\t-3\t1\t1\t1\t\t\t\t",
                "\t\t10\t1\t1\t1\t\t\t\t",
            ),
        )
        assertEquals(listOf("1"), result.foods.map { it.id })
        assertEquals(5, result.skipped)
    }

    @Test
    fun toleratesCrlf() {
        val result = GenericFoodFormat.parse(doc("1\tOk\t10\t1\t1\t1\t\t\t\t").replace("\n", "\r\n"))
        assertEquals(1, result.foods.size)
    }

    @Test
    fun rejectsUnknownFormat() {
        assertFailsWith<IllegalArgumentException> { GenericFoodFormat.parse("hola") }
        assertFailsWith<IllegalArgumentException> { GenericFoodFormat.parse("${GenericFoodFormat.MAGIC}\nid\tname") }
    }
}
