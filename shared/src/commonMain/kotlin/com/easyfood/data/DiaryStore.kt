package com.easyfood.data

import com.easyfood.model.DiaryEntry
import com.easyfood.model.MealType
import com.easyfood.model.NewDiaryEntry
import com.easyfood.model.Quantity

/** Acceso al diario local (la implementación real es [SqlDelightDiaryStore]). */
interface DiaryStore {
    /** Entradas de un día, ordenadas por comida y por instante de creación. */
    suspend fun entriesForDate(date: String): List<DiaryEntry>
    suspend fun find(id: Long): DiaryEntry?
    /** Guarda la entrada y devuelve su id. */
    suspend fun add(entry: NewDiaryEntry, createdAtMillis: Long): Long
    /** Cambia comida y cantidad; devuelve `false` si la entrada no existe. */
    suspend fun update(id: Long, meal: MealType, quantity: Quantity): Boolean
    /** Borra la entrada; devuelve `false` si no existía. */
    suspend fun delete(id: Long): Boolean
}
