package app.luxion.fitarchiveapp.data

import app.luxion.fitarchiveapp.domain.DaySummary
import app.luxion.fitarchiveapp.model.DiaryEntry
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.NewDiaryEntry
import app.luxion.fitarchiveapp.model.Quantity

/** Acceso al diario local (la implementación real es [SqlDelightDiaryStore]). */
interface DiaryStore {
    /** Entradas de un día, ordenadas por comida y por instante de creación. */
    suspend fun entriesForDate(date: String): List<DiaryEntry>
    /** Kcal por día de los días con entradas entre [from] y [to] (ambos incluidos), en orden de fecha. */
    suspend fun daySummaries(from: String, to: String): List<DaySummary> = emptyList()
    suspend fun find(id: Long): DiaryEntry?
    /** Última entrada de cada alimento distinto (hasta [limit]), de la más reciente a la más antigua. */
    suspend fun recentFoods(limit: Int): List<DiaryEntry> = emptyList()
    /** Guarda la entrada y devuelve su id. */
    suspend fun add(entry: NewDiaryEntry, createdAtMillis: Long): Long
    /** Cambia comida y cantidad; devuelve `false` si la entrada no existe. */
    suspend fun update(id: Long, meal: MealType, quantity: Quantity): Boolean
    /** Borra la entrada; devuelve `false` si no existía. */
    suspend fun delete(id: Long): Boolean
}
