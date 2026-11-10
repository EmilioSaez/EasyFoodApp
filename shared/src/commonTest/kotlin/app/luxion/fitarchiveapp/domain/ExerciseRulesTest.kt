package app.luxion.fitarchiveapp.domain

import app.luxion.fitarchiveapp.model.ExerciseRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExerciseRulesTest {
    @Test
    fun bonusIsThePercentOfTheExerciseRounded() {
        assertEquals(275, ExerciseRules.bonusKcal(550, 50))
        assertEquals(550, ExerciseRules.bonusKcal(550, 100))
        assertEquals(138, ExerciseRules.bonusKcal(550, 25)) // 137,5 redondea hacia arriba
        assertEquals(0, ExerciseRules.bonusKcal(550, 0))
    }

    @Test
    fun noExerciseOrInvalidPercentNeverSubtracts() {
        assertEquals(0, ExerciseRules.bonusKcal(0, 50))
        assertEquals(0, ExerciseRules.bonusKcal(-100, 50))
        assertEquals(0, ExerciseRules.bonusKcal(100, -20))
        assertEquals(100, ExerciseRules.bonusKcal(100, 500)) // se limita al 100 %
    }

    @Test
    fun defaultIsHalfAndTheChoicesIncludeOffAndAll() {
        assertEquals(50, ExerciseRules.DEFAULT_PERCENT)
        assertTrue(0 in ExerciseRules.PERCENT_CHOICES && 100 in ExerciseRules.PERCENT_CHOICES)
        assertTrue(ExerciseRules.PERCENT_CHOICES.all(ExerciseRules::isValidPercent))
    }

    @Test
    fun validation() {
        assertTrue(ExerciseRules.isValidKcal(1) && ExerciseRules.isValidKcal(10_000))
        assertFalse(ExerciseRules.isValidKcal(0) || ExerciseRules.isValidKcal(10_001))
        assertTrue(ExerciseRules.isValidMinutes(null) && ExerciseRules.isValidMinutes(1440))
        assertFalse(ExerciseRules.isValidMinutes(0) || ExerciseRules.isValidMinutes(1441))
        assertFalse(ExerciseRules.isValidPercent(-1) || ExerciseRules.isValidPercent(101))
    }

    @Test
    fun streakCountsADayThatOnlyFitsWithItsExerciseBonus() {
        val today = "2026-11-10"
        val summaries = mapOf("2026-11-09" to DaySummary("2026-11-09", 2400.0), today to DaySummary(today, 2000.0))
        assertEquals(1, DayStatus.streak(today, 2000, summaries))
        assertEquals(2, DayStatus.streak(today, 2000, summaries, mapOf("2026-11-09" to 200)))
    }
}
