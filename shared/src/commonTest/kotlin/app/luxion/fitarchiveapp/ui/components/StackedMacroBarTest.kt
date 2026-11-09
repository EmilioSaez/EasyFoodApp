package app.luxion.fitarchiveapp.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class StackedMacroBarTest {
    @Test
    fun percentagesFollowEnergyAndSumTo100() {
        // 30 g P = 120 kcal, 50 g H = 200 kcal, 25 g G = 225 kcal → 22,0 / 36,7 / 41,3 %
        val shares = macroShares(30.0, 50.0, 25.0)
        assertEquals(listOf(22, 37, 41), shares.map { it.percent })
        assertEquals(100, shares.sumOf { it.percent })
    }

    @Test
    fun roundingAlwaysSumsTo100() {
        val shares = macroShares(10.0, 10.0, 10.0)
        assertEquals(100, shares.sumOf { it.percent })
    }

    @Test
    fun emptyOrInvalidMacrosGiveZero() {
        assertEquals(listOf(0, 0, 0), macroShares(0.0, 0.0, 0.0).map { it.percent })
        assertEquals(listOf(0, 0, 100), macroShares(-5.0, Double.NaN, 10.0).map { it.percent })
    }

    @Test
    fun descriptionNamesTheThreeMacros() {
        assertEquals(
            "Proteína 30 g, 22 %; Hidratos 50 g, 37 %; Grasa 25 g, 41 %",
            macroBarDescription(macroShares(30.0, 50.0, 25.0)),
        )
    }
}
