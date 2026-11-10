package app.luxion.fitarchiveapp.ai

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Cifra y descifra la clave en reposo. */
interface SecretProtector {
    fun protect(plain: ByteArray): ByteArray
    fun unprotect(blob: ByteArray): ByteArray
}

/**
 * Windows: DPAPI del usuario (`ProtectedData`, ámbito CurrentUser) mediante PowerShell. El secreto viaja solo por la
 * entrada estándar del proceso, nunca en sus argumentos.
 */
class DpapiProtector : SecretProtector {
    override fun protect(plain: ByteArray) = run("Protect", plain)
    override fun unprotect(blob: ByteArray) = run("Unprotect", blob)

    private fun run(op: String, data: ByteArray): ByteArray {
        val script = "\$ErrorActionPreference='Stop';Add-Type -AssemblyName System.Security;" +
            "\$b=[Convert]::FromBase64String([Console]::In.ReadToEnd().Trim());" +
            "\$o=[Security.Cryptography.ProtectedData]::$op(\$b,\$null,'CurrentUser');" +
            "[Console]::Out.Write([Convert]::ToBase64String(\$o))"
        val process = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script).start()
        try {
            process.outputStream.use { it.write(Base64.getEncoder().encode(data)) }
            val out = process.inputStream.readBytes()
            val err = process.errorStream.readBytes()
            if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0 || out.isEmpty()) {
                throw AiStorageException("DPAPI no disponible (${String(err).take(120).trim()})")
            }
            return Base64.getDecoder().decode(out)
        } finally {
            process.destroy()
        }
    }
}

/**
 * macOS/Linux: AES-256-GCM con una clave aleatoria en un archivo aparte con permisos 600 (solo el usuario).
 * Protege la clave frente a copias del archivo de ajustes, pero no frente a quien pueda leer los archivos del usuario.
 */
class KeyFileProtector(private val keyFile: File) : SecretProtector {
    private fun secretKey(): SecretKeySpec {
        if (!keyFile.exists()) {
            keyFile.parentFile?.mkdirs()
            keyFile.writeBytes(ByteArray(32).also { SecureRandom().nextBytes(it) })
            restrict(keyFile)
        }
        val bytes = keyFile.readBytes()
        if (bytes.size != 32) throw AiStorageException("Clave local dañada")
        return SecretKeySpec(bytes, "AES")
    }

    override fun protect(plain: ByteArray): ByteArray {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey(), GCMParameterSpec(128, iv)) }
        return iv + cipher.doFinal(plain)
    }

    override fun unprotect(blob: ByteArray): ByteArray {
        if (blob.size < 13) throw AiStorageException("Dato cifrado dañado")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            .apply { init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, blob.copyOfRange(0, 12))) }
        return cipher.doFinal(blob, 12, blob.size - 12)
    }
}

/** Deja el archivo solo para su propietario (rw-------); en sistemas sin POSIX se ignora. */
internal fun restrict(file: File) {
    try {
        Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------"))
    } catch (e: Exception) {
        // Sistema de archivos sin permisos POSIX (Windows): ahí se usa DPAPI.
    }
}

/** [AiKeyVault] de escritorio: guarda la clave cifrada con [protector] en [file]. */
class JvmAiKeyVault(private val file: File, private val protector: SecretProtector) : AiKeyVault {
    override fun read(): String? {
        if (!file.exists()) return null
        return try {
            String(protector.unprotect(Base64.getDecoder().decode(file.readText().trim())), Charsets.UTF_8)
        } catch (e: Exception) {
            null // ilegible (otro usuario, archivo dañado): como si no hubiera clave
        }
    }

    override fun write(key: String) {
        try {
            file.parentFile?.mkdirs()
            file.writeText(Base64.getEncoder().encodeToString(protector.protect(key.toByteArray(Charsets.UTF_8))))
            restrict(file)
        } catch (e: AiStorageException) {
            throw e
        } catch (e: Exception) {
            throw AiStorageException("no se pudo cifrar la clave", e)
        }
    }

    override fun clear() {
        file.delete()
    }

    companion object {
        /** Almacén por defecto en `~/.easyfood/ai/` (fuera de `easyfood.db`, que es lo que se exporta). */
        fun default(dir: File = File(System.getProperty("user.home"), ".easyfood/ai")): JvmAiKeyVault {
            val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
            val protector = if (windows) DpapiProtector() else KeyFileProtector(File(dir, "local.key"))
            return JvmAiKeyVault(File(dir, "api-key.bin"), protector)
        }
    }
}

/** [AiPreferences] en un `.properties` por dispositivo, fuera de la base de datos exportable. */
class JvmAiPreferences(private val file: File) : AiPreferences {
    private val props = Properties().apply {
        if (file.exists()) {
            try {
                file.inputStream().use { load(it) }
            } catch (e: Exception) {
                // se empieza vacío
            }
        }
    }

    @Synchronized
    override fun getString(name: String): String? = props.getProperty(name)

    @Synchronized
    override fun putString(name: String, value: String) {
        props.setProperty(name, value)
        save()
    }

    @Synchronized
    override fun remove(name: String) {
        props.remove(name)
        save()
    }

    private fun save() {
        file.parentFile?.mkdirs()
        file.outputStream().use { props.store(it, null) }
    }

    companion object {
        fun default(dir: File = File(System.getProperty("user.home"), ".easyfood/ai")) = JvmAiPreferences(File(dir, "settings.properties"))
    }
}
