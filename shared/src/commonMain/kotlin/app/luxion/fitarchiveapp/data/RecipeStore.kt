package app.luxion.fitarchiveapp.data

import app.luxion.fitarchiveapp.model.NewRecipe
import app.luxion.fitarchiveapp.model.Recipe

/** Acceso a las recetas del usuario (la implementación real es [SqlDelightRecipeStore]). */
interface RecipeStore {
    /** Todas las recetas con sus ingredientes, ordenadas por nombre. */
    suspend fun all(): List<Recipe>
    suspend fun find(id: Long): Recipe?
    /** Guarda la receta y devuelve su id. */
    suspend fun create(recipe: NewRecipe, nowMillis: Long): Long
    /** Reemplaza nombre, raciones e ingredientes; devuelve `false` si la receta no existe. */
    suspend fun update(id: Long, recipe: NewRecipe, nowMillis: Long): Boolean
    /** Borra la receta y sus ingredientes; devuelve `false` si no existía. */
    suspend fun delete(id: Long): Boolean
}
