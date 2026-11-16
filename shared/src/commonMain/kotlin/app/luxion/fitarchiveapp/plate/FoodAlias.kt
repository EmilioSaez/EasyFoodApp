package app.luxion.fitarchiveapp.plate

import app.luxion.fitarchiveapp.ai.AiPreferences
import app.luxion.fitarchiveapp.ui.screens.FoodPick
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** Alimento de la base que el usuario eligió para un término de la IA. [barcode] es `null` en los alimentos propios. */
data class FoodAlias(val name: String, val barcode: String?)

/**
 * Recuerda qué alimento de la base eligió el usuario cuando corrigió el enlace de la IA («arroz cocido» → su arroz
 * habitual), para enlazarlo así la próxima vez sin gastar otra llamada. Solo vive en el dispositivo (preferencias de la
 * IA, fuera de la base de datos exportable) y no viaja a la API. Guarda como mucho [MAX_ALIASES], descartando los más antiguos.
 */
class FoodAliasStore(private val prefs: AiPreferences) {
    fun get(term: String): FoodAlias? = read()[normalize(term)]

    fun remember(term: String, pick: FoodPick) {
        val key = normalize(term)
        if (key.isEmpty()) return
        val all = read().toMutableMap()
        all.remove(key) // se reinserta al final: así el más antiguo es el primero
        all[key] = FoodAlias(pick.result.name, pick.barcode)
        write(all.entries.toList().takeLast(MAX_ALIASES).associate { it.key to it.value })
    }

    fun forget(term: String) {
        val all = read().toMutableMap()
        if (all.remove(normalize(term)) != null) write(all)
    }

    fun clear() = prefs.remove(KEY)

    private fun read(): Map<String, FoodAlias> {
        val raw = prefs.getString(KEY) ?: return emptyMap()
        val obj = try {
            Json.parseToJsonElement(raw) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: return emptyMap()
        val out = LinkedHashMap<String, FoodAlias>()
        for ((k, v) in obj) {
            val o = v as? JsonObject ?: continue
            val name = (o["n"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: continue
            out[k] = FoodAlias(name, (o["b"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull)
        }
        return out
    }

    private fun write(map: Map<String, FoodAlias>) {
        val json = buildJsonObject {
            map.forEach { (k, a) ->
                put(k, buildJsonObject {
                    put("n", a.name)
                    a.barcode?.let { put("b", it) }
                })
            }
        }
        prefs.putString(KEY, json.toString())
    }

    companion object {
        const val MAX_ALIASES = 100
        private const val KEY = "plate.aliases"

        fun normalize(term: String): String = term.lowercase().trim().replace(Regex("\\s+"), " ").take(60)
    }
}
