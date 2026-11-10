package app.luxion.fitarchiveapp.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchTextTest {
    @Test
    fun normalizeStripsAccentsAndCase() {
        assertEquals(listOf("jamon", "iberico", "pina"), SearchText.terms("Jamón IBÉRICO, Piña"))
    }

    @Test
    fun stemHandlesSpanishPlurals() {
        assertEquals("manzana", SearchText.stem("manzanas"))
        assertEquals("limon", SearchText.stem("limones"))
        assertEquals("lapiz", SearchText.stem("lapices"))
        assertEquals("arroz", SearchText.stem("arroces"))
        assertEquals("pan", SearchText.stem("pan"))
        assertEquals("gas", SearchText.stem("gas"))
    }

    @Test
    fun closeTermsAllowTyposAndPartialTyping() {
        assertTrue(SearchText.isCloseTo("lechr", "leche"))
        assertTrue(SearchText.isCloseTo("mazana", "manzana"))
        assertTrue(SearchText.isCloseTo("manzsn", "manzana")) // errata dentro de un prefijo
        assertTrue(SearchText.isCloseTo("yogurt", "yogur"))
        assertFalse(SearchText.isCloseTo("pan", "pie")) // demasiado corto para tolerar errores
        assertFalse(SearchText.isCloseTo("leche", "carne"))
    }

    @Test
    fun expressionAddsStemSupermarketBrandsAndCorrections() {
        assertNull(ProductSearchQuery.toFtsExpression("  \"*() "))
        assertEquals("\"manzana\"*", ProductSearchQuery.toFtsExpression("Manzanas"))
        assertEquals(
            "(brand : \"mercadona\" OR brand : \"hacendado\" OR brand : \"deliplus\" OR brand : \"bosque verde\" OR brand : \"compy\") AND \"leche\"*",
            ProductSearchQuery.toFtsExpression("mercadona leche"),
        )
        val vocabulary = FoodVocabulary.of(listOf("Leche entera" to "Hacendado", "Leche de avena" to null))
        assertEquals("(\"lechr\"* OR \"leche\"*)", ProductSearchQuery.toFtsExpression("lechr", vocabulary))
        assertEquals("\"lech\"*", ProductSearchQuery.toFtsExpression("lech", vocabulary)) // hay prefijo: sin corregir
    }
}
