package com.easyfood.ui.screens

import com.easyfood.data.DiaryStore
import com.easyfood.data.GoalStore
import com.easyfood.data.generic.GenericFoodFormat
import com.easyfood.model.DailyGoals
import com.easyfood.model.DiaryEntry
import com.easyfood.model.MealType
import com.easyfood.model.NewDiaryEntry
import com.easyfood.model.Quantity
import com.easyfood.model.QuantityUnit
import com.easyfood.ui.components.DataSource
import com.easyfood.ui.components.formatGrams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Progreso de un macro frente a su objetivo (`goalG == null` si no hay objetivo). */
data class MacroProgress(val label: String, val consumedG: Double, val goalG: Double?)

/** Una fila del diario ya lista para pintar. */
data class EntryUi(
    val id: Long,
    val barcode: String?,
    val name: String,
    val quantity: Quantity,
    val source: DataSource,
    val kcal: Double?,
    val doubtful: Boolean,
    val servingSizeG: Double?,
)

/** Una comida con sus totales. */
data class MealUi(
    val meal: MealType,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val entries: List<EntryUi>,
)

/** Estado del Diario (docs/design.md §2.1). */
data class DiaryUiState(
    val date: String,
    val goalKcal: Int? = null,
    val consumedKcal: Double = 0.0,
    val macros: List<MacroProgress> = emptyList(),
    val meals: List<MealUi> = emptyList(),
    val loading: Boolean = true,
) {
    val isEmpty: Boolean get() = meals.all { it.entries.isEmpty() }
}

/** Origen del dato deducido de la clave del producto: sin código = propio, `generic:` = genérico. */
fun DiaryEntry.dataSource(): DataSource = when {
    barcode == null -> DataSource.Custom
    barcode.startsWith(GenericFoodFormat.KEY_PREFIX) -> DataSource.Generic
    else -> DataSource.OpenFoodFacts
}

/**
 * Agrega las entradas del día. Las entradas sin dato (porciones sin tamaño o kcal desconocidas) no
 * suman; su fila muestra «—».
 */
fun buildDiaryUiState(date: String, entries: List<DiaryEntry>, goals: DailyGoals): DiaryUiState {
    val meals = MealType.entries.map { meal ->
        val rows = entries.filter { it.meal == meal }
        MealUi(
            meal = meal,
            kcal = rows.sumOf { it.totals.kcal ?: 0.0 },
            proteinG = rows.sumOf { it.totals.proteinG ?: 0.0 },
            carbsG = rows.sumOf { it.totals.carbsG ?: 0.0 },
            fatG = rows.sumOf { it.totals.fatG ?: 0.0 },
            entries = rows.map {
                EntryUi(
                    id = it.id,
                    barcode = it.barcode,
                    name = it.name,
                    quantity = it.quantity,
                    source = it.dataSource(),
                    kcal = it.totals.kcal,
                    doubtful = it.nutrients.needsReview,
                    servingSizeG = it.servingSizeG,
                )
            },
        )
    }
    return DiaryUiState(
        date = date,
        goalKcal = goals.kcal,
        consumedKcal = meals.sumOf { it.kcal },
        macros = listOf(
            MacroProgress("Proteína", meals.sumOf { it.proteinG }, goals.proteinG?.toDouble()),
            MacroProgress("Hidratos", meals.sumOf { it.carbsG }, goals.carbsG?.toDouble()),
            MacroProgress("Grasa", meals.sumOf { it.fatG }, goals.fatG?.toDouble()),
        ),
        meals = meals,
        loading = false,
    )
}

/** Cantidad de una entrada como texto: «30 g» o «2 porciones». */
fun Quantity.format(): String = when (unit) {
    QuantityUnit.GRAMS -> "${formatGrams(amount)} g"
    QuantityUnit.SERVINGS ->
        formatGrams(amount) + if (amount == 1.0) " porción" else " porciones"
}

/**
 * Lógica de la pantalla del Diario: carga el día, cambia de día, edita cantidad y borra (con deshacer).
 * Se crea con el [CoroutineScope] de la composición.
 */
class DiaryPresenter(
    private val diary: DiaryStore,
    private val goals: GoalStore,
    private val scope: CoroutineScope,
    today: String,
) {
    private val _state = MutableStateFlow(DiaryUiState(date = today))
    val state: StateFlow<DiaryUiState> = _state.asStateFlow()

    private var removed: Pair<NewDiaryEntry, Long>? = null

    fun load(date: String = _state.value.date) {
        scope.launch {
            _state.value = buildDiaryUiState(date, diary.entriesForDate(date), goals.get())
        }
    }

    fun shiftDay(days: Int) = load(IsoDate.plusDays(_state.value.date, days))

    fun updateQuantity(id: Long, quantity: Quantity) {
        scope.launch {
            val entry = diary.find(id) ?: return@launch
            diary.update(id, entry.meal, quantity)
            load()
        }
    }

    /** Borra la entrada y recuerda sus datos para [undoDelete]. */
    fun delete(id: Long) {
        scope.launch {
            val entry = diary.find(id) ?: return@launch
            diary.delete(id)
            removed = NewDiaryEntry(
                entry.date, entry.meal, entry.barcode, entry.name, entry.quantity, entry.servingSizeG, entry.nutrients,
            ) to entry.createdAtMillis
            load()
        }
    }

    fun undoDelete() {
        val (entry, createdAt) = removed ?: return
        removed = null
        scope.launch {
            diary.add(entry, createdAt)
            load()
        }
    }
}

/** Aritmética mínima de fechas ISO (`YYYY-MM-DD`) en el calendario gregoriano, sin dependencias. */
object IsoDate {
    private val monthNames = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
    )

    private fun isLeap(y: Int) = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0

    private fun daysInMonth(y: Int, m: Int) = when (m) {
        2 -> if (isLeap(y)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    private fun parse(date: String): Triple<Int, Int, Int> {
        val (y, m, d) = date.split("-").map { it.toInt() }
        return Triple(y, m, d)
    }

    fun plusDays(date: String, days: Int): String {
        var (y, m, d) = parse(date)
        d += days
        while (d > daysInMonth(y, m)) {
            d -= daysInMonth(y, m)
            if (++m > 12) { m = 1; y++ }
        }
        while (d < 1) {
            if (--m < 1) { m = 12; y-- }
            d += daysInMonth(y, m)
        }
        return "${y.toString().padStart(4, '0')}-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
    }

    /** «4 de noviembre de 2026». */
    fun long(date: String): String {
        val (y, m, d) = parse(date)
        return "$d de ${monthNames[m - 1]} de $y"
    }
}
