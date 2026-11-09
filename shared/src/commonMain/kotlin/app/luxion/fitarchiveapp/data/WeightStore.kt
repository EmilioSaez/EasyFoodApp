package app.luxion.fitarchiveapp.data

import app.luxion.fitarchiveapp.model.NewWeightEntry
import app.luxion.fitarchiveapp.model.WeightEntry

/** Acceso a los registros de peso, uno por día (la implementación real es [SqlDelightWeightStore]). */
interface WeightStore {
    /** Todos los registros, del más antiguo al más reciente. */
    suspend fun all(): List<WeightEntry>

    /** Guarda el peso de ese día; si ya había uno lo sustituye (conserva su fecha de creación). */
    suspend fun save(entry: NewWeightEntry, nowMillis: Long)

    /**
     * Edita el registro de [originalDate]: puede cambiar fecha y peso. Si la nueva fecha ya tenía otro
     * registro, queda sustituido. Devuelve `false` si [originalDate] no existe.
     */
    suspend fun update(originalDate: String, entry: NewWeightEntry, nowMillis: Long): Boolean

    /** Borra el registro; devuelve `false` si no existía. */
    suspend fun delete(date: String): Boolean
}
