# IA con la clave del usuario

Base técnica de la IA (Fase 13). Cada persona pone su propia clave de la API de Anthropic; no hay servidor nuestro. Interfaz en `docs/design.md` §2.16. Código en `shared/src/*/kotlin/app/luxion/fitarchiveapp/ai/`.

## Cliente de la API (`ClaudeClient`)
No hay SDK oficial de Kotlin Multiplatform, así que se usa HTTP directo con Ktor contra `https://api.anthropic.com`:

| Pieza | Valor |
|---|---|
| Mensajes | `POST /v1/messages` |
| Capacidades del modelo | `GET /v1/models/{id}` (botón «Probar conexión») |
| Cabeceras | `x-api-key`, `anthropic-version: 2026-10-21` |
| Modelo por defecto | `claude-haiku-5-5` (0,10 / 0,50 $ por millón de tokens de entrada / salida con prompts de hasta 100K tokens). Configurable: `claude-sonnet-5-5` (2 / 10 $), `claude-opus-5-5` (4 / 20 $) |
| Esfuerzo | `output_config.effort: "low"` por defecto, siempre enviado (el de Haiku 5.5 sin indicar es `medium`) |
| Salida estructurada | `output_config.format = {type: "json_schema", schema}` (cada objeto con `additionalProperties: false`) |
| Imágenes | bloque `image` con `source.type = base64` y `image/jpeg` |
| No se envía | `temperature`, `top_p`, `top_k` (Haiku 5.5 los rechaza con 400) ni *prefill* (también 400); `thinking` se omite (adaptativo por defecto) |

Errores, siempre en español y sin la clave: 401 clave no válida · 402 / «credit balance» saldo · 403 permiso · 404 modelo inexistente · 413 petición grande · 429 límite de peticiones (con `retry-after`) · 5xx y 529 servicio caído · sin red · `stop_reason: "refusal"` (negativa, con categoría) · `max_tokens` (respuesta cortada) · JSON mal formado. Se reintenta 429, 5xx y fallos de red hasta 2 veces con espera 1 s y 2 s (o el `retry-after` si es de 30 s o menos); 400, 401, 402, 403 y 404 no se reintentan.

Capacidades de imágenes: la documentación vigente (https://platform.claude.com/docs/en/models/overview, consultada el 10-11-2026) dice que **todos los modelos actuales, Haiku 5.5 incluido, aceptan texto e imagen de entrada**, tienen `effort` por defecto `medium` en Haiku 5.5 y 1M de contexto. Sin clave en el entorno de desarrollo no se ha podido hacer la llamada real `GET /v1/models/claude-haiku-5-5`; la comprobación queda en la app: «Probar conexión» lee `capabilities.image_input` y `capabilities.structured_outputs` del modelo elegido y las muestra. Antes de la primera función con foto hay que pulsarlo con una clave real.

## Uso desde el resto de la app
`AiService` es el único punto de entrada: `send`, `askText` y `askJson(request con jsonSchema)`. Antes de llamar exige IA activada, clave guardada, aviso de privacidad aceptado y que no se haya llegado al tope diario (30 por defecto, de 1 a 500). Solo las respuestas 200 cuentan como llamada (una negativa también, porque se factura). `AiRequest` lleva mensajes con partes `AiPart.Text` / `AiPart.Image`; las fotos se preparan con `downscaleToJpeg` (lado mayor 1024 px, JPEG calidad 80, sin metadatos EXIF; `expect/actual` en Android, escritorio e iOS). `AiDaySummary` es el resumen mínimo del día (kcal, macros y objetivos) que podrán enviar las funciones.

## Dónde vive cada dato
| Dato | Dónde | Sale del dispositivo |
|---|---|---|
| Clave de API | Android: Keystore (AES-256-GCM no exportable) · iOS: Keychain (`WhenUnlockedThisDeviceOnly`) · Windows: DPAPI del usuario (vía PowerShell, la clave solo por la entrada estándar) · macOS/Linux: AES-256-GCM con clave en archivo 600 | Solo a Anthropic, en la cabecera `x-api-key` |
| Modelo, activada, aviso aceptado, tope, contadores | Android: SharedPreferences · iOS: `NSUserDefaults` · escritorio: `~/.easyfood/ai/settings.properties` | No |

Nada de esto toca la base de datos SQLDelight, así que **no hay migración** (la 18 sigue reservada a notificaciones) y la clave no puede entrar en exportar/importar ni en la transferencia por QR o red local. En Android `allowBackup` está desactivado y, además, `data_extraction_rules.xml` y `backup_rules.xml` excluyen los dos archivos de ajustes de IA. No hay plugin de *logging* de Ktor, para que las cabeceras no acaben en registros.

Límite conocido: en macOS/Linux la clave se protege con permisos de archivo, no con el llavero del sistema (el comando `security` obligaría a pasar la clave por argumentos del proceso). Si no se puede cifrar con seguridad, guardar falla con un mensaje y no se degrada en silencio.

## Pruebas
Cliente Ktor simulado (`MockEngine`), sin llamadas reales ni en local ni en CI: respuesta correcta, negativa, 401, 402, 429 (con reintentos y `retry-after`), 5xx, red caída y JSON mal formado; servicio (tope, consentimiento, coste); ajustes; almacén de escritorio (incluido DPAPI en Windows); reducción de imágenes; y la pestaña «IA» en la app completa. El Keychain y la reducción de imágenes de iOS solo se comprueban de tipado (`docs/ios-pendiente.md`).
