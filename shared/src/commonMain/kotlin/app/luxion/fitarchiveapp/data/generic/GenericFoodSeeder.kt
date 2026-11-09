package app.luxion.fitarchiveapp.data.generic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Estado de la carga inicial de la base genérica. */
sealed interface GenericSeedState {
    data object Idle : GenericSeedState
    data class Running(val done: Int = 0, val total: Int = 0) : GenericSeedState {
        /** Avance entre 0 y 1, o `null` mientras aún no se conoce el total. */
        val fraction: Float? get() = if (total > 0) done.toFloat() / total else null
    }
    data object Done : GenericSeedState
    data class Failed(val message: String?) : GenericSeedState
}

/**
 * Carga la base genérica (docs/generic-foods.md) la primera vez que arranca la app, sin bloquear la UI:
 * lanzar [ensureSeeded] desde una corrutina de fondo y observar [state] para mostrar el progreso.
 *
 * Idempotente: si ya están cargados los datos de la versión actual ([GenericFoodFormat.DATA_VERSION]) no hace nada; si
 * la base es de una versión anterior (p. ej. nombres en inglés) la reimporta actualizando las filas. Llamadas simultáneas se serializan. La
 * importación es una única transacción, así que una interrupción no deja una carga a medias.
 */
class GenericFoodSeeder(
    private val importer: GenericFoodImporter,
    private val nowMillis: () -> Long,
    private val readResource: suspend () -> String = ::readEmbeddedGenericFoods,
    private val readTranslations: suspend () -> String? = ::readEmbeddedGenericTranslations,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<GenericSeedState>(GenericSeedState.Idle)
    val state: StateFlow<GenericSeedState> = _state.asStateFlow()

    /** Importa si hace falta. Un fallo queda en [state] como [GenericSeedState.Failed] y se puede reintentar llamando de nuevo. */
    suspend fun ensureSeeded() = mutex.withLock {
        if (_state.value == GenericSeedState.Done) return
        try {
            if (importer.importedCount() > 0 && importer.importedDataVersion() >= GenericFoodFormat.DATA_VERSION) {
                _state.value = GenericSeedState.Done
                return
            }
            _state.value = GenericSeedState.Running()
            importer.import(readResource(), nowMillis(), readTranslations()) { done, total ->
                _state.value = GenericSeedState.Running(done, total)
            }
            _state.value = GenericSeedState.Done
        } catch (e: CancellationException) {
            _state.value = GenericSeedState.Idle
            throw e
        } catch (e: Exception) {
            _state.value = GenericSeedState.Failed(e.message)
        }
    }
}
