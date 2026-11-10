package app.luxion.fitarchiveapp.ai

/** Resultado de una operación de IA: valor o error con mensaje en español para el usuario. */
sealed interface AiResult<out T> {
    data class Ok<T>(val value: T) : AiResult<T>
    data class Err(val error: AiError) : AiResult<Nothing>
}

/** Errores de la IA. [message] es apto para mostrar tal cual (nunca contiene la clave). */
sealed class AiError(val message: String, val retryable: Boolean = false) {
    data object NoKey : AiError("La IA está desactivada: falta la clave de API. Pégala en Ajustes de IA.")
    data object Disabled : AiError("La IA está desactivada. Actívala en Ajustes de IA.")
    data object ConsentRequired : AiError("Antes de usar la IA hay que aceptar el aviso de privacidad en Ajustes de IA.")
    data class LimitReached(val limit: Int) : AiError("Has alcanzado el límite de $limit llamadas de hoy. Súbelo en Ajustes de IA o espera a mañana.")
    data object InvalidKey : AiError("La clave de API no es válida o ha sido revocada. Revísala en Ajustes de IA.")
    data class Permission(val detail: String?) : AiError("La clave no tiene permiso para esta operación o este modelo.")
    data object Billing : AiError("Tu cuenta de Anthropic no tiene saldo o hay un problema de facturación. Revísalo en la consola de Anthropic.")
    data class ModelNotFound(val model: String) : AiError("El modelo «$model» no existe o tu cuenta no tiene acceso a él.")
    data object TooLarge : AiError("La petición es demasiado grande (¿imagen muy pesada?). Prueba con una foto más pequeña.")
    data class BadRequest(val detail: String) : AiError("La petición no es válida: $detail")
    data class RateLimited(val retryAfterSeconds: Long?) : AiError(
        if (retryAfterSeconds != null) "Demasiadas peticiones. Espera unos $retryAfterSeconds s e inténtalo de nuevo."
        else "Demasiadas peticiones. Espera un momento e inténtalo de nuevo.",
        retryable = true,
    )
    data object ServerError : AiError("El servicio de Anthropic no está disponible ahora mismo. Inténtalo más tarde.", retryable = true)
    data object Network : AiError("No hay conexión con la API de Anthropic. Comprueba tu conexión a internet.", retryable = true)
    data class Refusal(val category: String?) : AiError("La IA ha rechazado esta petición por sus normas de seguridad. Prueba con otra foto o consulta.")
    data object Truncated : AiError("La respuesta de la IA se ha cortado antes de terminar. Inténtalo de nuevo.", retryable = true)
    data class BadResponse(val detail: String) : AiError("No se pudo interpretar la respuesta de la IA ($detail).")
    data class Storage(val detail: String) : AiError("No se pudo guardar de forma segura en este dispositivo: $detail")
}
