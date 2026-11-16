package app.luxion.fitarchiveapp.data.supermarket

import app.luxion.fitarchiveapp.data.generic.GenericSeedState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Carga el catálogo de supermercados (docs/supermarket-catalog.md) la primera vez que arranca la app, sin bloquear la
 * UI. Análogo a `GenericFoodSeeder`: si ya hay productos del catálogo no hace nada (ni lee el recurso); una sola
 * transacción, así que una interrupción no deja una carga a medias; un fallo se puede reintentar.
 */
class SupermarketSeeder(
    private val importer: SupermarketImporter,
    private val nowMillis: () -> Long,
    private val readResource: suspend () -> String = ::readEmbeddedSupermarketProducts,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<GenericSeedState>(GenericSeedState.Idle)
    val state: StateFlow<GenericSeedState> = _state.asStateFlow()

    suspend fun ensureSeeded() = mutex.withLock {
        if (_state.value == GenericSeedState.Done) return
        try {
            if (importer.importedCount() > 0) {
                _state.value = GenericSeedState.Done
                return
            }
            _state.value = GenericSeedState.Running()
            importer.import(readResource(), nowMillis()) { done, total ->
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
