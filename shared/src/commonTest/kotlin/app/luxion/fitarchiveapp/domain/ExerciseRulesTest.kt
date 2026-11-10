package app.luxion.fitarchiveapp.domain

import app.luxion.fitarchiveapp.model.ExerciseRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExerciseRulesTest {
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
}
