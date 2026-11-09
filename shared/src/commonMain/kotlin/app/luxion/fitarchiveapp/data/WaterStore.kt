package app.luxion.fitarchiveapp.data

/** Registro de agua por día y su objetivo (la implementación real es [SqlDelightWaterStore]). */
interface WaterStore {
    /** Ml bebidos ese día (0 si no hay registro). */
    suspend fun total(date: String): Int

    /**
     * Suma [deltaMl] (negativo para deshacer) al total del día y devuelve el nuevo total, limitado entre 0 y
     * [app.luxion.fitarchiveapp.model.WaterRules.MAX_DAY_ML]. Un día que llega a 0 queda sin registro.
     */
    suspend fun add(date: String, deltaMl: Int, nowMillis: Long): Int

    /** Objetivo diario en ml; [app.luxion.fitarchiveapp.model.WaterRules.DEFAULT_GOAL_ML] si no se ha editado. */
    suspend fun goalMl(): Int

    /** Fija el objetivo; lanza [IllegalArgumentException] fuera de 500 a 10 000 ml. */
    suspend fun setGoalMl(ml: Int, nowMillis: Long)
}
