package app.luxion.fitarchiveapp.domain

import app.luxion.fitarchiveapp.model.epochDayToIsoDate
import app.luxion.fitarchiveapp.model.isoDateToEpochDay
import app.luxion.fitarchiveapp.steps.DailySteps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StepsActivityTest {
    private val today = "2026-11-09"
    private val profile = UserProfile(30, Sex.MALE, 70.0, 175.0, ActivityLevel.SEDENTARY, Goal.MAINTAIN)

    private fun days(count: Int, steps: Long): List<DailySteps> =
        (1..count).map { DailySteps(epochDayToIsoDate(isoDateToEpochDay(today)!! - it), steps) }

    @Test
    fun ventanaDeLectura() {
        assertEquals("2026-10-26", StepsActivity.windowStart(today))
        assertEquals("2026-11-08", StepsActivity.windowEnd(today))
        assertNull(StepsActivity.windowStart("no-es-fecha"))
    }

    @Test
    fun mediaIgnoraHoyDiasVaciosYFueraDeVentana() {
        val steps = days(6, 10_000) +
            DailySteps(today, 1_000) + // hoy está incompleto
            DailySteps("2026-10-01", 20_000) + // fuera de la ventana
            DailySteps("2026-10-27", 0) // sin datos
        assertEquals(10_000 to 6, StepsActivity.averageSteps(steps, today))
    }

    @Test
    fun pocosDiasNoGeneranSugerencia() {
        assertNull(StepsActivity.averageSteps(days(4, 10_000), today))
        assertNull(StepsActivity.suggest(profile, days(4, 10_000), today, fixed = false))
    }

    @Test
    fun cadaDiaSeLimitaYNoSeCuentaDosVeces() {
        val steps = days(5, 10_000) + DailySteps("2026-11-08", 99_999) // duplicado de ayer
        assertEquals(10_000 to 5, StepsActivity.averageSteps(steps, today))
        assertEquals(40_000 to 5, StepsActivity.averageSteps(days(5, 90_000), today))
    }

    @Test
    fun pocosPasosEquivalenASedentaria() {
        val tmb = TdeeCalculator.bmr(profile)
        assertEquals(tmb * 1.2, StepsActivity.stepsTdee(profile, 2_000), 1e-9)
        assertEquals(tmb * 1.2, StepsActivity.stepsTdee(profile, 3_000), 1e-9)
    }

    @Test
    fun pasosSumanSobreElUmbral() {
        val tmb = TdeeCalculator.bmr(profile)
        // (13 000 − 3 000) × 0,0005 × 70 = 350 kcal
        assertEquals(tmb * 1.2 + 350, StepsActivity.stepsTdee(profile, 13_000), 1e-9)
    }

    @Test
    fun sugiereSubirElNivel() {
        val s = StepsActivity.suggest(profile, days(7, 13_000), today, fixed = false)!!
        // factor = 1,2 + 350 / TMB (1 648,75) ≈ 1,41 → ligera (1,375)
        assertEquals(ActivityLevel.LIGHT, s.suggestedLevel)
        assertEquals(13_000, s.averageSteps)
        assertEquals(7, s.daysUsed)
        assertTrue(s.changesLevel)
        assertTrue(s.canApply)
    }

    @Test
    fun sinCambioSiElNivelYaEncaja() {
        val s = StepsActivity.suggest(profile, days(7, 3_500), today, fixed = false)!!
        assertEquals(ActivityLevel.SEDENTARY, s.suggestedLevel)
        assertFalse(s.changesLevel)
        assertFalse(s.canApply)
    }

    @Test
    fun objetivoFijoSoloSugiere() {
        val s = StepsActivity.suggest(profile, days(7, 13_000), today, fixed = true)!!
        assertTrue(s.changesLevel)
        assertFalse(s.canApply)
    }

    @Test
    fun puedeSugerirBajarElNivel() {
        val active = profile.copy(activity = ActivityLevel.VERY_ACTIVE)
        val s = StepsActivity.suggest(active, days(7, 4_000), today, fixed = false)!!
        assertEquals(ActivityLevel.SEDENTARY, s.suggestedLevel)
        assertTrue(s.stepsTdeeKcal < s.currentTdeeKcal)
    }

    @Test
    fun nivelMasCercano() {
        assertEquals(ActivityLevel.SEDENTARY, StepsActivity.nearestLevel(1.1))
        assertEquals(ActivityLevel.VERY_ACTIVE, StepsActivity.nearestLevel(2.5))
        assertEquals(ActivityLevel.MODERATE, StepsActivity.nearestLevel(1.5))
    }
}
