package app.luxion.fitarchiveapp.plate

import app.luxion.fitarchiveapp.ai.AiError
import app.luxion.fitarchiveapp.ai.AiResult
import app.luxion.fitarchiveapp.ai.AiService
import app.luxion.fitarchiveapp.ai.downscaleToJpeg
import app.luxion.fitarchiveapp.data.DiaryStore
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.Nutrients
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Fases de «Foto del plato» (docs/design.md §2.21). */
sealed interface PlateState {
    /** La IA no se puede usar ahora (sin clave, desactivada, sin aviso aceptado o tope diario): no se pide foto. */
    data class Blocked(val error: AiError) : PlateState

    /** Esperando la foto. */
    data object Capture : PlateState

    /** Esperando que el usuario escriba lo que ha comido (sin foto). */
    data object TextInput : PlateState

    /** Foto lista; el usuario puede añadir notas antes de enviarla. */
    data object Ready : PlateState

    data object Analyzing : PlateState

    /** La IA no ha visto comida en la foto. */
    data class NoFood(val note: String?) : PlateState

    /** Fallo al leer la foto o al llamar a la IA; [retryable] si se puede reintentar con la misma foto. */
    data class Failed(val message: String, val retryable: Boolean) : PlateState

    /** Revisión editable. Nada se ha guardado todavía. [saving] mientras se escribe en el diario. */
    data class Review(
        val review: PlateReview,
        val note: String?,
        val saving: Boolean = false,
        val error: String? = null,
    ) : PlateState
}

/**
 * Lógica de «Foto del plato» y «Apuntar por texto»: reduce la foto (o recibe el texto), la envía a la IA, enlaza cada alimento con la base y guarda solo al
 * confirmar. La foto vive únicamente en memoria (nunca se escribe en disco) y se descarta al terminar o salir.
 */
class PlatePresenter(
    private val service: AiService,
    private val analyzer: PlateAnalyzer,
    private val catalog: FoodCatalog,
    private val diary: DiaryStore,
    private val nowMillis: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
    private val prepare: (ByteArray) -> ByteArray? = { downscaleToJpeg(it) },
    /** Enlaces corregidos por el usuario antes; `null` = no se recuerda nada. */
    private val aliases: FoodAliasStore? = null,
    startInText: Boolean = false,
) {
    /** `true` si el usuario describe la comida con texto en lugar de una foto. */
    var textMode: Boolean = startInText
        private set

    private var lastText: String = ""

    private val _state = MutableStateFlow<PlateState>(initial())
    val state: StateFlow<PlateState> = _state.asStateFlow()

    /** Foto reducida pendiente de enviar o de reintentar. Solo en memoria. */
    private var jpeg: ByteArray? = null
    private var savedIds = mutableSetOf<Int>()

    private fun initial(): PlateState =
        service.blocker()?.let { PlateState.Blocked(it) } ?: if (textMode) PlateState.TextInput else PlateState.Capture

    /** Vuelve a comprobar los ajustes (p. ej. al volver de «Ajustes de IA»). */
    fun refreshBlocker() {
        val s = _state.value
        if (s is PlateState.Blocked || s is PlateState.Capture || s is PlateState.TextInput) _state.value = initial()
    }

    /** Cambia a describir la comida con texto (sin foto). */
    fun startText() {
        discardPhoto()
        textMode = true
        _state.value = initial()
    }

    /** Describe la comida con [text] («2 huevos y una tostada con aceite»): una llamada a la IA sin foto, y luego la misma revisión. */
    suspend fun analyzeText(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || _state.value is PlateState.Analyzing) return // un segundo toque no vuelve a llamar a la IA
        service.blocker()?.let {
            _state.value = PlateState.Blocked(it)
            return
        }
        textMode = true
        lastText = clean
        _state.value = PlateState.Analyzing
        handle(analyzer.analyzeText(clean))
    }

    /** Vuelve a pedir el texto (tras un error o «No veo comida»). */
    fun editText() {
        textMode = true
        _state.value = initial()
    }

    /** Texto de la última descripción, para reintentar. */
    val lastDescription: String get() = lastText

    /** Recibe la foto de la cámara o del selector: la reduce (1024 px, sin metadatos) y pasa a «lista». */
    suspend fun onPhoto(raw: ByteArray) {
        val reduced = try {
            withContext(Dispatchers.Default) { prepare(raw) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (reduced == null) {
            _state.value = PlateState.Failed("No se pudo leer la imagen. Prueba con otra foto.", retryable = false)
            return
        }
        jpeg = reduced
        _state.value = PlateState.Ready
    }

    /** Envía la foto con las [notes] opcionales («con 200 g de arroz», un objeto de referencia...). */
    suspend fun analyze(notes: String) {
        if (_state.value is PlateState.Analyzing) return
        val photo = jpeg ?: run {
            _state.value = PlateState.Capture
            return
        }
        service.blocker()?.let {
            _state.value = PlateState.Blocked(it)
            return
        }
        _state.value = PlateState.Analyzing
        handle(analyzer.analyze(photo, notes))
    }

    private suspend fun handle(result: AiResult<PlateAnalysis>) {
        when (val r = result) {
            is AiResult.Err -> _state.value = when (r.error) {
                AiError.NoKey, AiError.Disabled, AiError.ConsentRequired, is AiError.LimitReached -> PlateState.Blocked(r.error)
                else -> PlateState.Failed(r.error.message, retryable = true)
            }
            is AiResult.Ok -> {
                val analysis = r.value
                if (!analysis.isFood) {
                    discardPhoto()
                    _state.value = PlateState.NoFood(analysis.note)
                } else {
                    val items = analysis.items.mapIndexed { i, d -> link(i, d).let { it.copy(question = questionFor(d, it)) } }
                    val review = PlateReview(items)
                    // La foto solo se conserva (en memoria) mientras quede alguna pregunta por responder.
                    if (!review.hasQuestions) discardPhoto()
                    savedIds = mutableSetOf()
                    _state.value = PlateState.Review(review, analysis.note)
                }
            }
        }
    }

    /**
     * Enlaza un alimento detectado con la base; sin coincidencia queda como estimación del modelo (marcada). Primero se
     * mira si el usuario ya corrigió ese término antes ([FoodAliasStore]); si no, se prueban el término de la IA y sus alternativos.
     */
    private suspend fun link(id: Int, d: DetectedFood): PlateItem {
        val match = try {
            val remembered = aliases?.get(d.searchTerm)?.let { catalog.resolveAlias(it) }
            val found = catalog.matchAny(d.terms)
            if (remembered == null) found else FoodMatch(listOf(remembered) + found.picks.filter { it != remembered }, loose = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FoodMatch(emptyList(), loose = false)
        }
        val best = match.picks.firstOrNull()
        val source = when {
            best != null -> PlateSource.Linked(best)
            else -> PlateSource.Estimated(d.estimate ?: Nutrients(null, null, null, null))
        }
        return PlateItem(
            id = id,
            name = best?.result?.name ?: d.name,
            grams = d.grams,
            source = source,
            confidence = d.confidence,
            portion = d.portion,
            looseMatch = best != null && match.loose,
            alternatives = match.picks,
            searchTerm = d.searchTerm,
        )
    }

    /**
     * Pregunta al usuario si la IA lo pidió o si no está segura (confianza baja) o no hay coincidencia en la base:
     * mejor preguntar que adivinar. Sin pregunta de la IA se usa [DEFAULT_QUESTION].
     */
    private fun questionFor(d: DetectedFood, item: PlateItem): PlateQuestion? {
        if (textMode) return null // sin foto no hay a quién re-consultar: lo no enlazado queda como estimado, editable
        val text = d.question
            ?: if (d.confidence == PlateConfidence.LOW || item.source !is PlateSource.Linked) DEFAULT_QUESTION else return null
        return PlateQuestion(text, d.options, askedGrams = d.grams)
    }

    /** Descarta la foto de memoria (al enviarla, al confirmar, al salir). */
    fun discardPhoto() {
        jpeg?.fill(0)
        jpeg = null
    }

    /** Vuelve a pedir otra foto. */
    fun retake() {
        discardPhoto()
        textMode = false
        _state.value = initial()
    }

    // — Edición de la revisión —

    private fun edit(f: (PlateReview) -> PlateReview) {
        val s = _state.value as? PlateState.Review ?: return
        if (s.saving) return
        val review = f(s.review)
        if (!review.hasQuestions) discardPhoto()
        _state.value = s.copy(review = review, error = null)
    }

    /**
     * Responde a la pregunta de la IA sobre el alimento [id]: re-consulta la misma foto (en memoria) con la [answer]
     * como dato, enlaza el resultado con la base y rellena nombre, gramos y macros. Si falla, la pregunta sigue
     * abierta con el error y se puede reintentar, dejar como está o cambiar el alimento a mano.
     */
    suspend fun answer(id: Int, answer: String) {
        val text = answer.trim().take(PlateAnalyzer.MAX_ANSWER)
        if (text.isEmpty()) return
        val s = _state.value as? PlateState.Review ?: return
        val item = s.review.items.firstOrNull { it.id == id } ?: return
        val q = item.question ?: return
        if (s.saving || q.busy) return
        fun fail(message: String) = edit { r -> r.updateQuestion(id) { it.copy(busy = false, error = message) } }
        val photo = jpeg ?: return fail("La foto ya no está disponible. Usa «Cambiar» o una entrada rápida.")
        service.blocker()?.let { return fail(it.message) }
        edit { r -> r.updateQuestion(id) { it.copy(busy = true, error = null) } }
        when (val r = analyzer.clarify(photo, item.name, item.grams, q.text, text)) {
            is AiResult.Err -> fail(r.error.message)
            is AiResult.Ok -> {
                val linked = link(id, r.value)
                // Mientras esperaba, el usuario pudo quitar el alimento, cambiarlo o dejar la duda: entonces no se pisa.
                val current = (_state.value as? PlateState.Review)?.review?.items?.firstOrNull { it.id == id }
                if (current?.question == null) return
                val grams = if (current.grams == q.askedGrams) linked.grams else current.grams
                edit { it.replace(id, linked.copy(grams = grams)) }
            }
        }
    }

    /** El usuario no quiere responder: el alimento se queda como estimado o se cambia a mano. */
    fun dismissQuestion(id: Int) = edit { it.dismissQuestion(id) }

    fun setGrams(id: Int, grams: Double) = edit { it.setGrams(id, grams) }
    fun link(id: Int, pick: app.luxion.fitarchiveapp.ui.screens.FoodPick) {
        // El usuario corrige el enlace de la IA: se recuerda para la próxima vez que salga ese término (no mientras se guarda: ahí no se edita).
        val s = _state.value as? PlateState.Review
        if (s != null && !s.saving) s.review.items.firstOrNull { it.id == id }?.searchTerm?.let { aliases?.remember(it, pick) }
        edit { it.link(id, pick) }
    }
    fun remove(id: Int) = edit { it.remove(id) }
    fun addLinked(pick: app.luxion.fitarchiveapp.ui.screens.FoodPick, grams: Double) = edit { it.addLinked(pick, grams) }
    fun addManual(name: String, totals: Nutrients) = edit { it.addManual(name, totals) }

    /** Busca en la base para «Cambiar alimento» y «Añadir». */
    suspend fun search(text: String) = catalog.search(text)

    companion object {
        const val DEFAULT_QUESTION = "¿Qué es esto?"
    }

    /**
     * Registra los alimentos en [meal] y [date] como entradas normales del diario (las no enlazadas, como entrada
     * rápida). Devuelve `true` si todo se guardó; si falla a medias, los ya guardados no se repiten al reintentar.
     */
    suspend fun confirm(date: String, meal: MealType): Boolean {
        val s = _state.value as? PlateState.Review ?: return false
        if (s.saving || s.review.items.isEmpty()) return false
        _state.value = s.copy(saving = true, error = null)
        return try {
            for (item in s.review.items) {
                if (item.id in savedIds) continue
                diary.add(item.toEntry(date, meal), nowMillis())
                savedIds += item.id
            }
            discardPhoto()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = s.copy(saving = false, error = "No se pudo guardar todo. Inténtalo de nuevo (lo ya guardado no se repetirá).")
            false
        }
    }
}
