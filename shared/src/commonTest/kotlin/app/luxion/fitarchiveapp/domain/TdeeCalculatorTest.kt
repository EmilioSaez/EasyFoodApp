package app.luxion.fitarchiveapp.domain

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TdeeCalculatorTest {
    private fun profile(
        sex: Sex = Sex.MALE,
        weight: Double = 80.0,
        height: Double = 180.0,
        age: Int = 30,
        goal: Goal = Goal.MAINTAIN,
    ) = UserProfile(age, sex, weight, height, goal)

    @Test
    fun bmrMale() {
        // 800 + 1125 - 150 + 5
        assertEquals(1780.0, TdeeCalculator.bmr(profile()))
    }

    @Test
    fun bmrFemale() {
        // 600 + 1050 - 125 - 161 (60 kg, 168 cm, 25 años)
        assertEquals(
            1364.0,
            TdeeCalculator.bmr(profile(Sex.FEMALE, 60.0, 168.0, 25)),
            0.001,
        )
    }

    @Test
    fun maintainUsesTdee() {
        val r = TdeeCalculator.calculate(profile())
        assertEquals(1780, r.bmrKcal)
        assertEquals(2136, r.tdeeKcal) // 1780 * 1.2
        assertEquals(2136, r.targetKcal)
    }

    @Test
    fun loseAndGainAdjustKcal() {
        val lose = TdeeCalculator.calculate(profile(goal = Goal.LOSE))
        val gain = TdeeCalculator.calculate(profile(goal = Goal.GAIN))
        assertEquals(1709, lose.targetKcal) // 2136 * 0.8
        assertEquals(2350, gain.targetKcal) // 2136 * 1.1
    }

    @Test
    fun stepsIncreaseTdee() {
        val base = TdeeCalculator.calculate(profile()).tdeeKcal
        assertEquals(base, TdeeCalculator.calculate(profile(), averageSteps = 3_000).tdeeKcal)
        assertTrue(TdeeCalculator.calculate(profile(), averageSteps = 12_000).tdeeKcal > base)
    }

    @Test
    fun macrosMatchTargetKcal() {
        for (goal in Goal.entries) {
            val r = TdeeCalculator.calculate(profile(goal = goal))
            val m = r.macros
            val kcal = m.proteinG * 4 + m.carbsG * 4 + m.fatG * 9
            assertTrue(abs(kcal - r.targetKcal) <= 20, "$goal: $kcal vs ${r.targetKcal}")
        }
    }

    @Test
    fun proteinPerKgDependsOnGoal() {
        assertEquals(160, TdeeCalculator.calculate(profile(goal = Goal.LOSE)).macros.proteinG)
        assertEquals(128, TdeeCalculator.calculate(profile(goal = Goal.MAINTAIN)).macros.proteinG)
        assertEquals(144, TdeeCalculator.calculate(profile(goal = Goal.GAIN)).macros.proteinG)
    }

    @Test
    fun targetNeverBelowMinimum() {
        val r = TdeeCalculator.calculate(
            profile(Sex.FEMALE, 40.0, 150.0, 70, Goal.LOSE),
        )
        assertEquals(TdeeCalculator.MIN_TARGET_KCAL, r.targetKcal)
        assertTrue(r.macros.carbsG >= 0)
    }

    @Test
    fun invalidProfileRejected() {
        assertFailsWith<IllegalArgumentException> { profile(age = 5) }
        assertFailsWith<IllegalArgumentException> { profile(weight = 0.0) }
        assertFailsWith<IllegalArgumentException> { profile(height = -1.0) }
    }
}
