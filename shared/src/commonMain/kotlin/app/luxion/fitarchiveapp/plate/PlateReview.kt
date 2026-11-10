package app.luxion.fitarchiveapp.plate

import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.NewDiaryEntry
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import app.luxion.fitarchiveapp.model.QuickEntry
import app.luxion.fitarchiveapp.ui.screens.FoodPick

/** De dónde salen los valores nutricionales de un alimento de la revisión. */
sealed interface PlateSource {
    /** Enlazado con la base (genérica, producto consultado o propio): kcal y macros reales, por 100 g. */
    data class Linked(val pick: FoodPick) : PlateSource

    /** Sin coincidencia en la base: valores por 100 g que supone el modelo. Se marcan siempre como estimados. */
    data class Estimated(val per100g: Nutrients) : PlateSource

    /** Entrada rápida escrita a mano: [totals] son los de toda la entrada, no por 100 g. */
    data class Manual(val totals: Nutrients) : PlateSource
}

/**
 * Duda de la IA sobre un alimento, pendiente de que el usuario responda (o la deje como está). [busy] mientras se
 * re-consulta; [error] si esa consulta falló (se puede reintentar). [askedGrams] son los gramos del alimento al
 * preguntar: si el usuario los ha cambiado, la respuesta de la IA no los pisa.
 */
data class PlateQuestion(
    val text: String,
    val options: List<String> = emptyList(),
    val askedGrams: Double = 0.0,
    val busy: Boolean = false,
    val error: String? = null,
)

/** Un alimento de la pantalla de revisión; todo es editable (ver [PlateReview]). */
data class PlateItem(
    val id: Int,
    val name: String,
    val grams: Double,
    val source: PlateSource,
    val confidence: PlateConfidence? = null,
    val portion: String? = null,
    /** El enlace se encontró quitando palabras de la búsqueda: conviene revisarlo. */
    val looseMatch: Boolean = false,
    /** Otros alimentos de la base que encajan, para «Cambiar alimento». */
    val alternatives: List<FoodPick> = emptyList(),
    /** La IA no sabe qué es: pregunta al usuario. `null` si no hay duda o ya se resolvió. */
    val question: PlateQuestion? = null,
) {
    val isEstimated: Boolean get() = source is PlateSource.Estimated

    /** Valores totales de este alimento (los de [grams] gramos); `null` en un dato que falte. */
    val totals: Nutrients
        get() = when (val s = source) {
            is PlateSource.Linked -> s.pick.nutrients.scaled(grams)
            is PlateSource.Estimated -> s.per100g.scaled(grams)
            is PlateSource.Manual -> s.totals
        }

    /** Entrada del diario: enlazado = entrada normal en gramos; estimado o manual = entrada rápida. */
    fun toEntry(date: String, meal: MealType): NewDiaryEntry = when (val s = source) {
        is PlateSource.Linked -> NewDiaryEntry(
            date = date,
            meal = meal,
            barcode = s.pick.barcode,
            name = s.pick.result.name,
            quantity = Quantity(grams, QuantityUnit.GRAMS),
            servingSizeG = s.pick.servingSizeG,
            nutrients = s.pick.nutrients,
        )
        is PlateSource.Estimated -> totals.let { QuickEntry.new(date, meal, "$name (estimado)", it.kcal, it.proteinG, it.carbsG, it.fatG) }
        is PlateSource.Manual -> s.totals.let { QuickEntry.new(date, meal, name, it.kcal, it.proteinG, it.carbsG, it.fatG) }
    }
}

/** Suma de la revisión. [missingKcal] cuenta los alimentos sin kcal conocidas (no entran en la suma). */
data class PlateTotals(val kcal: Double, val proteinG: Double, val carbsG: Double, val fatG: Double, val missingKcal: Int)

/**
 * Lista editable de alimentos de una foto: cambiar alimento o cantidad, borrar y añadir. Nada se guarda hasta que
 * se confirma; es un valor inmutable, cada edición devuelve la lista nueva.
 */
data class PlateReview(val items: List<PlateItem>, private val nextId: Int = (items.maxOfOrNull { it.id } ?: -1) + 1) {
    val totals: PlateTotals
        get() = PlateTotals(
            kcal = items.sumOf { it.totals.kcal ?: 0.0 },
            proteinG = items.sumOf { it.totals.proteinG ?: 0.0 },
            carbsG = items.sumOf { it.totals.carbsG ?: 0.0 },
            fatG = items.sumOf { it.totals.fatG ?: 0.0 },
            missingKcal = items.count { it.totals.kcal == null },
        )

    val estimatedCount: Int get() = items.count { it.isEstimated }

    val hasQuestions: Boolean get() = items.any { it.question != null }

    fun setGrams(id: Int, grams: Double): PlateReview {
        require(grams > 0 && grams.isFinite()) { "La cantidad debe ser positiva" }
        return map(id) { it.copy(grams = grams) }
    }

    /** Enlaza el alimento con [pick] (de la base): pasa a tener datos reales y se mantiene la cantidad. */
    fun link(id: Int, pick: FoodPick): PlateReview =
        map(id) { it.copy(name = pick.result.name, source = PlateSource.Linked(pick), looseMatch = false, question = null) }

    /** Deja de preguntar por el alimento: se queda como está (entrada manual o estimada). */
    fun dismissQuestion(id: Int): PlateReview = map(id) { it.copy(question = null) }

    /** Cambia el estado de la pregunta del alimento (ocupada, con error...); no hace nada si ya no hay pregunta. */
    fun updateQuestion(id: Int, f: (PlateQuestion) -> PlateQuestion): PlateReview =
        map(id) { item -> item.question?.let { item.copy(question = f(it)) } ?: item }

    /** Sustituye el alimento por el ya identificado con la respuesta (sin pregunta). */
    fun replace(id: Int, resolved: PlateItem): PlateReview = map(id) { resolved.copy(id = id, question = null) }

    fun remove(id: Int): PlateReview = copy(items = items.filterNot { it.id == id })

    /** Añade un alimento enlazado con la base, con [grams] gramos. */
    fun addLinked(pick: FoodPick, grams: Double): PlateReview = withNew { id ->
        PlateItem(id, pick.result.name, grams.coerceIn(PlateAnalyzer.MIN_GRAMS, PlateAnalyzer.MAX_GRAMS), PlateSource.Linked(pick))
    }

    /** Añade una entrada rápida con totales escritos a mano. */
    fun addManual(name: String, totals: Nutrients): PlateReview = withNew { id ->
        PlateItem(id, name.trim().ifEmpty { QuickEntry.DEFAULT_NAME }, QuickEntry.QUANTITY_G, PlateSource.Manual(totals))
    }

    private fun map(id: Int, f: (PlateItem) -> PlateItem) = copy(items = items.map { if (it.id == id) f(it) else it })

    private fun withNew(make: (Int) -> PlateItem) = copy(items = items + make(nextId), nextId = nextId + 1)
}

/** Valores escalados a [grams] gramos desde valores por 100 g (`null` se conserva). */
internal fun Nutrients.scaled(grams: Double): Nutrients {
    fun s(v: Double?) = v?.let { it * grams / 100.0 }
    return Nutrients(
        kcal = s(kcal), proteinG = s(proteinG), carbsG = s(carbsG), fatG = s(fatG),
        saturatedFatG = s(saturatedFatG), sugarsG = s(sugarsG), fiberG = s(fiberG), saltG = s(saltG),
    )
}
