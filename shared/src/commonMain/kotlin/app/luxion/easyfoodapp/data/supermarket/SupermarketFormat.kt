package app.luxion.easyfoodapp.data.supermarket

import app.luxion.easyfoodapp.model.Nutrients
import app.luxion.easyfoodapp.model.Product

/**
 * Formato del recurso `supermarket_products.tsv` (catálogo de supermercados españoles, ver docs/supermarket-catalog.md).
 * Datos de Open Food Facts, © Open Food Facts contributors, licencia ODbL 1.0; este recurso es una base derivada y se
 * ofrece también bajo ODbL.
 *
 * Texto UTF-8, tabuladores: la primera línea es [MAGIC]; las líneas `#` son comentarios; la primera línea sin `#` es la
 * cabecera [COLUMNS]; después una fila por producto, valores por 100 g/ml (celda vacía = no disponible).
 * `code` es el EAN, que se guarda tal cual como clave (así un escaneo lo encuentra sin red).
 */
object SupermarketFormat {
    const val MAGIC = "#easyfood-supermarket-products v1"
    val COLUMNS = listOf(
        "code", "name", "brand", "kcal", "protein_g", "carbs_g", "fat_g", "saturated_fat_g", "sugars_g", "fiber_g",
        "salt_g",
    )

    fun parse(text: String): SupermarketParseResult {
        val lines = text.lineSequence().map { it.trimEnd('\r') }.filter { it.isNotBlank() }.iterator()
        require(lines.hasNext() && lines.next() == MAGIC) { "Formato del catálogo de supermercados no reconocido" }
        var header: String? = null
        while (lines.hasNext()) {
            val line = lines.next()
            if (line.startsWith("#")) continue
            header = line
            break
        }
        require(header?.split('\t') == COLUMNS) { "Cabecera del catálogo de supermercados inesperada" }
        val products = mutableListOf<Product>()
        var skipped = 0
        for (line in lines) {
            if (line.startsWith("#")) continue
            val product = parseRow(line)
            if (product == null) skipped++ else products += product
        }
        return SupermarketParseResult(products, skipped)
    }

    private fun parseRow(line: String): Product? {
        val cells = line.split('\t')
        if (cells.size != COLUMNS.size) return null
        val code = cells[0].trim().takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: return null
        val name = cells[1].trim().takeIf { it.isNotEmpty() } ?: return null
        val numbers = cells.drop(3).map { cell ->
            if (cell.isBlank()) null else cell.toDoubleOrNull()?.takeIf { it >= 0 && it.isFinite() } ?: return null
        }
        val kcal = numbers[0] ?: return null
        return Product(
            barcode = code,
            name = name,
            brand = cells[2].trim().takeIf { it.isNotEmpty() },
            imageUrl = null,
            nutrients = Nutrients(
                kcal = kcal, proteinG = numbers[1], carbsG = numbers[2], fatG = numbers[3],
                saturatedFatG = numbers[4], sugarsG = numbers[5], fiberG = numbers[6], saltG = numbers[7],
            ),
        )
    }
}

/** [skipped] = filas descartadas por estar mal formadas. */
data class SupermarketParseResult(val products: List<Product>, val skipped: Int)
