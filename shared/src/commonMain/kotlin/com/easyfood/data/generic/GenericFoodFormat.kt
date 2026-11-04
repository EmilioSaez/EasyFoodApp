package com.easyfood.data.generic

import com.easyfood.model.Nutrients
import com.easyfood.model.Product

/**
 * Formato compacto del recurso `generic_foods.tsv` (base genérica de alimentos, ver docs/generic-foods.md).
 *
 * Texto UTF-8, una fila por alimento, columnas separadas por tabulador:
 * - Líneas que empiezan por `#` son metadatos/comentarios; la primera debe ser `#easyfood-generic-foods v1`.
 * - Primera línea no comentada: cabecera de columnas (se valida).
 * - Resto: `id, name, kcal, protein_g, carbs_g, fat_g, saturated_fat_g, sugars_g, fiber_g, salt_g`.
 *   Valores por 100 g; celda vacía = dato no disponible. `id` es el identificador de la fuente (FDC id).
 */
object GenericFoodFormat {
    const val MAGIC = "#easyfood-generic-foods v1"
    val COLUMNS = listOf(
        "id", "name", "kcal", "protein_g", "carbs_g", "fat_g", "saturated_fat_g", "sugars_g", "fiber_g", "salt_g",
    )

    /** Prefijo de la clave (columna `barcode` de `ProductEntity`) de los alimentos genéricos; no choca con un EAN. */
    const val KEY_PREFIX = "generic:"

    fun parse(text: String): GenericFoodParseResult {
        val lines = text.lineSequence().map { it.trimEnd('\r') }.filter { it.isNotBlank() }.iterator()
        require(lines.hasNext() && lines.next() == MAGIC) { "Formato de base genérica no reconocido" }
        var header: String? = null
        while (lines.hasNext()) {
            val line = lines.next()
            if (line.startsWith("#")) continue
            header = line
            break
        }
        require(header?.split('\t') == COLUMNS) { "Cabecera de base genérica inesperada" }

        val foods = mutableListOf<GenericFood>()
        var skipped = 0
        for (line in lines) {
            if (line.startsWith("#")) continue
            val food = parseRow(line)
            if (food == null) skipped++ else foods += food
        }
        return GenericFoodParseResult(foods, skipped)
    }

    private fun parseRow(line: String): GenericFood? {
        val cells = line.split('\t')
        if (cells.size != COLUMNS.size) return null
        val id = cells[0].trim().takeIf { it.isNotEmpty() } ?: return null
        val name = cells[1].trim().takeIf { it.isNotEmpty() } ?: return null
        val numbers = cells.drop(2).map { cell ->
            if (cell.isBlank()) null else cell.toDoubleOrNull()?.takeIf { it >= 0 && it.isFinite() } ?: return null
        }
        val kcal = numbers[0] ?: return null
        return GenericFood(
            id = id,
            name = name,
            nutrients = Nutrients(
                kcal = kcal,
                proteinG = numbers[1],
                carbsG = numbers[2],
                fatG = numbers[3],
                saturatedFatG = numbers[4],
                sugarsG = numbers[5],
                fiberG = numbers[6],
                saltG = numbers[7],
            ),
        )
    }
}

/** Alimento de la base genérica, valores por 100 g. */
data class GenericFood(val id: String, val name: String, val nutrients: Nutrients) {
    fun toProduct() = Product(
        barcode = GenericFoodFormat.KEY_PREFIX + id,
        name = name,
        brand = null,
        imageUrl = null,
        nutrients = nutrients,
    )
}

/** [skipped] = filas descartadas por estar mal formadas (columnas de menos, números inválidos, sin kcal…). */
data class GenericFoodParseResult(val foods: List<GenericFood>, val skipped: Int)
