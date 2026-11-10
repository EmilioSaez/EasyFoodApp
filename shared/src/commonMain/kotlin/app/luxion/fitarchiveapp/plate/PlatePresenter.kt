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
 * Lógica de «Foto del plato»: reduce la foto, la envía a la IA, enlaza cada alimento con la base y guarda solo al
 * confirmar. La foto vive únicamente en memoria (nunca se escribe en disco) y se descarta al terminar o salir.
 */
class PlatePresenter(
    private val service: AiService,
    private val analyzer: PlateAnalyzer,
    private val catalog: FoodCatalog,
    private val diary: DiaryStore,
    private val nowMillis: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
    private val prepare: (ByteArray) -> ByteArray? = { downscaleToJpeg(it) },
) {
    private val _state = MutableStateFlow<PlateState>(initial())
    val state: StateFlow<PlateState> = _state.asStateFlow()

    /** Foto reducida pendiente de enviar o de reintentar. Solo en memoria. */
    private var jpeg: ByteArray? = null
    private var savedIds = mutableSetOf<Int>()

    private fun initial(): PlateState = service.blocker()?.let { PlateState.Blocked(it) } ?: PlateState.Capture

    /** Vuelve a comprobar los ajustes (p. ej. al volver de «Ajustes de IA»). */
    fun refreshBlocker() {
        val s = _state.value
        if (s is PlateState.Blocked || s is PlateState.Capture) _state.value = initial()
    }

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
        val photo = jpeg ?: run {
            _state.value = PlateState.Capture
            return
        }
        service.blocker()?.let {
            _state.value = PlateState.Blocked(it)
            return
        }
        _state.value = PlateState.Analyzing
        when (val r = analyzer.analyze(photo, notes)) {
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
                    val items = analysis.items.mapIndexed { i, d -> link(i, d) }
                    discardPhoto()
                    savedIds = mutableSetOf()
                    _state.value = PlateState.Review(PlateReview(items), analysis.note)
                }
            }
        }
    }

    /** Enlaza un alimento detectado con la base; sin coincidencia queda como estimación del modelo (marcada). */
    private suspend fun link(id: Int, d: DetectedFood): PlateItem {
        val match = try {
            catalog.match(d.searchTerm)
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
        )
    }

    /** Descarta la foto de memoria (al enviarla, al confirmar, al salir). */
    fun discardPhoto() {
        jpeg?.fill(0)
        jpeg = null
    }

    /** Vuelve a pedir otra foto. */
    fun retake() {
        discardPhoto()
        _state.value = initial()
    }

    // — Edición de la revisión —

    private fun edit(f: (PlateReview) -> PlateReview) {
        val s = _state.value as? PlateState.Review ?: return
        if (s.saving) return
        _state.value = s.copy(review = f(s.review), error = null)
    }

    fun setGrams(id: Int, grams: Double) = edit { it.setGrams(id, grams) }
    fun link(id: Int, pick: app.luxion.fitarchiveapp.ui.screens.FoodPick) = edit { it.link(id, pick) }
    fun remove(id: Int) = edit { it.remove(id) }
    fun addLinked(pick: app.luxion.fitarchiveapp.ui.screens.FoodPick, grams: Double) = edit { it.addLinked(pick, grams) }
    fun addManual(name: String, totals: Nutrients) = edit { it.addManual(name, totals) }

    /** Busca en la base para «Cambiar alimento» y «Añadir». */
    suspend fun search(text: String) = catalog.search(text)

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
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = s.copy(saving = false, error = "No se pudo guardar todo. Inténtalo de nuevo (lo ya guardado no se repetirá).")
            false
        }
    }
}
