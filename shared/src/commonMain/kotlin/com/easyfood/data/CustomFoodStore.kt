package com.easyfood.data

import com.easyfood.model.CustomFood
import com.easyfood.model.NewCustomFood

/** Acceso a los alimentos propios del usuario (la implementación real es [SqlDelightCustomFoodStore]). */
interface CustomFoodStore {
    /** Todos los alimentos propios, ordenados por nombre. */
    suspend fun all(): List<CustomFood>
    suspend fun find(id: Long): CustomFood?
    /** Guarda el alimento y devuelve su id. */
    suspend fun create(food: NewCustomFood, nowMillis: Long): Long
    /** Reemplaza los datos del alimento; devuelve `false` si no existe. */
    suspend fun update(id: Long, food: NewCustomFood, nowMillis: Long): Boolean
    /** Borra el alimento; devuelve `false` si no existía. */
    suspend fun delete(id: Long): Boolean
}
