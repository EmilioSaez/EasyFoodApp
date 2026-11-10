package app.luxion.fitarchiveapp.ai

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Clave de API en el Keychain de iOS. `WhenUnlockedThisDeviceOnly`: solo con el dispositivo desbloqueado y sin
 * pasar a copias de seguridad ni a otro dispositivo.
 * Nota: solo se comprueba su tipado desde Windows y en CI; el comportamiento real se prueba en un iPhone (docs/ios-pendiente.md).
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class KeychainAiKeyVault(private val service: String = "app.luxion.fitarchiveapp.ai") : AiKeyVault {
    override fun read(): String? = memScoped {
        val query = dictionary()
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(query, kSecAttrService, CFBridgingRetain(service))
        CFDictionaryAddValue(query, kSecAttrAccount, CFBridgingRetain(ACCOUNT))
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        CFRelease(query)
        if (status != errSecSuccess) return@memScoped null
        val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
        NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
    }

    override fun write(key: String) {
        clear()
        val data = NSString.create(string = key).dataUsingEncoding(NSUTF8StringEncoding)
            ?: throw AiStorageException("no se pudo codificar la clave")
        val query = dictionary()
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(query, kSecAttrService, CFBridgingRetain(service))
        CFDictionaryAddValue(query, kSecAttrAccount, CFBridgingRetain(ACCOUNT))
        CFDictionaryAddValue(query, kSecValueData, CFBridgingRetain(data))
        CFDictionaryAddValue(query, kSecAttrAccessible, kSecAttrAccessibleWhenUnlockedThisDeviceOnly)
        val status = SecItemAdd(query, null)
        CFRelease(query)
        if (status != errSecSuccess) throw AiStorageException("el Keychain rechazó la clave (código $status)")
    }

    override fun clear() {
        val query = dictionary()
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(query, kSecAttrService, CFBridgingRetain(service))
        CFDictionaryAddValue(query, kSecAttrAccount, CFBridgingRetain(ACCOUNT))
        SecItemDelete(query)
        CFRelease(query)
    }

    private fun dictionary() = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)

    private companion object {
        const val ACCOUNT = "anthropic-api-key"
    }
}

/** Ajustes no sensibles de la IA en `NSUserDefaults` (por dispositivo, fuera de la BD exportable). */
class IosAiPreferences(private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults) : AiPreferences {
    override fun getString(name: String): String? = defaults.stringForKey(PREFIX + name)
    override fun putString(name: String, value: String) = defaults.setObject(value, forKey = PREFIX + name)
    override fun remove(name: String) = defaults.removeObjectForKey(PREFIX + name)

    private companion object {
        const val PREFIX = "easyfood."
    }
}
