package app.luxion.fitarchiveapp.ai

import app.luxion.fitarchiveapp.ui.screens.AiSettingsState
import app.luxion.fitarchiveapp.ui.screens.AiTabStatus
import app.luxion.fitarchiveapp.ui.screens.AiTestResult
import app.luxion.fitarchiveapp.ui.screens.aiTabStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiSettingsStateTest {
    private val key = "sk-ant-api03-ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun module(status: HttpStatusCode, body: String): AiModule {
        val vault = InMemoryAiKeyVault()
        val engine = MockEngine { respond(body, status, json) }
        return AiModule.create(vault, InMemoryAiPreferences(), { "2026-11-10" }, ClaudeClient(HttpClient(engine), apiKey = { vault.read() }, pause = {}))
    }

    private val okModel = """{"id":"claude-haiku-5-5","display_name":"Claude Haiku 5.5","capabilities":{"image_input":{"supported":true},"structured_outputs":{"supported":true}}}"""

    @Test
    fun implausibleKeysAreRejectedWithoutSavingNorCalling() = runTest {
        val m = module(HttpStatusCode.OK, okModel)
        val state = AiSettingsState(m, { "2026-11-10" })
        listOf("", "hola", "sk-ant- con espacios dentro", "sk-ant-corta").forEach { state.saveKey(it) }
        assertFalse(state.hasKey)
        assertNull(m.vault.read())
        assertTrue(state.message!!.contains("no parece válida"))
    }

    @Test
    fun validKeyIsSavedTestedAndMasked() = runTest {
        val m = module(HttpStatusCode.OK, okModel)
        val state = AiSettingsState(m, { "2026-11-10" })
        state.saveKey("  $key  ")
        assertEquals(key, m.vault.read())
        assertTrue(state.hasKey)
        assertEquals("…WXYZ", state.keyHint)
        val result = assertIs<AiTestResult.Success>(state.test)
        assertTrue(result.info.supportsImages && result.info.supportsStructuredOutputs)
    }

    @Test
    fun rejectedKeyIsDiscarded() = runTest {
        val m = module(HttpStatusCode.Unauthorized, """{"error":{"message":"invalid x-api-key"}}""")
        val state = AiSettingsState(m, { "2026-11-10" })
        state.saveKey(key)
        assertNull(m.vault.read())
        assertFalse(state.hasKey)
        assertEquals(AiError.InvalidKey.message, assertIs<AiTestResult.Failure>(state.test).message)
    }

    @Test
    fun enablingNeedsKeyAndConsentThenClearingKeyDisables() = runTest {
        val m = module(HttpStatusCode.OK, okModel)
        val state = AiSettingsState(m, { "2026-11-10" })
        state.refresh()
        state.setEnabled(true)
        assertFalse(state.settings.enabled) // sin clave no se activa
        state.saveKey(key)
        assertFalse(state.setEnabled(true)) // falta el aviso de privacidad
        assertFalse(state.settings.enabled)
        state.acceptConsentAndEnable()
        assertTrue(state.settings.enabled && state.settings.consentAccepted)
        assertTrue(m.service.isReady())
        state.clearKey()
        assertFalse(state.settings.enabled)
        assertFalse(m.service.isReady())
    }

    @Test
    fun modelAndLimitCanBeChanged() = runTest {
        val m = module(HttpStatusCode.OK, okModel)
        val state = AiSettingsState(m, { "2026-11-10" })
        state.setModel("claude-sonnet-5-5")
        state.adjustLimit(10)
        assertEquals("claude-sonnet-5-5", state.settings.model)
        assertEquals(AiSettingsStore.DEFAULT_DAILY_LIMIT + 10, state.settings.dailyLimit)
    }

    @Test
    fun tabStatusFollowsConfiguration() {
        assertEquals(AiTabStatus.NeedsKey, aiTabStatus(hasKey = false, enabled = true, consentAccepted = true))
        assertEquals(AiTabStatus.Disabled, aiTabStatus(true, enabled = false, consentAccepted = true))
        assertEquals(AiTabStatus.Disabled, aiTabStatus(true, enabled = true, consentAccepted = false))
        assertEquals(AiTabStatus.Ready, aiTabStatus(true, true, true))
    }

    @Test
    fun usdFormatting() {
        assertEquals("~0,02 $", AiSettingsState.formatUsd(0.0212))
        assertEquals("menos de 0,01 $", AiSettingsState.formatUsd(0.0002))
        assertEquals("0,00 $", AiSettingsState.formatUsd(0.0))
        assertEquals("~12,50 $", AiSettingsState.formatUsd(12.5))
    }
}
