package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.QuickEntry
import app.luxion.fitarchiveapp.ui.components.formatGrams
import app.luxion.fitarchiveapp.ui.components.parseNumber
import kotlin.math.roundToInt

/** Texto de cada campo de la entrada rápida (docs/design.md §2.16). Todos son opcionales salvo que debe haber algún valor. */
data class QuickEntryFormState(
    val name: String = "",
    val kcal: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
)

/** Errores por campo (`null` = sin error); [empty] se da si no hay ningún valor informado. */
data class QuickEntryFormErrors(
    val kcal: String? = null,
    val protein: String? = null,
    val carbs: String? = null,
    val fat: String? = null,
    val empty: Boolean = false,
) {
    val hasErrors: Boolean get() = empty || listOf(kcal, protein, carbs, fat).any { it != null }
}

private const val MAX_KCAL = 10_000.0
private const val MAX_GRAMS = 2_000.0

/** Valida el formulario: números ≥ 0 en rango y al menos un valor (kcal o algún macro). */
fun validateQuickEntry(form: QuickEntryFormState): QuickEntryFormErrors {
    fun check(text: String, max: Double, maxMsg: String): String? {
        if (text.isBlank()) return null
        val v = parseNumber(text) ?: return "Número no válido"
        return when {
            !v.isFinite() || v < 0 -> "Número no válido"
            v > max -> maxMsg
            else -> null
        }
    }
    val texts = listOf(form.kcal, form.protein, form.carbs, form.fat)
    return QuickEntryFormErrors(
        kcal = check(form.kcal, MAX_KCAL, "Máximo ${formatGrams(MAX_KCAL)} kcal"),
        protein = check(form.protein, MAX_GRAMS, "Máximo ${formatGrams(MAX_GRAMS)} g"),
        carbs = check(form.carbs, MAX_GRAMS, "Máximo ${formatGrams(MAX_GRAMS)} g"),
        fat = check(form.fat, MAX_GRAMS, "Máximo ${formatGrams(MAX_GRAMS)} g"),
        empty = texts.all { it.isBlank() },
    )
}

/** Totales a guardar, o `null` si el formulario no es válido. */
fun QuickEntryFormState.toTotals(): Nutrients? {
    if (validateQuickEntry(this).hasErrors) return null
    return QuickEntry.nutrients(parseNumber(kcal), parseNumber(protein), parseNumber(carbs), parseNumber(fat))
}

/** Nombre a guardar: el escrito o, si está vacío, [QuickEntry.DEFAULT_NAME]. */
fun QuickEntryFormState.resolvedName(): String = name.trim().ifEmpty { QuickEntry.DEFAULT_NAME }

/** Número sin pérdida de precisión y con «,» como separador. */
private fun formatField(v: Double?): String =
    v?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString().replace('.', ',') } ?: ""

/** Formulario relleno con una entrada rápida existente (el nombre por defecto se deja vacío). */
fun quickEntryFormStateOf(name: String, totals: Nutrients) = QuickEntryFormState(
    name = if (name == QuickEntry.DEFAULT_NAME) "" else name,
    kcal = formatField(totals.kcal),
    protein = formatField(totals.proteinG),
    carbs = formatField(totals.carbsG),
    fat = formatField(totals.fatG),
)

/** Macros de una entrada rápida en la fila del diario: «17 P | 62 H | 8 G» (solo los informados); vacío si ninguno. */
fun quickMacrosText(proteinG: Double?, carbsG: Double?, fatG: Double?): String = listOfNotNull(
    proteinG?.let { "${it.roundToInt()} P" },
    carbsG?.let { "${it.roundToInt()} H" },
    fatG?.let { "${it.roundToInt()} G" },
).joinToString(" | ")
