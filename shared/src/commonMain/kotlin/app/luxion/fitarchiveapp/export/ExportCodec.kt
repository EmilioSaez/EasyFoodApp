package app.luxion.fitarchiveapp.export

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** El archivo no es un JSON de exportación válido o su versión no es compatible. */
class ExportFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

object ExportCodec {
    const val CURRENT_VERSION = 1

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(file: ExportFile): String = json.encodeToString(ExportFile.serializer(), file)

    /**
     * Lee un archivo exportado. Comprueba `schemaVersion` antes de interpretar el resto, de modo que un
     * archivo de una versión futura falla con un mensaje claro en lugar de leerse a medias.
     */
    fun decode(text: String): ExportFile {
        val version = try {
            val root = json.parseToJsonElement(text).jsonObject
            root["schemaVersion"]?.jsonPrimitive?.int
        } catch (e: Exception) {
            throw ExportFormatException("El archivo no es un JSON de exportación válido", e)
        } ?: throw ExportFormatException("Falta el campo schemaVersion")
        if (version < 1 || version > CURRENT_VERSION) {
            throw ExportFormatException("Versión de formato no compatible: $version (máxima admitida: $CURRENT_VERSION)")
        }
        return try {
            json.decodeFromString(ExportFile.serializer(), text)
        } catch (e: SerializationException) {
            throw ExportFormatException("El archivo de exportación está dañado", e)
        } catch (e: IllegalArgumentException) {
            throw ExportFormatException("El archivo de exportación está dañado", e)
        }
    }
}
