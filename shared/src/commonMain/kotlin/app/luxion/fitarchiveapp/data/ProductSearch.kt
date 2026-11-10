package app.luxion.fitarchiveapp.data

import app.luxion.fitarchiveapp.db.ProductSearchQueries
import app.luxion.fitarchiveapp.model.Product
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Búsqueda de texto sobre los productos guardados en local. */
interface ProductSearchStore {
    /**
     * Productos que encajan con [text] (nombre o marca; tolera acentos, plurales y erratas; «mercadona», «lidl»… buscan
     * por la marca del supermercado), los mejores primero y, a igual relevancia, los que más se usan en el diario.
     * Con [onlyMine] solo los productos consultados o favoritos, sin los de la base genérica no marcados.
     */
    suspend fun search(
        text: String,
        limit: Int = ProductSearchQuery.DEFAULT_LIMIT,
        onlyMine: Boolean = false,
    ): List<Product>
}

/** Términos indexados (nombre y marca, normalizados) con el nº de productos que los contienen. */
class FoodVocabulary(counts: Map<String, Int>) {
    private val terms: List<String> = counts.keys.sorted()
    private val docs: Map<String, Int> = counts

    /** `true` si algún término empieza por [prefix]. */
    fun hasPrefix(prefix: String): Boolean {
        var lo = 0
        var hi = terms.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (terms[mid] < prefix) lo = mid + 1 else hi = mid
        }
        return lo < terms.size && terms[lo].startsWith(prefix)
    }

    /** Hasta [max] términos parecidos a [term] (misma letra inicial), los más frecuentes primero. */
    fun closeTo(term: String, max: Int = MAX_CORRECTIONS): List<String> {
        if (term.isEmpty() || SearchText.maxTypos(term) == 0) return emptyList()
        return terms.asSequence()
            .filter { it[0] == term[0] && SearchText.isCloseTo(term, it) }
            .sortedByDescending { docs[it] ?: 0 }
            .take(max)
            .toList()
    }

    companion object {
        const val MAX_CORRECTIONS = 4

        fun of(rows: Iterable<Pair<String, String?>>): FoodVocabulary {
            val counts = HashMap<String, Int>()
            for ((name, brand) in rows) {
                for (t in (SearchText.terms(name) + SearchText.terms(brand.orEmpty())).toSet()) {
                    counts[t] = (counts[t] ?: 0) + 1
                }
            }
            return FoodVocabulary(counts)
        }
    }
}

/** Convierte texto libre del usuario en una expresión FTS5 segura (prefijo en cada término, unidos con AND). */
object ProductSearchQuery {
    const val DEFAULT_LIMIT = 50

    /**
     * `null` si el texto no contiene ningún término buscable. Cada término se busca como prefijo, junto con su raíz
     * (plurales) y, si ningún término indexado empieza así y hay [vocabulary], con las correcciones más parecidas.
     * Un supermercado conocido («mercadona») se busca en la marca, junto con sus marcas blancas.
     */
    fun toFtsExpression(text: String, vocabulary: FoodVocabulary? = null): String? {
        val terms = SearchText.terms(text)
        if (terms.isEmpty()) return null
        return terms.joinToString(" AND ") { term ->
            val brands = SearchText.supermarkets[term]
            if (brands != null) {
                brands.joinToString(" OR ", "(", ")") { "brand : \"$it\"" }
            } else {
                val stem = SearchText.stem(term)
                val options = linkedSetOf(stem)
                if (vocabulary != null && !vocabulary.hasPrefix(stem)) options += vocabulary.closeTo(stem)
                if (options.size == 1) "\"$stem\"*" else options.joinToString(" OR ", "(", ")") { "\"$it\"*" }
            }
        }
    }
}

/** [ProductSearchStore] respaldado por el índice FTS5 `ProductFts`. */
class SqlDelightProductSearchStore(
    private val queries: ProductSearchQueries,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ProductSearchStore {
    private val vocabularyLock = Mutex()
    private var vocabularyStamp: List<Long?>? = null
    private var vocabulary: FoodVocabulary? = null

    override suspend fun search(text: String, limit: Int, onlyMine: Boolean): List<Product> {
        if (SearchText.terms(text).isEmpty()) return emptyList()
        return withContext(dispatcher) {
            val expression = ProductSearchQuery.toFtsExpression(text, currentVocabulary()) ?: return@withContext emptyList()
            queries.search(expression, if (onlyMine) 1L else 0L, limit.toLong().coerceAtLeast(0))
                .executeAsList().map { it.toCached().product }
        }
    }

    /** Vocabulario en memoria; se reconstruye solo si cambió la caché de productos (alta, baja o edición). */
    private suspend fun currentVocabulary(): FoodVocabulary = vocabularyLock.withLock {
        val stamp = queries.searchVocabularyStamp().executeAsOne().let { listOf(it.n, it.max_rowid, it.cached_sum) }
        vocabulary?.takeIf { stamp == vocabularyStamp } ?: FoodVocabulary.of(
            queries.searchVocabulary().executeAsList().map { it.name to it.brand },
        ).also {
            vocabulary = it
            vocabularyStamp = stamp
        }
    }
}
