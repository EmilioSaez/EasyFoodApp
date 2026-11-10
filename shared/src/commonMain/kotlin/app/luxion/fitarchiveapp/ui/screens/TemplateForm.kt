package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.model.MacroTotals
import app.luxion.fitarchiveapp.model.MealTemplate
import app.luxion.fitarchiveapp.model.NewMealTemplate
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import app.luxion.fitarchiveapp.model.TemplateItem
import app.luxion.fitarchiveapp.ui.components.formatKcal

/** Estado del formulario de plantilla: el nombre tal como lo escribe el usuario más los alimentos ya elegidos. */
data class TemplateFormState(
    val name: String = "",
    val items: List<TemplateItem> = emptyList(),
    /** Nombres de las demás plantillas, para no repetir ninguno (la importación identifica por nombre). */
    val otherNames: Set<String> = emptySet(),
) {
    val nameError: String?
        get() = when {
            name.isBlank() -> "Escribe un nombre"
            TemplateRules.nameTaken(name, otherNames) -> TemplateRules.NAME_TAKEN
            else -> null
        }

    val itemsError: String? get() = if (items.isEmpty()) "Añade al menos un alimento" else null

    /** La plantilla lista para guardar, o `null` si algo no es válido. */
    fun toNewTemplate(): NewMealTemplate? =
        if (nameError != null || itemsError != null) null else NewMealTemplate(name.trim(), items)

    fun addItem(item: TemplateItem) = copy(items = items + item)

    fun removeItem(index: Int) = copy(items = items.filterIndexed { i, _ -> i != index })

    /** Cambia la cantidad del alimento [index] conservando su unidad. */
    fun withAmount(index: Int, amount: Double) = copy(
        items = items.mapIndexed { i, it -> if (i == index) it.copy(quantity = Quantity(amount, it.quantity.unit)) else it },
    )

    companion object {
        fun of(template: MealTemplate, otherNames: Set<String> = emptySet()) =
            TemplateFormState(template.name, template.items, otherNames)
    }
}

object TemplateRules {
    const val NAME_TAKEN = "Ya tienes una plantilla con ese nombre"

    /** `true` si [name] (sin distinguir mayúsculas ni espacios sobrantes) ya lo lleva una de [others]. */
    fun nameTaken(name: String, others: Collection<String>): Boolean =
        others.any { it.trim().equals(name.trim(), ignoreCase = true) }
}

/** Cantidad de un alimento de plantilla como texto: «30 g» o «2 porciones». */
fun TemplateItem.quantityText(): String = quantity.format()

/** Texto de la unidad junto al campo de cantidad. */
fun TemplateItem.unitSuffix(): String = if (quantity.unit == QuantityUnit.GRAMS) "g" else "porciones"

/** Kcal de la plantilla entera; `null` si ningún alimento tiene dato. Los alimentos sin dato no suman. */
fun MealTemplate.totalKcal(): Double? = templateTotals(items).kcal

/** Totales de macros de [items]; cada campo es `null` si ningún alimento tiene ese dato. */
fun templateTotals(items: List<TemplateItem>): MacroTotals {
    fun sum(f: (MacroTotals) -> Double?): Double? = items.mapNotNull { f(it.totals) }.takeIf { it.isNotEmpty() }?.sum()
    return MacroTotals(sum { it.kcal }, sum { it.proteinG }, sum { it.carbsG }, sum { it.fatG })
}

/** «3 alimentos · 420 kcal» (o «sin dato de energía»). */
fun MealTemplate.listDetail(): String {
    val n = items.size
    val count = if (n == 1) "1 alimento" else "$n alimentos"
    return count + (totalKcal()?.let { " · ${formatKcal(it)} kcal" } ?: " · sin dato de energía")
}

/** Resumen de los alimentos para la fila: «Pan integral 60 g, Aceite 10 g…». */
fun MealTemplate.itemsPreview(limit: Int = 3): String {
    val shown = items.take(limit).joinToString(", ") { "${it.name} ${it.quantityText()}" }
    return if (items.size > limit) "$shown…" else shown
}
