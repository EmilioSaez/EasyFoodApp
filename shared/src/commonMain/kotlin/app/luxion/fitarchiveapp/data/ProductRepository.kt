package app.luxion.fitarchiveapp.data

import app.luxion.fitarchiveapp.data.generic.GenericFoodFormat
import app.luxion.fitarchiveapp.domain.ProductChange
import app.luxion.fitarchiveapp.model.Product
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/** Fuente remota de productos (Open Food Facts). */
interface ProductRemoteSource {
    suspend fun findByBarcode(barcode: String): ProductLookup
}

/** Producto guardado en local junto con el instante (epoch ms) en que se descargó. */
data class CachedProduct(val product: Product, val fetchedAtMillis: Long)

/** Seguimiento del refresco de un producto: última confirmación de Open Food Facts y último cambio relevante. */
data class ProductRefreshInfo(
    val refreshedAtMillis: Long?,
    val changedAtMillis: Long?,
    val changeSummary: String?,
)

/** Acceso a la caché local de productos (la implementación real será SQLDelight). */
interface ProductLocalStore {
    suspend fun find(barcode: String): CachedProduct?
    suspend fun save(product: Product, fetchedAtMillis: Long)

    /**
     * Guarda una versión refrescada de un producto de Open Food Facts ya cacheado. [changeSummary] es el resumen del
     * cambio nutricional relevante (`null` = ninguno). Devuelve `false` si no se tocó nada (la fila no es de OFF).
     */
    suspend fun saveRefreshed(product: Product, nowMillis: Long, changeSummary: String?): Boolean {
        save(product, nowMillis)
        return true
    }

    /** Anota que Open Food Facts respondió pero no se aplicó ningún dato nuevo. */
    suspend fun markChecked(barcode: String, nowMillis: Long) {}

    suspend fun refreshInfo(barcode: String): ProductRefreshInfo? = null

    /** Códigos de productos de OFF usados (diario o favoritos) sin consultar desde antes de [staleBeforeMillis]. */
    suspend fun refreshCandidates(staleBeforeMillis: Long, limit: Int): List<String> = emptyList()
}

/** Resultado de [ProductRepository.getByBarcode]. */
sealed interface ProductResult {
    /** [fromCache] indica si viene de la base de datos; [stale] si está caducado (solo tras un fallo de red). */
    data class Found(
        val product: Product,
        val fromCache: Boolean,
        val stale: Boolean = false,
        /** Última confirmación de Open Food Facts (epoch ms), si se conoce. */
        val refreshedAtMillis: Long? = null,
        /** Último cambio nutricional relevante detectado al refrescar, y cuándo. */
        val changeSummary: String? = null,
        val changedAtMillis: Long? = null,
    ) : ProductResult
    data object NotFound : ProductResult
    data class Failure(val cause: Throwable) : ProductResult
}

/** Resultado de [ProductRepository.refresh]. */
sealed interface RefreshResult {
    /** Producto actualizado; [change] resume el cambio nutricional relevante, o es `null` si no lo hubo. */
    data class Updated(val product: Product, val change: String?) : RefreshResult
    /** Open Food Facts devolvió datos peores (sin energía o absurdos): se conserva la copia local. */
    data object Rejected : RefreshResult
    /** Ya no está en Open Food Facts: se conserva la copia local. */
    data object NotFound : RefreshResult
    /** No es un producto de Open Food Facts (genérico, propio) o no está en la caché: no se toca. */
    data object Skipped : RefreshResult
    data class Failure(val cause: Throwable) : RefreshResult
}

/**
 * Busca primero en la base de datos local; si no está o ha caducado, consulta la red,
 * guarda el resultado y lo devuelve. Si la red falla y hay una copia caducada, la devuelve marcada como `stale`.
 */
class ProductRepository(
    private val remote: ProductRemoteSource,
    private val local: ProductLocalStore,
    private val ttl: Duration = DEFAULT_TTL,
    private val nowMillis: () -> Long,
) {
    suspend fun getByBarcode(barcode: String): ProductResult {
        val ean = barcode.trim()
        val cached = try {
            local.find(ean)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // una BD ilegible no debe impedir consultar la red
        }
        // Los genéricos viven solo en local (se siembran con la app): nunca caducan ni existen en Open Food Facts.
        if (ean.startsWith(GenericFoodFormat.KEY_PREFIX)) {
            return cached?.let { ProductResult.Found(it.product, fromCache = true) } ?: ProductResult.NotFound
        }
        val now = nowMillis()
        if (cached != null && now - cached.fetchedAtMillis < ttl.inWholeMilliseconds) {
            return foundFromCache(cached, ean)
        }
        return when (val lookup = remote.findByBarcode(ean)) {
            is ProductLookup.Found ->
                if (cached == null) {
                    safely { local.save(lookup.product, now) } // un fallo al cachear no invalida el producto
                    ProductResult.Found(lookup.product, fromCache = false, refreshedAtMillis = now)
                } else when (val r = applyRefresh(cached.product, lookup.product, now)) {
                    is RefreshResult.Updated -> ProductResult.Found(
                        r.product, fromCache = false, refreshedAtMillis = now,
                        changeSummary = r.change, changedAtMillis = r.change?.let { now },
                    )
                    else -> foundFromCache(cached, ean, stale = true)
                }
            ProductLookup.NotFound -> ProductResult.NotFound
            is ProductLookup.Failure ->
                if (cached != null) foundFromCache(cached, ean, stale = true)
                else ProductResult.Failure(lookup.cause)
        }
    }

    private suspend fun foundFromCache(cached: CachedProduct, ean: String, stale: Boolean = false): ProductResult.Found {
        val info = try {
            local.refreshInfo(ean)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        return ProductResult.Found(
            cached.product, fromCache = true, stale = stale,
            refreshedAtMillis = info?.refreshedAtMillis ?: cached.fetchedAtMillis,
            changeSummary = info?.changeSummary, changedAtMillis = info?.changedAtMillis,
        )
    }

    /**
     * Fuerza una consulta a Open Food Facts (ignora la caducidad) de un producto ya cacheado y aplica el resultado.
     * No toca el diario ni las recetas (guardan su propia copia de los valores), ni genéricos ni alimentos propios.
     */
    suspend fun refresh(barcode: String): RefreshResult {
        val ean = barcode.trim()
        if (ean.isEmpty() || !ean.all { it.isDigit() }) return RefreshResult.Skipped
        val cached = try {
            local.find(ean)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return RefreshResult.Skipped
        val now = nowMillis()
        return when (val lookup = remote.findByBarcode(ean)) {
            is ProductLookup.Found -> applyRefresh(cached.product, lookup.product, now)
            ProductLookup.NotFound -> {
                safely { local.markChecked(ean, now) }
                RefreshResult.NotFound
            }
            is ProductLookup.Failure -> RefreshResult.Failure(lookup.cause)
        }
    }

    /** Códigos de productos de OFF usados en el diario o favoritos cuya última consulta tiene más de [minAge]. */
    suspend fun refreshCandidates(minAge: Duration, limit: Int): List<String> = try {
        local.refreshCandidates(nowMillis() - minAge.inWholeMilliseconds, limit)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

    private suspend fun applyRefresh(old: Product, new: Product, now: Long): RefreshResult {
        // Un dato nuevo sin energía o absurdo no sustituye a uno válido.
        if (new.nutrients.needsReview && !old.nutrients.needsReview) {
            safely { local.markChecked(old.barcode, now) }
            return RefreshResult.Rejected
        }
        val fresh = new.copy(barcode = old.barcode)
        val change = ProductChange.summary(old.nutrients, fresh.nutrients)
        val applied = try {
            local.saveRefreshed(fresh, now, change)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        return if (applied) RefreshResult.Updated(fresh, change) else RefreshResult.Skipped
    }

    private suspend inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    companion object {
        val DEFAULT_TTL: Duration = 30.days
    }
}
