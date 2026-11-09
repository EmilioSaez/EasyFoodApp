package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.data.DiaryStore
import app.luxion.fitarchiveapp.data.GoalStore
import app.luxion.fitarchiveapp.data.generic.GenericFoodFormat
import app.luxion.fitarchiveapp.domain.MealPlan
import app.luxion.fitarchiveapp.model.DailyGoals
import app.luxion.fitarchiveapp.model.DiaryEntry
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.NewDiaryEntry
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import app.luxion.fitarchiveapp.ui.components.DataSource
import app.luxion.fitarchiveapp.ui.components.formatGrams
import kotlinx.coroutines.CancellationException
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
    /** La carga del día falló (base de datos ilegible): se muestra un estado de error con «Reintentar». */
    val loadFailed: Boolean = false,
    /** Fallo puntual de una acción (editar, borrar, deshacer); la UI lo enseña una vez y llama a [DiaryPresenter.consumeMessage]. */
    val message: String? = null,
) {
    val isEmpty: Boolean get() = meals.all { it.entries.isEmpty() }
}

/** Origen del dato deducido de la clave del producto: sin código = propio, `generic:` = genérico. */
fun DiaryEntry.dataSource(): DataSource = dataSourceOf(barcode)

/** Origen del dato según la clave: `null` o `custom:` = propio, `generic:` = genérico, otra = Open Food Facts. */
fun dataSourceOf(key: String?): DataSource = when {
    key == null || key.startsWith(CUSTOM_FOOD_KEY_PREFIX) -> DataSource.Custom
    key.startsWith(GenericFoodFormat.KEY_PREFIX) -> DataSource.Generic
    else -> DataSource.OpenFoodFacts
}

/**
 * Agrega las entradas del día en las secciones de [mealsPerDay] comidas al día (`null` = las de por defecto). Las entradas sin dato (porciones sin tamaño o kcal desconocidas) no
 * suman; su fila muestra «—».
 */
fun buildDiaryUiState(date: String, entries: List<DiaryEntry>, goals: DailyGoals, mealsPerDay: Int? = null): DiaryUiState {
    // Solo las comidas del preset, más las que ya no lo son pero tienen entradas (no se oculta ningún dato).
    val meals = MealPlan.visible(mealsPerDay, entries.mapTo(mutableSetOf()) { it.meal }).map { meal ->
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
    private val mealsPerDay: Int? = null,
) {
    private val _state = MutableStateFlow(DiaryUiState(date = today))
    val state: StateFlow<DiaryUiState> = _state.asStateFlow()

    private var removed: Pair<NewDiaryEntry, Long>? = null

    fun load(date: String = _state.value.date) {
        scope.launch {
            _state.value = try {
                buildDiaryUiState(date, diary.entriesForDate(date), goals.get(), mealsPerDay)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DiaryUiState(date = date, loading = false, loadFailed = true)
            }
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun fail(message: String) {
        _state.value = _state.value.copy(message = message)
    }

    fun shiftDay(days: Int) = load(IsoDate.plusDays(_state.value.date, days))

    fun updateQuantity(id: Long, quantity: Quantity) {
        scope.launch {
            try {
                val entry = diary.find(id) ?: return@launch
                diary.update(id, entry.meal, quantity)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(SAVE_ERROR)
                return@launch
            }
            load()
        }
    }

    /** Borra la entrada y recuerda sus datos para [undoDelete]. */
    fun delete(id: Long) {
        scope.launch {
            try {
                val entry = diary.find(id) ?: return@launch
                diary.delete(id)
                removed = NewDiaryEntry(
                    entry.date, entry.meal, entry.barcode, entry.name, entry.quantity, entry.servingSizeG, entry.nutrients,
                ) to entry.createdAtMillis
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(DELETE_ERROR)
                return@launch
            }
            load()
        }
    }

    fun undoDelete() {
        val (entry, createdAt) = removed ?: return
        removed = null
        scope.launch {
            try {
                diary.add(entry, createdAt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                removed = entry to createdAt
                fail(UNDO_ERROR)
                return@launch
            }
            load()
        }
    }

    companion object {
        const val SAVE_ERROR = "No se pudo guardar el cambio. Inténtalo de nuevo."
        const val DELETE_ERROR = "No se pudo borrar. Inténtalo de nuevo."
        const val UNDO_ERROR = "No se pudo deshacer el borrado."
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
