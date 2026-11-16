package app.luxion.easyfoodapp.ui.screens

import app.luxion.easyfoodapp.data.DiaryStore
import app.luxion.easyfoodapp.data.GoalStore
import app.luxion.easyfoodapp.data.MealTemplateStore
import app.luxion.easyfoodapp.model.NewMealTemplate
import app.luxion.easyfoodapp.model.TemplateItem
import app.luxion.easyfoodapp.data.generic.GenericFoodFormat
import app.luxion.easyfoodapp.domain.DayStatus
import app.luxion.easyfoodapp.domain.DiaryCopy
import app.luxion.easyfoodapp.domain.DaySummary
import app.luxion.easyfoodapp.domain.IntakeGuidelines
import app.luxion.easyfoodapp.domain.MealPlan
import app.luxion.easyfoodapp.domain.NutrientIntake
import app.luxion.easyfoodapp.model.DailyGoals
import app.luxion.easyfoodapp.model.DiaryEntry
import app.luxion.easyfoodapp.model.MealType
import app.luxion.easyfoodapp.model.NewDiaryEntry
import app.luxion.easyfoodapp.model.epochDayToIsoDate
import app.luxion.easyfoodapp.model.isoDateToEpochDay
import app.luxion.easyfoodapp.model.Quantity
import app.luxion.easyfoodapp.model.QuantityUnit
import app.luxion.easyfoodapp.ui.components.DataSource
import app.luxion.easyfoodapp.ui.components.formatGrams
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
    /** Entrada rápida: sin alimento; [macrosText] sustituye a la cantidad en la fila. */
    val quick: Boolean = false,
    val macrosText: String = "",
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

/** Un día de la franja semanal (docs/design.md §2.12). */
data class WeekDayUi(
    val date: String,
    /** Inicial del día: L M X J V S D. */
    val initial: String,
    val dayNumber: Int,
    val status: DayStatus,
    val selected: Boolean,
)

/** Estado del Diario (docs/design.md §2.1 y §2.12). */
data class DiaryUiState(
    val date: String,
    /** Hoy, para saber si [date] es el día de hoy. */
    val today: String = date,
    /** Los siete días (lunes a domingo) de la semana de [date]. */
    val week: List<WeekDayUi> = emptyList(),
    /** Días completados consecutivos que terminan hoy (0 = sin racha). */
    val streak: Int = 0,
    /** Objetivo de kcal del día. */
    val goalKcal: Int? = null,
    /** Kcal de cada día con registro del historial (hasta el fin de la semana mostrada o hoy): el calendario mensual lo usa. */
    val summaries: Map<String, DaySummary> = emptyMap(),
    val consumedKcal: Double = 0.0,
    val macros: List<MacroProgress> = emptyList(),
    val meals: List<MealUi> = emptyList(),
    /** Fibra, sal, azúcares y saturadas del día (vacío si el día no tiene entradas). */
    val intake: List<NutrientIntake> = emptyList(),
    val loading: Boolean = true,
    /** La carga del día falló (base de datos ilegible): se muestra un estado de error con «Reintentar». */
    val loadFailed: Boolean = false,
    /** Aviso puntual de una acción (fallo al editar, borrar o deshacer; confirmación o fallo al copiar); la UI lo enseña una vez y llama a [DiaryPresenter.consumeMessage]. */
    val message: String? = null,
) {
    val isEmpty: Boolean get() = meals.all { it.entries.isEmpty() }
}

/**
 * Error de destino al copiar (o `null` si es válido): copiar una comida sobre sí misma, o el día sobre sí mismo, duplicaría las entradas.
 * [meal] es `null` al copiar el día; [targetMeal] `null` = la equivalente. Lo usa el diálogo para avisar sin cerrarse.
 */
fun copyTargetError(meal: MealType?, sourceDate: String, targetDate: String, targetMeal: MealType?): String? =
    if (targetDate == sourceDate && (meal == null || (targetMeal ?: meal) == meal)) DiaryPresenter.COPY_SAME_ERROR else null

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
fun buildDiaryUiState(
    date: String,
    entries: List<DiaryEntry>,
    goals: DailyGoals,
    mealsPerDay: Int? = null,
    today: String = date,
    summaries: List<DaySummary> = emptyList(),
): DiaryUiState {
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
                    // Los valores de una entrada rápida son totales (no «por 100 g»): no tiene sentido marcarlos como dudosos.
                    doubtful = !it.quick && it.nutrients.needsReview,
                    servingSizeG = it.servingSizeG,
                    quick = it.quick,
                    macrosText = if (it.quick) quickMacrosText(it.totals.proteinG, it.totals.carbsG, it.totals.fatG) else "",
                )
            },
        )
    }
    val byDate = summaries.associateBy { it.date }
    return DiaryUiState(
        date = date,
        today = today,
        week = buildWeek(date, today, goals.kcal, byDate),
        streak = DayStatus.streak(today, goals.kcal, byDate),
        goalKcal = goals.kcal,
        summaries = byDate,
        consumedKcal = meals.sumOf { it.kcal },
        macros = listOf(
            MacroProgress("Proteína", meals.sumOf { it.proteinG }, goals.proteinG?.toDouble()),
            MacroProgress("Hidratos", meals.sumOf { it.carbsG }, goals.carbsG?.toDouble()),
            MacroProgress("Grasa", meals.sumOf { it.fatG }, goals.fatG?.toDouble()),
        ),
        meals = meals,
        intake = IntakeGuidelines.intake(entries, goals.kcal),
        loading = false,
    )
}

private val weekInitials = listOf("L", "M", "X", "J", "V", "S", "D")

/** Franja de lunes a domingo de la semana de [date], con el estado de cada día. */
fun buildWeek(
    date: String,
    today: String,
    goalKcal: Int?,
    summaries: Map<String, DaySummary>,
): List<WeekDayUi> {
    val start = IsoDate.weekStart(date)
    return (0 until 7).map { i ->
        val day = IsoDate.plusDays(start, i)
        WeekDayUi(
            date = day,
            initial = weekInitials[i],
            dayNumber = day.substring(8, 10).toInt(),
            status = DayStatus.of(day, today, goalKcal, summaries[day]),
            selected = day == date,
        )
    }
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
    private val today: String,
    private val mealsPerDay: Int? = null,
    private val nowMillis: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
    private val templates: MealTemplateStore? = null,
) {
    private val _state = MutableStateFlow(DiaryUiState(date = today))
    val state: StateFlow<DiaryUiState> = _state.asStateFlow()

    private var removed: Pair<NewDiaryEntry, Long>? = null

    fun load(date: String = _state.value.date) {
        scope.launch {
            val pending = _state.value.message // un aviso aún no mostrado sobrevive a la recarga
            _state.value = try {
                // Resumen de kcal de todo el historial hasta el fin de la semana (o hoy): franja y racha salen de él.
                val upTo = maxOf(IsoDate.plusDays(IsoDate.weekStart(date), 6), today)
                buildDiaryUiState(
                    date, diary.entriesForDate(date), goals.get(), mealsPerDay,
                    today = today, summaries = diary.daySummaries(IsoDate.EARLIEST, upTo),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DiaryUiState(date = date, loading = false, loadFailed = true)
            }.let { if (it.message == null) it.copy(message = pending) else it }
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun fail(message: String) {
        _state.value = _state.value.copy(message = message)
    }

    fun shiftDay(days: Int) = load(IsoDate.plusDays(_state.value.date, days))

    /** Elige un día cualquiera (franja semanal o selector de fecha). */
    fun select(date: String) = load(date)

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
                    entry.date, entry.meal, entry.barcode, entry.name, entry.quantity, entry.servingSizeG, entry.nutrients, entry.quick,
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

    /**
     * Copia las entradas de [meal] del día mostrado a [targetDate]; en [targetMeal] o, con `null`, en la comida equivalente.
     * No hace nada si el destino es la misma comida del mismo día (duplicaría las entradas).
     */
    fun copyMeal(meal: MealType, targetDate: String, targetMeal: MealType?) {
        if (targetDate == _state.value.date && (targetMeal ?: meal) == meal) return fail(COPY_SAME_ERROR)
        copy(targetDate) { entries -> entries.filter { it.meal == meal } to (targetMeal ?: DiaryCopy.targetMeal(meal, activeMeals)) }
    }

    /** Copia todas las comidas del día mostrado a [targetDate], cada una a su comida equivalente. */
    fun copyDay(targetDate: String) {
        if (targetDate == _state.value.date) return fail(COPY_SAME_ERROR)
        copy(targetDate) { it to null }
    }

    /**
     * Guarda las entradas de [meal] del día mostrado como plantilla [name] (con sus cantidades y datos nutricionales).
     * [onResult] recibe `null` si se ha guardado o el motivo del fallo (nombre repetido, comida vacía, error); el
     * diálogo lo enseña y solo se cierra si todo fue bien.
     */
    fun saveMealAsTemplate(meal: MealType, name: String, onResult: (String?) -> Unit) {
        val store = templates ?: return onResult(TEMPLATE_ERROR)
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return onResult("Escribe un nombre")
        val date = _state.value.date
        scope.launch {
            val error = try {
                val entries = diary.entriesForDate(date).filter { it.meal == meal }
                when {
                    entries.isEmpty() -> TEMPLATE_EMPTY
                    TemplateRules.nameTaken(trimmed, store.all().map { it.name }) -> TemplateRules.NAME_TAKEN
                    else -> {
                        store.create(NewMealTemplate(trimmed, entries.map(TemplateItem::of)), nowMillis())
                        _state.value = _state.value.copy(message = "Plantilla «$trimmed» guardada")
                        null
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                TEMPLATE_ERROR
            }
            onResult(error)
        }
    }

    /**
     * «Mismo que ayer»: copia al día mostrado lo que se apuntó el día anterior, en la misma comida ([meal]) o, con `null`,
     * el día entero (cada comida a su equivalente). Reutiliza la lógica de [copyMeal] y [copyDay].
     */
    fun repeatPrevious(meal: MealType?) {
        val target = _state.value.date
        val source = IsoDate.plusDays(target, -1)
        copy(target, source, empty = if (meal == null) REPEAT_EMPTY_DAY else REPEAT_EMPTY_MEAL, onDone = { count, remapped ->
            "Repetido de ${previousDayWord(source)}: $count " + (if (count == 1) "alimento" else "alimentos") +
                if (remapped) " (algunas comidas, a la más cercana)" else ""
        }) { entries -> if (meal == null) entries to null else entries.filter { it.meal == meal } to meal }
    }

    private fun previousDayWord(source: String) = if (source == IsoDate.plusDays(today, -1)) "ayer" else IsoDate.long(source)

    private val activeMeals get() = MealPlan.preset(mealsPerDay)

    private fun copy(
        targetDate: String,
        sourceDate: String = _state.value.date,
        empty: String = COPY_EMPTY,
        onDone: (count: Int, remapped: Boolean) -> String = { count, remapped ->
            "Copiado a ${IsoDate.long(targetDate)}: $count " + (if (count == 1) "alimento" else "alimentos") +
                if (remapped) " (algunas comidas, a la más cercana)" else ""
        },
        select: (List<DiaryEntry>) -> Pair<List<DiaryEntry>, MealType?>,
    ) {
        scope.launch {
            val count: Int
            val remapped: Boolean
            try {
                val (entries, meal) = select(diary.entriesForDate(sourceDate))
                if (entries.isEmpty()) return@launch fail(empty)
                val plan = DiaryCopy.plan(entries, targetDate, meal, activeMeals)
                // Instantes crecientes: conservan el orden de las filas dentro de cada comida.
                val now = nowMillis()
                plan.entries.forEachIndexed { i, e -> diary.add(e, now + i) }
                count = plan.entries.size
                remapped = plan.remapped
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch fail(COPY_ERROR)
            }
            _state.value = _state.value.copy(message = onDone(count, remapped))
            load()
        }
    }

    companion object {
        const val TEMPLATE_ERROR = "No se pudo guardar la plantilla. Inténtalo de nuevo."
        const val TEMPLATE_EMPTY = "No hay nada que guardar en esta comida."
        const val COPY_ERROR = "No se pudo copiar. Inténtalo de nuevo."
        const val COPY_EMPTY = "No hay nada que copiar."
        const val COPY_SAME_ERROR = "Elige otro día u otra comida."
        const val REPEAT_EMPTY_DAY = "El día anterior no tiene nada apuntado."
        const val REPEAT_EMPTY_MEAL = "El día anterior no hay nada apuntado en esta comida."
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

    /** Fecha mínima admitida al consultar el historial. */
    const val EARLIEST = "0000-01-01"

    private val weekdayNames = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")

    /** «lunes 16 de noviembre» (día de la semana + día y mes, sin año). */
    fun weekdayAndDay(date: String): String {
        val day = isoDateToEpochDay(date) ?: return long(date)
        return weekdayNames[(day + 3).mod(7L).toInt()] + " " + long(date).substringBeforeLast(" de ")
    }

    /** Posición del día en la semana: 0 = lunes … 6 = domingo. */
    fun weekdayIndex(date: String): Int = ((isoDateToEpochDay(date) ?: 0L) + 3).mod(7L).toInt()

    /** Lunes de la semana de [date]. */
    fun weekStart(date: String): String {
        val day = isoDateToEpochDay(date) ?: return date
        return epochDayToIsoDate(day - (day + 3).mod(7L))
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

    /** Primer día del mes de [date]. */
    fun monthStart(date: String): String = date.substring(0, 8) + "01"

    /** Primer día del mes que queda [months] meses después (o antes, si es negativo) del de [date]. */
    fun shiftMonth(date: String, months: Int): String {
        val (y, m, _) = parse(date)
        val index = y * 12 + (m - 1) + months
        return "${(index / 12).toString().padStart(4, '0')}-${(index % 12 + 1).toString().padStart(2, '0')}-01"
    }

    /** Días del mes de [date]. */
    fun daysInMonth(date: String): Int = parse(date).let { (y, m, _) -> daysInMonth(y, m) }

    /** «noviembre de 2026». */
    fun monthTitle(date: String): String = parse(date).let { (y, m, _) -> "${monthNames[m - 1]} de $y" }

    /** «4 de noviembre de 2026». */
    fun long(date: String): String {
        val (y, m, d) = parse(date)
        return "$d de ${monthNames[m - 1]} de $y"
    }
}
