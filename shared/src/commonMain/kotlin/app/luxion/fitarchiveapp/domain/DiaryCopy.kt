package app.luxion.fitarchiveapp.domain

import app.luxion.fitarchiveapp.model.DiaryEntry
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.NewDiaryEntry

/** Entradas por crear al copiar y si alguna comida se ha reasignado por no existir en el destino. */
data class CopyPlan(val entries: List<NewDiaryEntry>, val remapped: Boolean)

/** Lógica de «Copiar» comida y «Copiar día» del Diario (docs/design.md §2.13). */
object DiaryCopy {
    /** Comida de [active] que recibe lo de [source]: ella misma si está activa y, si no, la más cercana por hora. */
    fun targetMeal(source: MealType, active: List<MealType>): MealType =
        if (source in active) source else MealPlan.closest(active, source)

    /**
     * Copias de [entries] en [targetDate], con las mismas cantidades y datos nutricionales y en el mismo orden.
     * Con [targetMeal] todo va a esa comida; con `null` cada entrada va a su comida equivalente de [active].
     */
    fun plan(entries: List<DiaryEntry>, targetDate: String, targetMeal: MealType?, active: List<MealType>): CopyPlan {
        var remapped = false
        val copies = entries.map {
            val meal = targetMeal ?: targetMeal(it.meal, active).also { m -> if (m != it.meal) remapped = true }
            NewDiaryEntry(targetDate, meal, it.barcode, it.name, it.quantity, it.servingSizeG, it.nutrients)
        }
        return CopyPlan(copies, remapped)
    }
}
