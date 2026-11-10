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
`AiService` es el único punto de entrada: `send`, `askText` y `askJson(request con jsonSchema)`. Antes de llamar exige IA activada, clave guardada, aviso de privacidad aceptado y que no se haya llegado al tope diario (30 por defecto, de 1 a 500). Solo las respuestas 200 cuentan como llamada (una negativa también, porque se factura). `AiRequest` lleva mensajes con partes `AiPart.Text` / `AiPart.Image`; las fotos se preparan con `downscaleToJpeg` (lado mayor 1024 px, JPEG calidad 80, sin metadatos EXIF; `expect/actual` en Android, escritorio e iOS). `AiDaySummary` es el resumen mínimo del día (kcal, macros y objetivos) que envían las funciones.

## Sugerencias y consejos (pestaña «IA»)
Tres funciones sobre `AiService`, cada una con **una sola llamada** por pulsación (cuenta contra el tope diario). Código en `ai/AiAdvice.kt` (prompts, esquemas, lectura de respuestas, `AiAdvisor`), `ai/AiAdviceSource.kt` (resumen mínimo desde los almacenes) y `ui/screens/AiTab*.kt`, `AiAdviceCards.kt`.

| Función | Llamada | Qué se envía | Salida |
|---|---|---|---|
| «¿Qué como?» | `askJson` · `max_tokens` 1 400 | Totales y objetivos del día (objetivo de kcal con el bonus de ejercicio), lo que queda de kcal y macros, comida actual y lo ya apuntado en ella (nombre y kcal), hasta 6 favoritos, 6 recientes, 6 plantillas y 6 recetas (nombre, cantidad habitual y kcal; los recientes no repiten favoritos ni incluyen entradas rápidas), preferencias libres (200 caracteres) | `{options:[{name, amount, kcal, proteinG, carbsG, fatG, reason}]}`; se descartan las opciones sin nombre o con kcal fuera de 0–3 000 y se quedan 5 como máximo |
| «¿Cómo voy?» | `askJson` · 900 | Totales y objetivos del día, agua, ejercicio, fibra, sal, azúcares y saturadas (objetivos orientativos de `IntakeGuidelines`); con «Esta semana», además las medias del lunes a hoy (kcal, macros, fibra, sal, azúcares, días con registro y días dentro del objetivo) | `{summary, points:[{topic, status: ok\|alto\|bajo, advice}]}` (hasta 4 puntos) |
| Pregunta libre | `askText` · 600 | Resumen del día (como arriba, sin candidatos) + los **últimos 4 mensajes** de la conversación + la pregunta (300 caracteres) | Texto breve (máx. 6 frases por instrucción) |

- **Privacidad:** nunca nombre, correo, pesos ni historial completo (ninguna fecha pasada viaja), y de las recetas solo el nombre y las kcal por ración. El texto libre se limpia (sin saltos ni `<`/`>`, longitud acotada) y va entre `<usuario>…</usuario>`, con la instrucción de tratarlo como dato. Como estos datos no estaban en el aviso original, se amplió `AiPrivacy` y la pestaña pide un **aviso propio una vez** («Antes de empezar»; `AiSettingsStore.hasAck("advice")`, en las mismas preferencias del dispositivo) antes de la primera llamada.
- **Sin consejo médico:** el *system prompt* prohíbe diagnosticar, pautar dietas para enfermedades o medicación y pide remitir a un profesional; la pestaña muestra un aviso fijo.
- **Añadir al diario:** `DiaryStore.add` con `QuickEntry.new` (cifras redondeadas, en la comida más cercana a la hora actual); se puede deshacer o abrir con «Ajustar» (`Route.QuickEntry(entryId)`). No hay tabla nueva.
- **Estados:** sin clave y desactivada (estado vacío), aviso sin aceptar y tope alcanzado (botones deshabilitados; `AiService.blocker()` lo vuelve a comprobar), error (con «Reintentar» si `retryable`), rechazo de la IA (sin reintento) y fallo al leer la base local.
- **Gasto:** Haiku 5.5 con `effort: low`; una consulta típica ronda 1 000 tokens de entrada y 150–400 de salida, unas milésimas de dólar (la cifra real, en «Uso» de los ajustes).

### Añadir una función a la pestaña
La pestaña tiene dos puntos de extensión en `ui/screens/AiTabRegistry.kt`, para que «Foto del plato», «Etiqueta» u otras se enganchen sin tocar `AiScreen`:
1. **Acción** (`AiTabAction(id, label, icon, onClick)`): botón de la fila «Herramientas». `onClick` recibe un `AiTabEnvironment` (navegador, módulo de IA, diario, `today`, `mealChoices`) y normalmente hace `env.navigator.push(Route.…)`. La fila no aparece si la lista está vacía.
2. **Tarjeta** (`AiTabCard(id, content)`): tarjeta propia en la pestaña, tras las de sugerencias, dibujada con `AiCard(título) { … }`.

Cada función define su acción o tarjeta en **su propio archivo** y añade **una línea** a `aiTabActions` o `aiTabCards`, en el hueco comentado con su nombre. La pestaña ya resuelve sin clave, desactivada, aviso y tope. Para llamar a la API, usar siempre `AiService` (nunca `ClaudeClient` directamente) para respetar el tope y el consentimiento.

## Etiqueta nutricional por foto (`LabelReader`)
`LabelReader` (en `ai/`) usa `AiService.askJson` con una imagen y un esquema JSON estricto; no hay otro cliente. Flujo y pantallas en `docs/design.md` §2.20.

- **Petición:** foto reducida con `downscaleToJpeg` (1024 px, JPEG 80, sin EXIF), prompt de sistema que prohíbe inventar o estimar (lo que no se ve, `null`), pide kcal (no kJ) y valores por 100 g (convierte desde la porción solo si la etiqueta da su peso; sal = sodio × 2,5) y trata el texto de la imagen como datos, no como instrucciones. `maxTokens` 1500, `effort` low, modelo de los ajustes.
- **Esquema:** `name`, `brand`, `ingredients`, `serving_label`, `serving_size_g` y `per_100g` (`kcal`, `fat_g`, `saturated_fat_g`, `carbs_g`, `sugars_g`, `fiber_g`, `protein_g`, `salt_g`). Todos obligatorios y anulables (`anyOf` número o texto y `null`), con `additionalProperties: false`.
- **Lectura defensiva:** texto vacío, números negativos, no finitos o que no sean numéricos se tratan como no leídos (`null`). Nada se rellena con valores por defecto: el usuario ve los huecos marcados y los completa. Una respuesta con todo a `null` se trata como «no parece una etiqueta».
- **Errores y tope:** los de `AiService` (sin clave, desactivada, sin aviso, tope diario, 401, 402, 429, 5xx, red, negativa, corte, JSON mal formado) llegan tal cual a la pantalla, que conserva la foto. Una respuesta 200 cuenta para el tope (también si la etiqueta resulta ilegible).
- **La foto no toca el disco:** Android la recibe en memoria de CameraX (`ImageCapture.OnImageCapturedCallback`; la galería usa `GetContent` y se lee del flujo), iOS de `UIImagePickerController` (`UIImage` en memoria) y escritorio lee el archivo elegido. Solo se retiene mientras la pantalla está abierta, para reintentar. Entra en el aviso de privacidad ya existente («la foto que elijas»).
- **Sin migración:** el resultado se guarda como alimento propio normal. Los ingredientes se muestran al revisar pero no se persisten.

## Dónde vive cada dato
| Dato | Dónde | Sale del dispositivo |
|---|---|---|
| Clave de API | Android: Keystore (AES-256-GCM no exportable) · iOS: Keychain (`WhenUnlockedThisDeviceOnly`) · Windows: DPAPI del usuario (vía PowerShell, la clave solo por la entrada estándar) · macOS/Linux: AES-256-GCM con clave en archivo 600 | Solo a Anthropic, en la cabecera `x-api-key` |
| Modelo, activada, aviso aceptado, avisos por función, tope, contadores | Android: SharedPreferences · iOS: `NSUserDefaults` · escritorio: `~/.easyfood/ai/settings.properties` | No |

Nada de esto toca la base de datos SQLDelight, así que **no hay migración** (la 18 sigue reservada a notificaciones) y la clave no puede entrar en exportar/importar ni en la transferencia por QR o red local. En Android `allowBackup` está desactivado y, además, `data_extraction_rules.xml` y `backup_rules.xml` excluyen los dos archivos de ajustes de IA. No hay plugin de *logging* de Ktor, para que las cabeceras no acaben en registros.

Límite conocido: en macOS/Linux la clave se protege con permisos de archivo, no con el llavero del sistema (el comando `security` obligaría a pasar la clave por argumentos del proceso). Si no se puede cifrar con seguridad, guardar falla con un mensaje y no se degrada en silencio.

## Pruebas
Cliente Ktor simulado (`MockEngine`), sin llamadas reales ni en local ni en CI: respuesta correcta, negativa, 401, 402, 429 (con reintentos y `retry-after`), 5xx, red caída y JSON mal formado; servicio (tope, consentimiento, coste); ajustes; almacén de escritorio (incluido DPAPI en Windows); reducción de imágenes; los prompts, esquemas y lectura tolerante de sugerencias y consejos (incluido que no viaje nada de cuenta ni historial y que la conversación se recorte a 4 mensajes); el resumen del día y de la semana desde los almacenes; el estado de la pestaña (tope, aviso, errores, rechazo, añadir y deshacer en el diario); y la pestaña «IA» en la app completa, con la API simulada. El Keychain y la reducción de imágenes de iOS solo se comprueban de tipado (`docs/ios-pendiente.md`). Etiqueta por foto (`LabelReaderTest`, `LabelFormTest`, `LabelPhotoScreenTest`): respuesta simulada correcta, con huecos, vacía, negativa, 401, JSON mal formado o cortado, imagen no decodificable, sin clave, desactivada, sin aviso o con el tope diario (sin ninguna llamada), foto reducida a 1024 px en la petición, la clave ausente de cuerpo y errores, esquema estricto, y el flujo de pantalla (éxito con EAN, error que conserva la foto y reintento, rechazo sin reintento). El Keychain y la reducción de imágenes de iOS solo se comprueban de tipado (`docs/ios-pendiente.md`).

## Pendiente de verificar con una clave real
No se ha hecho ninguna llamada real (sin clave en desarrollo). Con una clave: pulsar «Probar conexión» y comprobar que `claude-haiku-5-5` admite salida estructurada; probar «¿Qué como?», «¿Cómo voy?» (día y semana) y una pregunta; comprobar que el JSON cumple el esquema, que las cifras de las opciones son razonables, que el tono es claro y sin consejo médico, que el tope y el gasto de «Uso» suben como se espera y que «Añadir al diario» deja la entrada en la comida correcta.
