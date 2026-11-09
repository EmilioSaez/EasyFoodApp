package app.luxion.fitarchiveapp.data

import app.luxion.fitarchiveapp.model.Product

/** Alimentos favoritos (la implementación real es [SqlDelightFavoriteStore]). */
interface FavoriteStore {
    /** Claves favoritas (EAN, `generic:<id>` o `custom:<id>`), de la más reciente a la más antigua. */
    suspend fun keys(): List<String>

    /** Productos favoritos que están en la caché local (los favoritos propios se leen de [CustomFoodStore]). */
    suspend fun products(): List<Product>

    /** Marca ([favorite] = `true`) o desmarca la clave. Marcar algo ya marcado no cambia nada. */
    suspend fun setFavorite(key: String, favorite: Boolean, nowMillis: Long)
}

/** Claves de favorito: la misma clave de navegación que usa la ficha del alimento. */
object FavoriteKeys {
    /** Prefijo de la clave de un alimento propio (el resto es su id). */
    const val CUSTOM_PREFIX = "custom:"

    fun custom(id: Long) = CUSTOM_PREFIX + id

    /** Id del alimento propio si [key] es `custom:<id>`; `null` si es un producto o no es válida. */
    fun customIdOf(key: String): Long? =
        if (key.startsWith(CUSTOM_PREFIX)) key.removePrefix(CUSTOM_PREFIX).toLongOrNull() else null
}
