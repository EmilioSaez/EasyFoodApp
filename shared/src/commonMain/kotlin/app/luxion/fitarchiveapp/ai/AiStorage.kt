package app.luxion.fitarchiveapp.ai

/**
 * Almacén protegido de la clave de API de Anthropic. La clave vive SOLO en el dispositivo: nunca va a la base de
 * datos (que se exporta), ni a exportar/importar, ni a copias de seguridad, ni a logs.
 * Android: Keystore · iOS: Keychain · escritorio: DPAPI (Windows) o archivo cifrado con permisos 600.
 * Las implementaciones lanzan [AiStorageException] si no pueden guardar de forma segura (no degradan en silencio).
 */
interface AiKeyVault {
    fun read(): String?
    fun write(key: String)
    fun clear()
}

/** Ajustes no sensibles de la IA (modelo, activada, tope, contadores) guardados por dispositivo, fuera de la BD exportable. */
interface AiPreferences {
    fun getString(name: String): String?
    fun putString(name: String, value: String)
    fun remove(name: String)
}

class AiStorageException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Almacenes en memoria para tests y vistas previas. */
class InMemoryAiKeyVault(private var key: String? = null) : AiKeyVault {
    override fun read(): String? = key
    override fun write(key: String) { this.key = key }
    override fun clear() { key = null }
}

class InMemoryAiPreferences : AiPreferences {
    private val values = mutableMapOf<String, String>()
    override fun getString(name: String): String? = values[name]
    override fun putString(name: String, value: String) { values[name] = value }
    override fun remove(name: String) { values.remove(name) }
}
