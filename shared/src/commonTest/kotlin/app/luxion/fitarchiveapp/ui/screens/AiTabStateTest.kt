package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.ai.AdviceHarness
import app.luxion.fitarchiveapp.ai.AiAdviceLimits
import app.luxion.fitarchiveapp.ai.AiPrivacy
import app.luxion.fitarchiveapp.ai.AiReviewPeriod
import app.luxion.fitarchiveapp.model.DailyGoals
import app.luxion.fitarchiveapp.model.MealType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiTabStateTest {
    private val options = """{"options":[
        {"name":"Yogur con nueces","amount":"150 g","kcal":210.4,"proteinG":9.6,"carbsG":12,"fatG":13,"reason":"Proteína y grasas buenas"},
        {"name":"Tortilla","amount":"1 ración","kcal":220,"proteinG":14,"carbsG":2,"fatG":17,"reason":"Saciante"}]}"""

    @Test
    fun gateFollowsKeyActivationAndConsent() = runTest {
        val noKey = AdviceHarness(key = null).also { it.refresh() }
        assertEquals(AiTabStatus.NeedsKey, noKey.state.gate)
        val off = AdviceHarness(enabled = false).also { it.refresh() }
        assertEquals(AiTabStatus.Disabled, off.state.gate)
        val ready = AdviceHarness().also { it.refresh() }
        assertEquals(AiTabStatus.Ready, ready.state.gate)
        assertEquals(30, ready.state.callsLeft)
    }

    @Test
    fun nothingIsSentWithoutKeyOrActivationOrTheFeatureNotice() = runTest {
        for (h in listOf(AdviceHarness(key = null), AdviceHarness(enabled = false), AdviceHarness(ack = false))) {
            h.reply(options)
            h.refresh()
            h.state.suggest("")
            h.state.review(AiReviewPeriod.DAY)
            h.state.ask("hola")
            assertEquals(0, h.requests.size)
            assertEquals(AiLoad.Idle, h.state.suggestions)
        }
    }

    @Test
    fun acceptingTheNoticeUnlocksTheCalls() = runTest {
        val h = AdviceHarness(ack = false)
        h.refresh()
        assertFalse(h.state.advicePrivacyAccepted)
        h.state.acceptAdvicePrivacy()
        assertTrue(h.state.advicePrivacyAccepted)
        assertTrue(h.module.settings.hasAck(AiPrivacy.ADVICE_ACK))
        h.reply(options)
        h.state.suggest("")
        assertIs<AiLoad.Done<*>>(h.state.suggestions)
    }

    @Test
    fun suggestionsAreLoadedCountedAndCandidatesAreSent() = runTest {
        val h = AdviceHarness(goals = DailyGoals(kcal = 2000, proteinG = 100))
        h.reply(options)
        h.refresh()
        h.state.suggest("sin gluten")
        val done = assertIs<AiLoad.Done<List<app.luxion.fitarchiveapp.ai.MealSuggestion>>>(h.state.suggestions)
        assertEquals(2, done.value.size)
        assertEquals(1, h.state.callsToday)
        assertEquals(29, h.state.callsLeft)
        assertEquals(1, h.requests.size)
        assertTrue("Quedan 2000 kcal." in h.requests.single())
    }

    @Test
    fun addToDiaryCreatesAQuickEntryInTheCurrentMealAndCanBeUndone() = runTest {
        val h = AdviceHarness(meal = MealType.SNACK)
        h.reply(options)
        h.refresh()
        h.state.suggest("")
        h.state.addToDiary(0)

        val entry = h.diary.rows.single()
        assertTrue(entry.quick)
        assertEquals(MealType.SNACK, entry.meal)
        assertEquals("2026-11-10", entry.date)
        assertEquals("Yogur con nueces (150 g)", entry.name)
        assertEquals(210.0, entry.totals.kcal) // se redondea; las entradas rápidas guardan los totales como «por 100 g» de 100 g
        assertEquals(MealType.SNACK, h.state.added[0]!!.meal)

        h.state.addToDiary(0) // no se duplica
        assertEquals(1, h.diary.rows.size)

        h.state.undoAdd(0)
        assertTrue(h.diary.rows.isEmpty())
        assertNull(h.state.added[0])
    }

    @Test
    fun aFailureWritingTheDiaryIsReportedAndKeepsTheSuggestions() = runTest {
        val h = AdviceHarness()
        h.reply(options)
        h.refresh()
        h.state.suggest("")
        h.diary.failing = true
        h.state.addToDiary(1)
        assertEquals("No se pudo añadir al diario. Inténtalo de nuevo.", h.state.addError)
        assertIs<AiLoad.Done<*>>(h.state.suggestions)
        assertTrue(h.state.added.isEmpty())
    }

    @Test
    fun errorsAreShownInSpanishAndRefusalsAreFlagged() = runTest {
        val h = AdviceHarness()
        h.refresh()
        h.reply("", stop = "refusal")
        h.state.suggest("")
        val refusal = assertIs<AiLoad.Failed>(h.state.suggestions)
        assertTrue(refusal.refusal && !refusal.retryable)

        h.reply("", status = 401)
        h.state.review(AiReviewPeriod.WEEK)
        val bad = assertIs<AiLoad.Failed>(h.state.review)
        assertTrue("clave" in bad.message)

        h.reply("", status = 529)
        h.state.ask("hola")
        val down = assertIs<AiLoad.Failed>(h.state.chatStatus)
        assertTrue(down.retryable)
        assertTrue(h.state.chat.isEmpty())
    }

    @Test
    fun theDailyLimitDisablesCallsAndIsExposed() = runTest {
        val h = AdviceHarness(limit = 2)
        h.reply(options)
        h.refresh()
        h.state.suggest("")
        assertFalse(h.state.limitReached)
        h.state.suggest("")
        assertTrue(h.state.limitReached)
        assertEquals(0, h.state.callsLeft)
        h.state.suggest("")
        h.state.ask("hola")
        assertEquals(2, h.requests.size)
    }

    @Test
    fun theConversationKeepsOnlyTheLastMessages() = runTest {
        val h = AdviceHarness()
        h.refresh()
        for (i in 1..4) {
            h.reply("respuesta $i")
            h.state.ask("pregunta $i")
        }
        assertEquals(AiAdviceLimits.CHAT_HISTORY_MESSAGES, h.state.chat.size)
        assertEquals("pregunta 3", h.state.chat.first().text)
        assertEquals("respuesta 4", h.state.chat.last().text)
        h.state.clearChat()
        assertTrue(h.state.chat.isEmpty())
    }

    @Test
    fun anUnreadableLocalDatabaseIsReportedWithoutCallingTheApi() = runTest {
        val h = AdviceHarness()
        h.reply(options)
        h.refresh()
        h.diary.failing = true
        h.state.suggest("")
        assertEquals(AiLoad.Failed(AiTabState.LOCAL_DATA_ERROR), h.state.suggestions)
        assertEquals(0, h.requests.size)
    }
}
