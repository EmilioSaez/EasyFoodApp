package app.luxion.fitarchiveapp.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NumberFormatTest {
    @Test
    fun formatsSpanishThousandsAndDecimals() {
        assertEquals("1.215", formatNumber(1215.0))
        assertEquals("4,6", formatNumber(4.6, 1))
        assertEquals("2.100", formatKcal(2100.0))
        assertEquals("0,05", formatNumber(0.05, 2))
        assertEquals("1.234.567", formatNumber(1234567.0))
        assertEquals("-12,5", formatNumber(-12.5, 1))
    }

    @Test
    fun gramsDropTrailingZeroDecimal() {
        assertEquals("68", formatGrams(68.0))
        assertEquals("4,6", formatGrams(4.6))
    }

    @Test
    fun sanitizeKeepsDigitsAndOneSeparator() {
        assertEquals("12,5", sanitizeNumber("12.5"))
        assertEquals("1,25", sanitizeNumber("1,2,5"))
        assertEquals("30", sanitizeNumber("3a0 "))
    }

    @Test
    fun parseAcceptsCommaDecimal() {
        assertEquals(12.5, parseNumber("12,5"))
        assertNull(parseNumber(""))
    }
}
