package app.luxion.fitarchiveapp.domain

import app.luxion.fitarchiveapp.model.Nutrients
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProductChangeTest {
    private val base = Nutrients(kcal = 250.0, proteinG = 8.0, carbsG = 40.0, fatG = 5.0)

    @Test
    fun identicalOrRoundingDifferencesAreNotRelevant() {
        assertNull(ProductChange.summary(base, base))
        assertNull(ProductChange.summary(base, base.copy(kcal = 252.0, proteinG = 8.4, carbsG = 40.9, fatG = 5.5)))
    }

    @Test
    fun energyChangeIsSummarized() {
        assertEquals("Energía 250 → 230 kcal", ProductChange.summary(base, base.copy(kcal = 230.0)))
    }

    @Test
    fun severalChangesAreJoined() {
        assertEquals(
            "Energía 250 → 300 kcal · Proteína 8 → 10,5 g",
            ProductChange.summary(base, base.copy(kcal = 300.0, proteinG = 10.5)),
        )
    }

    @Test
    fun smallRelativeChangeOnBigValueIsIgnored() {
        // 1 g sobre 40 g de hidratos es un 2,5 %: ruido.
        assertNull(ProductChange.summary(base, base.copy(carbsG = 41.0)))
    }

    @Test
    fun missingDataAppearingOrDisappearingIsRelevant() {
        assertEquals("Grasas 5 → sin dato g", ProductChange.summary(base, base.copy(fatG = null)))
        assertEquals("Grasas sin dato → 5 g", ProductChange.summary(base.copy(fatG = null), base))
    }
}
