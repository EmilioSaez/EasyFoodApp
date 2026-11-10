package app.luxion.fitarchiveapp.ai

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Clave de API cifrada con una clave AES-256-GCM del Android Keystore (no exportable, ligada al dispositivo).
 * Solo el texto cifrado se guarda en SharedPreferences; sin la clave del Keystore no sirve de nada, así que
 * aunque una copia de seguridad lo arrastrara no se podría descifrar (además `allowBackup` está desactivado y
 * las reglas de extracción lo excluyen).
 */
class AndroidKeystoreKeyVault(context: Context) : AiKeyVault {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun read(): String? {
        val stored = prefs.getString(BLOB, null) ?: return null
        return try {
            val raw = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, raw.copyOfRange(0, IV_SIZE)))
            String(cipher.doFinal(raw, IV_SIZE, raw.size - IV_SIZE), Charsets.UTF_8)
        } catch (e: Exception) {
            null // clave del Keystore invalidada o dato dañado: como si no hubiera clave
        }
    }

    override fun write(key: String) {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val encrypted = cipher.iv + cipher.doFinal(key.toByteArray(Charsets.UTF_8))
            if (!prefs.edit().putString(BLOB, Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()) {
                throw AiStorageException("no se pudo escribir en el almacenamiento del dispositivo")
            }
        } catch (e: AiStorageException) {
            throw e
        } catch (e: Exception) {
            throw AiStorageException("el Keystore del dispositivo no está disponible", e)
        }
    }

    override fun clear() {
        prefs.edit().remove(BLOB).commit()
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val FILE = "easyfood_ai_secure"
        const val BLOB = "api_key_blob"
        const val ALIAS = "easyfood_ai_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}

/** Ajustes no sensibles de la IA en SharedPreferences (excluido de copias de seguridad). */
class AndroidAiPreferences(context: Context) : AiPreferences {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun getString(name: String): String? = prefs.getString(name, null)
    override fun putString(name: String, value: String) { prefs.edit().putString(name, value).apply() }
    override fun remove(name: String) { prefs.edit().remove(name).apply() }

    companion object {
        const val FILE = "easyfood_ai_prefs"
    }
}
