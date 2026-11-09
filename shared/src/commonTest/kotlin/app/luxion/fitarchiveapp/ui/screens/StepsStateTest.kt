package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.steps.DailySteps
import app.luxion.fitarchiveapp.steps.StepsAccess
import app.luxion.fitarchiveapp.steps.StepsProvider
import app.luxion.fitarchiveapp.steps.UnavailableStepsProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeStepsProvider(var current: StepsAccess, val onRequest: StepsAccess) : StepsProvider {
    var reads = 0
    override suspend fun access() = current
    override suspend fun requestAccess(): StepsAccess {
        current = onRequest
        return current
    }

    override suspend fun dailySteps(fromDate: String, toDate: String): List<DailySteps> {
        reads++
        return listOf(DailySteps("2026-11-09", 4_200), DailySteps("2026-11-08", 9_000))
    }
}

class StepsStateTest {
    private val today = { "2026-11-09" }

    @Test
    fun sinPermisoNoLeePasos() = runTest {
        val provider = FakeStepsProvider(StepsAccess.NEEDS_PERMISSION, StepsAccess.GRANTED)
        val state = StepsState(provider, today)
        state.refresh()
        assertEquals(StepsAccess.NEEDS_PERMISSION, state.access)
        assertTrue(state.days.isEmpty())
        assertEquals(0, provider.reads)
    }

    @Test
    fun alConcederElPermisoCargaLosPasos() = runTest {
        val state = StepsState(FakeStepsProvider(StepsAccess.NEEDS_PERMISSION, StepsAccess.GRANTED), today)
        state.refresh()
        state.request()
        assertEquals(StepsAccess.GRANTED, state.access)
        assertEquals(4_200L, state.todaySteps)
        assertEquals(2, state.days.size)
    }

    @Test
    fun permisoDenegadoSigueSinDatos() = runTest {
        val state = StepsState(FakeStepsProvider(StepsAccess.NEEDS_PERMISSION, StepsAccess.NEEDS_PERMISSION), today)
        state.request()
        assertEquals(StepsAccess.NEEDS_PERMISSION, state.access)
        assertTrue(state.days.isEmpty())
    }

    @Test
    fun escritorioNoDisponible() = runTest {
        val state = StepsState(UnavailableStepsProvider, today)
        state.refresh()
        assertEquals(StepsAccess.UNAVAILABLE, state.access)
        assertEquals(null, state.todaySteps)
    }
}
