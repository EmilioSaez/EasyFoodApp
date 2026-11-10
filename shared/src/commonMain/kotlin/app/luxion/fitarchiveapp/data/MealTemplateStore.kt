package app.luxion.fitarchiveapp.data

import app.luxion.fitarchiveapp.model.MealTemplate
import app.luxion.fitarchiveapp.model.NewMealTemplate

/** Acceso a las plantillas de comida (la implementación real es [SqlDelightMealTemplateStore]). */
interface MealTemplateStore {
    /** Todas las plantillas con sus alimentos, ordenadas por nombre. */
    suspend fun all(): List<MealTemplate>
    suspend fun find(id: Long): MealTemplate?
    /** Guarda la plantilla y devuelve su id. */
    suspend fun create(template: NewMealTemplate, nowMillis: Long): Long
    /** Reemplaza nombre y alimentos; devuelve `false` si la plantilla no existe. */
    suspend fun update(id: Long, template: NewMealTemplate, nowMillis: Long): Boolean
    /** Cambia solo el nombre; devuelve `false` si la plantilla no existe. */
    suspend fun rename(id: Long, name: String, nowMillis: Long): Boolean
    /** Borra la plantilla y sus alimentos; devuelve `false` si no existía. */
    suspend fun delete(id: Long): Boolean
}
