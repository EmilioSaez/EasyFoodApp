package app.luxion.fitarchiveapp.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImageDownscalePlanTest {
    @Test
    fun smallImagesAreNotEnlarged() {
        assertEquals(800 to 600, planDownscale(800, 600))
        assertEquals(1024 to 1024, planDownscale(1024, 1024))
    }

    @Test
    fun longestSideIsCappedKeepingAspect() {
        assertEquals(1024 to 768, planDownscale(4000, 3000))
        assertEquals(768 to 1024, planDownscale(3000, 4000))
        assertEquals(1024 to 1, planDownscale(100_000, 100)) // nunca 0 px
    }

    @Test
    fun invalidSizesGiveNull() {
        assertNull(planDownscale(0, 10))
        assertNull(planDownscale(10, -1))
        assertNull(planDownscale(10, 10, 0))
    }

    @Test
    fun daySummaryTextIsMinimal() {
        val text = AiDaySummary(1234.4, 80.2, 150.0, 40.6, 2000.0, 120.0).toPromptText()
        assertEquals("Hoy: 1234 kcal, proteína 80 g, hidratos 150 g, grasa 41 g. Objetivo: 2000 kcal, proteína 120 g.", text)
    }
}
