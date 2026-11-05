package app.luxion.fitarchiveapp.data

import app.luxion.fitarchiveapp.data.generic.GenericFoodFormat
import app.luxion.fitarchiveapp.model.Product
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/** Fuente remota de productos (Open Food Facts). */
interface ProductRemoteSource {
    suspend fun findByBarcode(barcode: String): ProductLookup
}

/** Producto guardado en local junto con el instante (epoch ms) en que se descargó. */
data class CachedProduct(val product: Product, val fetchedAtMillis: Long)

/** Acceso a la caché local de productos (la implementación real será SQLDelight). */
interface ProductLocalStore {
    suspend fun find(barcode: String): CachedProduct?
    suspend fun save(product: Product, fetchedAtMillis: Long)
}

/** Resultado de [ProductRepository.getByBarcode]. */
sealed interface ProductResult {
    /** [fromCache] indica si viene de la base de datos; [stale] si está caducado (solo tras un fallo de red). */
    data class Found(val product: Product, val fromCache: Boolean, val stale: Boolean = false) : ProductResult
    data object NotFound : ProductResult
    data class Failure(val cause: Throwable) : ProductResult
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
        } catch (e: Exception) {
            null // una BD ilegible no debe impedir consultar la red
        }
        // Los genéricos viven solo en local (se siembran con la app): nunca caducan ni existen en Open Food Facts.
        if (ean.startsWith(GenericFoodFormat.KEY_PREFIX)) {
            return cached?.let { ProductResult.Found(it.product, fromCache = true) } ?: ProductResult.NotFound
        }
        val now = nowMillis()
        // Los genéricos no existen en Open Food Facts ni caducan: solo se actualizan con la app.
        val neverExpires = ean.startsWith(GenericFoodFormat.KEY_PREFIX)
        if (cached != null && (neverExpires || now - cached.fetchedAtMillis < ttl.inWholeMilliseconds)) {
            return ProductResult.Found(cached.product, fromCache = true)
        }
        return when (val lookup = remote.findByBarcode(ean)) {
            is ProductLookup.Found -> {
                try {
                    local.save(lookup.product, now)
                } catch (_: Exception) {
                    // fallo al cachear: el producto sigue siendo válido
                }
                ProductResult.Found(lookup.product, fromCache = false)
            }
            ProductLookup.NotFound -> ProductResult.NotFound
            is ProductLookup.Failure ->
                if (cached != null) ProductResult.Found(cached.product, fromCache = true, stale = true)
                else ProductResult.Failure(lookup.cause)
        }
    }

    companion object {
        val DEFAULT_TTL: Duration = 30.days
    }
}
