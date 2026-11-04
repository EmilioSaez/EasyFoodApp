package com.easyfood.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProductValidatorTest {
    private fun product(
        kcal: Double? = 400.0,
        protein: Double? = 10.0,
        carbs: Double? = 60.0,
        fat: Double? = 13.0,
        name: String = "Galletas",
        brand: String? = "Marca",
        imageUrl: String? = "http://x/img.jpg",
    ) = Product("1", name, brand, imageUrl, Nutrients(kcal, protein, carbs, fat))

    @Test
    fun `producto coherente no genera avisos`() {
        // 4*10 + 4*60 + 9*13 = 397
        assertEquals(emptyList(), product().dataWarnings())
    }

    @Test
    fun `mas de 900 kcal por 100 g avisa`() {
        val w = product(kcal = 950.0, protein = 0.0, carbs = 0.0, fat = 100.0).dataWarnings()
        assertEquals(listOf<DataWarning>(DataWarning.ImplausibleKcal(950.0)), w)
    }

    @Test
    fun `exactamente 900 kcal no avisa`() {
        assertEquals(emptyList(), product(kcal = 900.0, protein = 0.0, carbs = 0.0, fat = 100.0).dataWarnings())
    }

    @Test
    fun `kcal incoherentes con los macros avisan`() {
        val w = product(kcal = 100.0).dataWarnings()
        assertEquals(listOf<DataWarning>(DataWarning.InconsistentMacros(100.0, 397.0)), w)
    }

    @Test
    fun `pequena desviacion dentro de tolerancia no avisa`() {
        assertEquals(emptyList(), product(kcal = 350.0).dataWarnings())
    }

    @Test
    fun `valores bajos no avisan por diferencia absoluta pequena`() {
        // estimado 4 kcal, declarado 20: diferencia relativa alta pero absoluta < 30
        assertEquals(emptyList(), product(kcal = 20.0, protein = 1.0, carbs = 0.0, fat = 0.0).dataWarnings())
    }

    @Test
    fun `macros que suman mas de 100 g avisan`() {
        val w = product(kcal = 600.0, protein = 40.0, carbs = 50.0, fat = 30.0).dataWarnings()
        assertTrue(DataWarning.MacrosExceed100g(120.0) in w)
    }

    @Test
    fun `campos ausentes avisan`() {
        val w = product(kcal = null, protein = null, carbs = null, fat = null, brand = null, imageUrl = "", name = " ")
            .dataWarnings()
        assertEquals(
            listOf<DataWarning>(
                DataWarning.MissingField("name"),
                DataWarning.MissingField("brand"),
                DataWarning.MissingField("imageUrl"),
                DataWarning.MissingField("kcal"),
                DataWarning.MissingField("proteinG"),
                DataWarning.MissingField("carbsG"),
                DataWarning.MissingField("fatG"),
            ),
            w,
        )
    }

    @Test
    fun `sin kcal no se comprueba coherencia`() {
        val w = product(kcal = null).dataWarnings()
        assertEquals(listOf<DataWarning>(DataWarning.MissingField("kcal")), w)
    }
}
