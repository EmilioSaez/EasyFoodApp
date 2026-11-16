package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.luxion.fitarchiveapp.ai.AiAdviceContext
import app.luxion.fitarchiveapp.ai.AiAdviceLimits
import app.luxion.fitarchiveapp.ai.AiAdvisor
import app.luxion.fitarchiveapp.ai.AiAdviceSource
import app.luxion.fitarchiveapp.ai.AiChatTurn
import app.luxion.fitarchiveapp.ai.AiError
import app.luxion.fitarchiveapp.ai.AiMealPlan
import app.luxion.fitarchiveapp.ai.AiModule
import app.luxion.fitarchiveapp.ai.AiPrivacy
import app.luxion.fitarchiveapp.ai.AiResult
import app.luxion.fitarchiveapp.ai.AiReview
import app.luxion.fitarchiveapp.ai.AiReviewPeriod
import app.luxion.fitarchiveapp.ai.MealSuggestion
import app.luxion.fitarchiveapp.data.DiaryStore
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.QuickEntry
import app.luxion.fitarchiveapp.plate.FoodCatalog
import app.luxion.fitarchiveapp.plate.PlateItem
import app.luxion.fitarchiveapp.plate.PlateSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.round

/** Estado de una llamada de IA: sin pedir, en curso, hecha o fallida (con el mensaje ya en español). */
sealed interface AiLoad<out T> {
    data object Idle : AiLoad<Nothing>
    data object Loading : AiLoad<Nothing>
    data class Done<T>(val value: T) : AiLoad<T>

    /** [refusal]: la IA rechazó la petición por sus normas (no tiene sentido reintentar igual). [limit]: tope diario. */
    data class Failed(val message: String, val retryable: Boolean = false, val refusal: Boolean = false, val limit: Boolean = false) : AiLoad<Nothing>
}

private fun AiError.toFailed() = AiLoad.Failed(message, retryable, refusal = this is AiError.Refusal, limit = this is AiError.LimitReached)

/**
 * Opción de «¿Qué como?» enlazada con la base de alimentos (la del usuario, la genérica o un producto consultado): sus
 * kcal y macros salen de [pick] × [grams] y no de la estimación de la IA.
 */
data class SuggestionLink(val pick: FoodPick, val grams: Double) {
    private val item get() = PlateItem(0, pick.result.name, grams, PlateSource.Linked(pick))
    val kcal: Double get() = item.totals.kcal ?: 0.0
    val proteinG: Double get() = item.totals.proteinG ?: 0.0
    val carbsG: Double get() = item.totals.carbsG ?: 0.0
    val fatG: Double get() = item.totals.fatG ?: 0.0

    /** De dónde sale el dato, para mostrarlo («De tu base · Genérico»). */
    val origin: String get() = "De tu base · " + when (pick.result.source) {
        app.luxion.fitarchiveapp.ui.components.DataSource.Custom -> "Propio"
        app.luxion.fitarchiveapp.ui.components.DataSource.Generic -> "Genérico"
        app.luxion.fitarchiveapp.ui.components.DataSource.OpenFoodFacts -> "Open Food Facts"
    }

    fun toEntry(date: String, meal: MealType) = item.toEntry(date, meal)
}

/** Una opción de comida ya añadida al diario: [entryId] sirve para deshacer o ajustarla. */
data class AddedSuggestion(val entryId: Long, val meal: MealType)

/**
 * Estado y acciones de la pestaña «IA» (docs/ia.md). Cada acción de IA es una sola llamada a [AiAdvisor] y respeta el
 * tope diario; el historial de la pregunta libre se limita a [AiAdviceLimits.CHAT_HISTORY_MESSAGES] mensajes, solo en memoria.
 */
@Stable
class AiTabState(
    private val module: AiModule,
    private val source: AiAdviceSource,
    private val diary: DiaryStore,
    private val today: () -> String,
    private val defaultMeal: () -> MealType,
    private val nowMillis: () -> Long,
    /** Para enlazar las sugerencias con la base y usar valores reales; sin él, solo cifras de la IA. */
    private val catalog: FoodCatalog? = null,
) {
    private val advisor = AiAdvisor(module.service)

    /** `null` mientras se lee. */
    var gate by mutableStateOf<AiTabStatus?>(null)
        private set
    var callsToday by mutableStateOf(0)
        private set
    var dailyLimit by mutableStateOf(0)
        private set
    var advicePrivacyAccepted by mutableStateOf(true)
        private set

    var suggestions by mutableStateOf<AiLoad<List<MealSuggestion>>>(AiLoad.Idle)
        private set
    var added by mutableStateOf<Map<Int, AddedSuggestion>>(emptyMap())
        private set
    var addError by mutableStateOf<String?>(null)
        private set

    /** Opciones de [suggestions] que se enlazaron con la base (índice → enlace). */
    var links by mutableStateOf<Map<Int, SuggestionLink>>(emptyMap())
        private set

    var plan by mutableStateOf<AiLoad<AiMealPlan>>(AiLoad.Idle)
        private set
    var planDays by mutableStateOf(3)
        private set

    /** Artículos de la lista de la compra ya marcados (índice en [AiMealPlan.shopping]). */
    var bought by mutableStateOf<Set<Int>>(emptySet())
        private set

    var review by mutableStateOf<AiLoad<AiReview>>(AiLoad.Idle)
        private set
    var reviewPeriod by mutableStateOf(AiReviewPeriod.DAY)
        private set

    var chat by mutableStateOf<List<AiChatTurn>>(emptyList())
        private set
    var chatStatus by mutableStateOf<AiLoad<Unit>>(AiLoad.Idle)
        private set

    val callsLeft: Int get() = (dailyLimit - callsToday).coerceAtLeast(0)
    val limitReached: Boolean get() = gate == AiTabStatus.Ready && callsToday >= dailyLimit
    private val busy: Boolean get() = suggestions == AiLoad.Loading || review == AiLoad.Loading || chatStatus == AiLoad.Loading || plan == AiLoad.Loading

    /** Vuelve a leer ajustes y contador (al abrir la pestaña y tras cada llamada). */
    suspend fun refresh() {
        val hasKey = withContext(Dispatchers.Default) { module.service.hasKey() }
        val s = module.settings.load()
        gate = aiTabStatus(hasKey, s.enabled, s.consentAccepted)
        dailyLimit = s.dailyLimit
        callsToday = module.settings.usage(today()).callsToday
        advicePrivacyAccepted = module.settings.hasAck(AiPrivacy.ADVICE_ACK)
    }

    fun acceptAdvicePrivacy() {
        module.settings.setAck(AiPrivacy.ADVICE_ACK)
        advicePrivacyAccepted = true
    }

    /** «¿Qué como?»: 3-5 opciones para lo que queda del día. [preferences] es texto libre opcional. */
    suspend fun suggest(preferences: String) {
        if (busy || !canCall()) return
        suggestions = AiLoad.Loading
        added = emptyMap()
        links = emptyMap()
        addError = null
        suggestions = when (val r = call(withCandidates = true) { ctx -> advisor.suggestMeals(ctx, preferences) }) {
            is AiResult.Ok -> {
                links = linkSuggestions(r.value)
                AiLoad.Done(r.value)
            }
            is AiResult.Err -> r.error.toFailed()
            null -> AiLoad.Failed(LOCAL_DATA_ERROR)
        }
        refresh()
    }

    /** «¿Cómo voy?» para el día o la semana. */
    suspend fun review(period: AiReviewPeriod) {
        if (busy || !canCall()) return
        reviewPeriod = period
        review = AiLoad.Loading
        review = when (val r = call(withWeek = period == AiReviewPeriod.WEEK, withFoods = true) { ctx -> advisor.review(ctx, period) }) {
            is AiResult.Ok -> AiLoad.Done(r.value)
            is AiResult.Err -> r.error.toFailed()
            null -> AiLoad.Failed(LOCAL_DATA_ERROR)
        }
        refresh()
    }

    /** Pregunta libre. Viajan solo los últimos mensajes; la conversación se recorta al mismo tamaño. */
    suspend fun ask(question: String) {
        val q = question.trim().take(AiAdviceLimits.MAX_QUESTION)
        if (q.isEmpty() || busy || !canCall()) return
        chatStatus = AiLoad.Loading
        val history = chat
        when (val r = call(withFoods = true) { ctx -> advisor.ask(ctx, q, history) }) {
            is AiResult.Ok -> {
                chat = (history + AiChatTurn(true, q) + AiChatTurn(false, r.value)).takeLast(AiAdviceLimits.CHAT_HISTORY_MESSAGES)
                chatStatus = AiLoad.Done(Unit)
            }
            is AiResult.Err -> chatStatus = r.error.toFailed()
            null -> chatStatus = AiLoad.Failed(LOCAL_DATA_ERROR)
        }
        refresh()
    }

    /** Menú de [days] días y lista de la compra. [preferences] es texto libre opcional. */
    suspend fun planMeals(days: Int, preferences: String) {
        if (busy || !canCall()) return
        planDays = days.coerceIn(1, AiAdviceLimits.MAX_PLAN_DAYS)
        plan = AiLoad.Loading
        bought = emptySet()
        plan = when (val r = call(withCandidates = true) { ctx -> advisor.planMeals(ctx, preferences, planDays) }) {
            is AiResult.Ok -> AiLoad.Done(r.value)
            is AiResult.Err -> r.error.toFailed()
            null -> AiLoad.Failed(LOCAL_DATA_ERROR)
        }
        refresh()
    }

    fun toggleBought(index: Int) {
        bought = if (index in bought) bought - index else bought + index
    }

    fun clearPlan() {
        plan = AiLoad.Idle
        bought = emptySet()
    }

    /**
     * Enlaza cada opción con la base por su `search_term`. Solo se acepta una coincidencia exacta (sin aflojar la
     * búsqueda) y con kcal conocidas; si no, la opción conserva las cifras de la IA.
     */
    private suspend fun linkSuggestions(options: List<MealSuggestion>): Map<Int, SuggestionLink> {
        val cat = catalog ?: return emptyMap()
        val out = mutableMapOf<Int, SuggestionLink>()
        options.forEachIndexed { i, o ->
            val grams = o.grams ?: return@forEachIndexed
            if (o.searchTerm.isBlank()) return@forEachIndexed
            try {
                val m = cat.match(o.searchTerm)
                val pick = m.picks.firstOrNull()
                if (pick != null && !m.loose && pick.nutrients.kcal != null) out[i] = SuggestionLink(pick, grams)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // sin enlace: se queda con la estimación de la IA
            }
        }
        return out
    }

    fun clearChat() {
        chat = emptyList()
        chatStatus = AiLoad.Idle
    }

    /** Apunta la opción como entrada rápida en la comida actual (se puede deshacer o ajustar después). */
    suspend fun addToDiary(index: Int) {
        val option = (suggestions as? AiLoad.Done)?.value?.getOrNull(index) ?: return
        if (index in added) return
        val meal = defaultMeal()
        addError = null
        try {
            val link = links[index]
            val entry = link?.toEntry(today(), meal) ?: QuickEntry.new(
                date = today(),
                meal = meal,
                name = option.diaryName,
                kcal = option.kcal.rounded(),
                proteinG = option.proteinG.rounded(),
                carbsG = option.carbsG.rounded(),
                fatG = option.fatG.rounded(),
            )
            added = added + (index to AddedSuggestion(diary.add(entry, nowMillis()), meal))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            addError = "No se pudo añadir al diario. Inténtalo de nuevo."
        }
    }

    suspend fun undoAdd(index: Int) {
        val a = added[index] ?: return
        try {
            diary.delete(a.entryId)
            added = added - index
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            addError = "No se pudo deshacer. Bórralo desde el Diario."
        }
    }

    /** Comprueba tope y avisos antes de llamar; si algo falla deja el motivo en el estado de la tarjeta que lo pidió. */
    private suspend fun canCall(): Boolean {
        refresh()
        return gate == AiTabStatus.Ready && advicePrivacyAccepted && callsToday < dailyLimit
    }

    /** Construye el contexto mínimo y ejecuta [block]; `null` si no se pudo leer la base local. */
    private suspend fun <T> call(withCandidates: Boolean = false, withWeek: Boolean = false, withFoods: Boolean = false, block: suspend (AiAdviceContext) -> AiResult<T>): AiResult<T>? {
        val ctx = try {
            source.build(today(), defaultMeal(), withCandidates = withCandidates, withWeek = withWeek, withFoods = withFoods)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        return block(ctx)
    }

    private fun Double.rounded(): Double = round(this)

    companion object {
        const val LOCAL_DATA_ERROR = "No se pudieron leer tus datos locales. Inténtalo de nuevo."
    }
}
