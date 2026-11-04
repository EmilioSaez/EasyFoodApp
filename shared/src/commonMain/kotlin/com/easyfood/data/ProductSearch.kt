package com.easyfood.data

import com.easyfood.db.ProductSearchQueries
import com.easyfood.model.Product
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Búsqueda de texto sobre los productos guardados en local. */
interface ProductSearchStore {
    suspend fun search(text: String, limit: Int = ProductSearchQuery.DEFAULT_LIMIT): List<Product>
}

/** Convierte texto libre del usuario en una expresión FTS5 segura (prefijo en cada término, AND implícito). */
object ProductSearchQuery {
    const val DEFAULT_LIMIT = 50

    /** `null` si el texto no contiene ningún término buscable. */
    fun toFtsExpression(text: String): String? {
        val terms = text.split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return null
        return terms.joinToString(" ") { "\"$it\"*" }
    }
}

/** [ProductSearchStore] respaldado por el índice FTS5 `ProductFts`. */
class SqlDelightProductSearchStore(
    private val queries: ProductSearchQueries,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ProductSearchStore {
    override suspend fun search(text: String, limit: Int): List<Product> {
        val expression = ProductSearchQuery.toFtsExpression(text) ?: return emptyList()
        return withContext(dispatcher) {
            queries.search(expression, limit.toLong().coerceAtLeast(0)).executeAsList().map { it.toCached().product }
        }
    }
}
