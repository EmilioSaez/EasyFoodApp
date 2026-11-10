package app.luxion.fitarchiveapp.data

/**
 * Utilidades de texto para la búsqueda de alimentos (docs/design.md §2.2): normalización como la del tokenizador FTS
 * (`unicode61 remove_diacritics 2`), plurales, erratas y supermercados. Puro Kotlin: lo comparten el FTS y los
 * alimentos propios, que se filtran en memoria.
 */
object SearchText {
    /** Minúsculas y sin diacríticos (incluidas ñ y ç, como hace el tokenizador del índice). */
    fun normalize(text: String): String = buildString(text.length) {
        for (c in text.lowercase()) append(fold(c))
    }

    /** Términos normalizados de [text]: letras y dígitos, el resto separa. */
    fun terms(text: String): List<String> =
        normalize(text).split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }

    /**
     * Raíz aproximada de un término para tolerar plurales: `manzanas` → `manzana`, `limones` → `limon`,
     * `lapices` → `lapiz`. Se usa como prefijo, así que no necesita ser una palabra real. Los términos cortos
     * no se tocan.
     */
    fun stem(term: String): String = when {
        term.length > 4 && term.endsWith("ces") -> term.dropLast(3) + "z"
        term.length > 4 && term.endsWith("es") -> term.dropLast(2)
        term.length > 3 && term.endsWith("s") -> term.dropLast(1)
        else -> term
    }

    /** Errores tolerados al escribir [term]: ninguno en palabras muy cortas, 1 hasta 5 letras, 2 a partir de 6. */
    fun maxTypos(term: String): Int = when {
        term.length < 4 -> 0
        term.length <= 5 -> 1
        else -> 2
    }

    /** `true` si [candidate] es [term] con erratas o lo es su comienzo (el usuario aún está escribiendo). */
    fun isCloseTo(term: String, candidate: String): Boolean {
        val k = maxTypos(term)
        if (k == 0) return false
        if (kotlin.math.abs(term.length - candidate.length) <= k && distance(term, candidate, k) <= k) return true
        return candidate.length > term.length && distance(term, candidate.take(term.length), k) <= k
    }

    /** Distancia de Damerau-Levenshtein (con transposiciones adyacentes); corta en cuanto supera [limit]. */
    fun distance(a: String, b: String, limit: Int = Int.MAX_VALUE): Int {
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
        var prev2 = IntArray(b.length + 1)
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, prev2[j - 2] + 1)
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > limit) return limit + 1
            prev2 = prev
            prev = cur
        }
        return prev[b.length]
    }

    /**
     * Marcas (en minúsculas y sin acentos) que identifican a cada supermercado, para buscar «mercadona» o «lidl»
     * aunque en la ficha figure «Hacendado» o «Milbona». La clave y los valores se buscan como palabra completa en la
     * marca del producto.
     */
    val supermarkets: Map<String, List<String>> = mapOf(
        "mercadona" to listOf("mercadona", "hacendado", "deliplus", "bosque verde", "compy"),
        "hacendado" to listOf("hacendado", "mercadona"),
        "carrefour" to listOf("carrefour"),
        "dia" to listOf("dia"),
        "lidl" to listOf("lidl", "milbona", "deluxe"),
        "eroski" to listOf("eroski"),
        "alcampo" to listOf("alcampo", "auchan"),
        "aldi" to listOf("aldi", "milsani"),
        "consum" to listOf("consum"),
    )

    private fun fold(c: Char): Char = when (c) {
        'á', 'à', 'ä', 'â', 'ã', 'å' -> 'a'
        'é', 'è', 'ë', 'ê' -> 'e'
        'í', 'ì', 'ï', 'î' -> 'i'
        'ó', 'ò', 'ö', 'ô', 'õ' -> 'o'
        'ú', 'ù', 'ü', 'û' -> 'u'
        'ñ' -> 'n'
        'ç' -> 'c'
        else -> c
    }
}
