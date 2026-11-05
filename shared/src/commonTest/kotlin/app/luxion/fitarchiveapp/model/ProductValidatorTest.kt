package app.luxion.fitarchiveapp.model

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

    private fun full(
        kcal: Double?, protein: Double?, carbs: Double?, fat: Double?,
        sat: Double? = null, sugars: Double? = null, fiber: Double? = null,
    ) = Product("1", "Alimento", "Marca", "http://x/img.jpg", Nutrients(kcal, protein, carbs, fat, sat, sugars, fiber))

    // --- Umbral de kcal: casos reales de USDA ---

    @Test
    fun `aceites y mantecas reales de USDA a 902 kcal no avisan`() {
        assertEquals(emptyList(), full(902.0, 0.0, 0.0, 100.0).dataWarnings())
    }

    @Test
    fun `905 kcal es el limite y 906 avisa`() {
        assertEquals(emptyList(), full(905.0, 0.0, 0.0, 100.0).dataWarnings())
        assertTrue(DataWarning.ImplausibleKcal(906.0) in full(906.0, 0.0, 0.0, 100.0).dataWarnings())
    }

    // --- Coherencia kcal / macros ---

    @Test
    fun `la fibra cuenta a 2 kcal por gramo y no genera falsos positivos`() {
        // Cardamomo (USDA): 311 kcal, P 10.76, H 68.47 (fibra 28), G 6.7. Sin fibra: 4P+4H+9G = 377 (18 % de desviación).
        assertEquals(emptyList(), full(311.0, 10.76, 68.47, 6.7, fiber = 28.0).dataWarnings())
    }

    @Test
    fun `sin dato de fibra el mismo cardamomo si avisa`() {
        assertTrue(full(311.0, 10.76, 68.47, 6.7).dataWarnings().any { it is DataWarning.InconsistentMacros })
    }

    @Test
    fun `fibra mayor que los hidratos se ignora`() {
        // Dato roto: no debe dar una estimación negativa ni inflar la tolerancia.
        val estimated = ProductValidator.estimatedKcal(Nutrients(100.0, 0.0, 10.0, 0.0, fiberG = 50.0))
        assertEquals(40.0, estimated)
    }

    @Test
    fun `desviacion del 14 por ciento no avisa y del 20 por ciento si`() {
        // estimado 397: 14 % por encima de 345 -> sin aviso; 20 % -> aviso
        assertEquals(emptyList(), full(345.0, 10.0, 60.0, 13.0).dataWarnings())
        assertTrue(full(318.0, 10.0, 60.0, 13.0).dataWarnings().any { it is DataWarning.InconsistentMacros })
    }

    @Test
    fun `vino tinto aparece como incoherente porque el alcohol no esta en los macros`() {
        // Limitación conocida: sin campo de alcohol no se puede distinguir de un dato erróneo. USDA: 83 kcal, H 2.6, P 0.07.
        assertTrue(full(83.0, 0.07, 2.6, 0.0).dataWarnings().any { it is DataWarning.InconsistentMacros })
    }

    @Test
    fun `agua y edulcorantes a cero kcal no avisan por valores bajos`() {
        assertEquals(emptyList(), full(0.0, 0.0, 0.0, 0.0).dataWarnings())
        assertEquals(emptyList(), full(2.0, 0.0, 0.5, 0.0).dataWarnings())
    }

    // --- Saturadas y azúcares ---

    @Test
    fun `saturadas mayores que las grasas avisan`() {
        val w = full(300.0, 5.0, 40.0, 13.0, sat = 20.0).dataWarnings()
        assertTrue(DataWarning.SaturatedExceedsFat(20.0, 13.0) in w)
    }

    @Test
    fun `azucares mayores que los hidratos avisan`() {
        val w = full(300.0, 5.0, 40.0, 13.0, sugars = 55.0).dataWarnings()
        assertTrue(DataWarning.SugarsExceedCarbs(55.0, 40.0) in w)
    }

    @Test
    fun `redondeos de etiqueta dentro de medio gramo no avisan`() {
        // USDA: tocino 0.35 g de azúcar con 0 g de hidratos; almíbar 73.2 de azúcar con 73.14 de hidratos.
        assertEquals(emptyList(), full(526.0, 37.0, 0.0, 42.0, sugars = 0.35).dataWarnings())
        assertEquals(emptyList(), full(268.0, 0.0, 73.14, 0.0, sugars = 73.2).dataWarnings())
    }

    @Test
    fun `igualdad exacta de saturadas y grasas no avisa`() {
        assertEquals(emptyList(), full(900.0, 0.0, 0.0, 100.0, sat = 100.0).dataWarnings())
    }

    @Test
    fun `needsReview usa el mismo umbral que el validador`() {
        assertTrue(!Nutrients(902.0, 0.0, 0.0, 100.0).needsReview)
        assertTrue(Nutrients(950.0, 0.0, 0.0, 100.0).needsReview)
        assertTrue(Nutrients(null, 1.0, 1.0, 1.0).needsReview)
    }
}
