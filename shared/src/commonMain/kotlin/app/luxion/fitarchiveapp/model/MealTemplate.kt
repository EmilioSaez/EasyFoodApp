package app.luxion.fitarchiveapp.model

/**
 * Un alimento de una plantilla de comida: copia (snapshot) del nombre y de los valores por 100 g, como una entrada del
 * diario, con su cantidad y unidad. Así «2 porciones» sigue siendo «2 porciones» al añadir la plantilla.
 */
data class TemplateItem(
    /** Clave del producto (EAN o `generic:<id>`); `null` en un alimento propio. */
    val barcode: String?,
    val name: String,
    val quantity: Quantity,
    val servingSizeG: Double?,
    /** Valores por 100 g; solo se usan kcal, proteínas, hidratos y grasa. */
    val nutrients: Nutrients,
) {
    init {
        require(name.isNotBlank()) { "El alimento necesita un nombre" }
    }

    /** La entrada del diario que crea este alimento al añadir la plantilla. */
    fun toDiaryEntry(date: String, meal: MealType) =
        NewDiaryEntry(date, meal, barcode, name, quantity, servingSizeG, nutrients)

    /** Totales de macros con la misma regla que una entrada del diario (porciones sin tamaño = sin dato). */
    val totals: MacroTotals
        get() = DiaryEntry(0, "", MealType.BREAKFAST, barcode, name, quantity, servingSizeG, nutrients, 0).totals

    companion object {
        fun of(entry: DiaryEntry) = TemplateItem(entry.barcode, entry.name, entry.quantity, entry.servingSizeG, entry.nutrients)
    }
}

/** Datos para crear o editar una plantilla; valida al construirse. */
data class NewMealTemplate(val name: String, val items: List<TemplateItem>) {
    init {
        require(name.isNotBlank()) { "El nombre no puede estar vacío" }
        require(items.isNotEmpty()) { "La plantilla necesita al menos un alimento" }
    }
}

/** Plantilla ya guardada. */
data class MealTemplate(
    val id: Long,
    val name: String,
    val items: List<TemplateItem>,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    /** Las entradas del diario que crea la plantilla en [date] y [meal], en el orden de la plantilla. */
    fun diaryEntries(date: String, meal: MealType): List<NewDiaryEntry> = items.map { it.toDiaryEntry(date, meal) }
}
