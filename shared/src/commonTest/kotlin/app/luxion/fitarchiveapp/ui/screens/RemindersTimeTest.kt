package app.luxion.fitarchiveapp.ui.screens

import kotlin.test.Test
import kotlin.test.assertEquals

class RemindersTimeTest {
    @Test
    fun formatsMinutesAsClockTime() {
        assertEquals("00:00", formatMinutes(0))
        assertEquals("08:30", formatMinutes(8 * 60 + 30))
        assertEquals("23:59", formatMinutes(24 * 60 - 1))
    }

    @Test
    fun stepsInQuartersOfAnHourWrappingAtMidnight() {
        assertEquals(9 * 60 + 15, stepMinutes(9 * 60, 1))
        assertEquals(8 * 60 + 45, stepMinutes(9 * 60, -1))
        assertEquals(0, stepMinutes(24 * 60 - 15, 1))
        assertEquals(24 * 60 - 15, stepMinutes(0, -1))
    }
}
