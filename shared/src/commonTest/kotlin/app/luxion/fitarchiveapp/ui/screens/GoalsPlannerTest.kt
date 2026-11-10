package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.domain.ActivityLevel
import app.luxion.fitarchiveapp.domain.Goal
import app.luxion.fitarchiveapp.domain.Sex
import app.luxion.fitarchiveapp.domain.UserProfile
import app.luxion.fitarchiveapp.model.DailyGoals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoalsPlannerTest {
    private fun profile(goal: Goal, sex: Sex = Sex.MALE, weight: Double = 80.0, activity: ActivityLevel = ActivityLevel.MODERATE) =
        UserProfile(30, sex, weight, 180.0, activity, goal)

    @Test
    fun maintainEqualsTdee() {
        val plan = GoalsPlanner.plan(profile(Goal.MAINTAIN), GoalsPlanner.DEFAULT_PACE_INDEX)
        // TMB = 800 + 1125 - 150 + 5 = 1780 · ×1,55 = 2759
        assertEquals(1780, plan.bmrKcal)
        assertEquals(2759, plan.tdeeKcal)
        assertEquals(2759, plan.targetKcal)
        assertFalse(plan.limited)
    }

    @Test
    fun paceAdjustsTargetByGoal() {
        // 0,5 kg/sem = 550 kcal/día
        assertEquals(2759 - 550, GoalsPlanner.plan(profile(Goal.LOSE), 1).targetKcal)
        assertEquals(2759 + 550, GoalsPlanner.plan(profile(Goal.GAIN), 1).targetKcal)
        // 2759 − 1100 = 1659 < TMB (1780): se limita al TMB
        assertEquals(1780, GoalsPlanner.plan(profile(Goal.LOSE), 3).targetKcal)
    }

    @Test
    fun targetNeverBelowBmrAndFlagsIt() {
        val p = profile(Goal.LOSE, sex = Sex.FEMALE, weight = 50.0, activity = ActivityLevel.SEDENTARY)
        val plan = GoalsPlanner.plan(p, 3)
        assertTrue(plan.limited)
        assertEquals(maxOf(plan.bmrKcal, 1200), plan.targetKcal)
    }

    @Test
    fun macrosUseGoalProteinAndQuarterFat() {
        val m = GoalsPlanner.macros(2000, 80.0, Goal.MAINTAIN)
        assertEquals(128, m.proteinG) // 1,6 g/kg
        assertEquals(56, m.fatG) // 25 % de 2000 / 9
        assertEquals(247, m.carbsG) // (2000 - 512 - 500) / 4
    }

    @Test
    fun percentsSumTo100() {
        val (p, c, f) = GoalsPlanner.macroPercents(128, 247, 56)
        assertEquals(100, p + c + f)
        assertEquals(Triple(0, 0, 0), GoalsPlanner.macroPercents(0, 0, 0))
    }

    @Test
    fun resolvePrefersOverridesThenPlanThenSaved() {
        val p = profile(Goal.MAINTAIN)
        val plan = GoalsPlanner.plan(p, GoalsPlanner.DEFAULT_PACE_INDEX)
        val saved = DailyGoals(kcal = 1800, proteinG = 100, carbsG = 200, fatG = 60)

        assertEquals(saved, GoalsPlanner.resolve(null, null, saved, null, null, null, null))
        assertEquals(plan.targetKcal, GoalsPlanner.resolve(plan, p, saved, null, null, null, null).kcal)

        val manual = GoalsPlanner.resolve(plan, p, saved, 2000, 150, null, null)
        assertEquals(2000, manual.kcal)
        assertEquals(150, manual.proteinG)
        assertEquals(GoalsPlanner.macros(2000, 80.0, Goal.MAINTAIN).carbsG, manual.carbsG)
    }
}
