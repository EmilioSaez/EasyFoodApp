package app.luxion.fitarchiveapp.domain

import app.luxion.fitarchiveapp.model.WeightEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeightReminderTest {
    private fun e(date: String) = WeightEntry(date, 80.0)

    @Test
    fun dueWhenThereAreNoEntries() = assertTrue(WeightReminder.isDue(emptyList(), "2026-11-09"))

    @Test
    fun notDueBeforeSevenDays() {
        assertFalse(WeightReminder.isDue(listOf(e("2026-11-09")), "2026-11-09"))
        assertFalse(WeightReminder.isDue(listOf(e("2026-11-03")), "2026-11-09")) // 6 días
    }

    @Test
    fun dueFromSevenDays() {
        assertTrue(WeightReminder.isDue(listOf(e("2026-11-02")), "2026-11-09")) // 7 días
        assertTrue(WeightReminder.isDue(listOf(e("2026-09-01")), "2026-11-09"))
    }

    @Test
    fun usesTheMostRecentEntryRegardlessOfOrder() {
        assertFalse(WeightReminder.isDue(listOf(e("2026-11-08"), e("2026-10-01")), "2026-11-09"))
        assertFalse(WeightReminder.isDue(listOf(e("2026-10-01"), e("2026-11-08")), "2026-11-09"))
    }

    @Test
    fun crossesMonthAndYearBoundaries() {
        assertTrue(WeightReminder.isDue(listOf(e("2026-12-28")), "2027-01-04"))
        assertFalse(WeightReminder.isDue(listOf(e("2026-12-29")), "2027-01-04"))
    }

    @Test
    fun futureEntryOrInvalidTodayDoNotRemind() {
        assertFalse(WeightReminder.isDue(listOf(e("2026-11-20")), "2026-11-09"))
        assertFalse(WeightReminder.isDue(emptyList(), "no-es-fecha"))
    }

    @Test
    fun daysSinceLast() {
        assertEquals(7L, WeightReminder.daysSinceLast(listOf(e("2026-11-02")), "2026-11-09"))
        assertNull(WeightReminder.daysSinceLast(emptyList(), "2026-11-09"))
    }
}
