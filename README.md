# EasyFoodApp

App multiplataforma (Kotlin Multiplatform + Compose Multiplatform) para **contar calorías con productos de supermercados españoles** (Mercadona/Hacendado, Carrefour, Dia, Lidl…).

## Qué hace

- **Diario del día**: totales de kcal y macros, secciones por comida, progreso frente al objetivo, edición y borrado de entradas.
- **Añadir alimento**: búsqueda de texto en la base local (por nombre, marca o supermercado como «mercadona» o «lidl»; tolera acentos, plurales y erratas, prioriza lo que más usas y tiene el filtro «Solo mis productos») y accesos rápidos (recientes, favoritos, plantillas, escáner y alimento propio); los alimentos se marcan como favoritos con una estrella. Las **plantillas de comida** («mi desayuno de siempre») se guardan desde una comida del Diario o desde la pestaña Alimentos y se añaden al diario de un toque, a la comida y fecha que elijas.
- **Escáner de códigos de barras** (solo Android) y entrada manual de EAN. La consulta por EAN va a [Open Food Facts](https://world.openfoodfacts.org).
- **Ficha de producto**: cantidad, tabla nutricional por 100 g y aviso cuando los datos faltan o parecen erróneos. Los productos de Open Food Facts se refrescan a mano («Actualizar datos») y en segundo plano (máx. 20 peticiones por arranque, sin red no hace nada) y la ficha avisa de los cambios nutricionales relevantes; el diario conserva los valores con los que registraste cada entrada ([detalles](docs/refrescar-productos.md)).
- **Alimento propio**: crear, editar y borrar alimentos introducidos a mano.
- **Objetivos y TDEE**: cálculo del gasto energético, ritmo y ajuste manual de kcal/macros.
- **Agua diaria**: tarjeta en el Diario con «+ Vaso», «+ 500 ml», «+ 1 L» y deshacer, y progreso frente a un objetivo (2 L por defecto, editable en Objetivos).
- **IA opcional** (pestaña «IA»): base técnica para funciones con Claude usando tu propia clave de API de Anthropic. La clave se guarda solo en el dispositivo, de forma protegida (Keystore, Keychain o DPAPI), y nunca entra en la base de datos, en exportar/importar ni en copias de seguridad. Sin clave la IA queda desactivada y el resto de la app no cambia. Ver [docs/ia.md](docs/ia.md).
- **Ejercicio**: registro manual de actividades (nombre, kcal y duración opcional) por día en el Diario; las kcal gastadas suben el objetivo del día. En Objetivos eliges si se suma todo, un porcentaje (50 % por defecto, porque el nivel de actividad del TDEE ya cuenta el ejercicio habitual) o nada.
- **Sin cuentas ni servidor**: los datos viven en el dispositivo (offline-first, SQLDelight).

## Plataformas

| Plataforma | Estado |
|---|---|
| Android | Funciona, con escáner de cámara (CameraX + ML Kit). |
| Escritorio (JVM) | Funciona. Sin escáner: se introduce el EAN a mano. |
| iOS | Compila y pasa los tests en el CI de macOS (simulador, sin firma). Escáner con AVFoundation. **Sin probar en iPhone real** ni TestFlight todavía. Ver [docs/ios.md](docs/ios.md). |

## Datos: exportar e importar

La pantalla **Datos** (icono ⇅ del Diario y de Objetivos) exporta el perfil a un JSON versionado (alimentos propios, favoritos, plantillas de comida, diario, pesos, recetas, agua, ejercicio y productos consultados; la base genérica no se exporta porque ya viene con la app) e importa fusionando, con vista previa y detección de duplicados. También transfiere por QR y por red local con código de emparejamiento de un solo uso.

**Entrada rápida:** para cuando comes fuera, el botón «Entrada rápida» de la pestaña Alimentos (y el «+» de cada comida del Diario) apunta solo kcal y macros, con nombre opcional, a una comida y día concretos, sin crear un alimento. Se edita, se borra, cuenta en los totales y se copia como cualquier entrada, y viaja en exportar e importar.

El seguimiento de lo que falta está en [ROADMAP.md](ROADMAP.md).

## Arquitectura

Casi todo el código está en `shared`: una sola base de código para las tres plataformas.

| Módulo | Contenido |
|---|---|
| `shared` | Modelo, dominio (TDEE), datos (Open Food Facts con Ktor, repositorios y stores SQLDelight), exportación/transferencia y toda la UI en Compose Multiplatform. Incluye el cliente de la API de Claude (`ai`). Con código específico por plataforma (`androidMain`, `jvmMain`, `iosMain`) para escáner, ajustes, aleatoriedad segura y drivers de base de datos. |
| `desktopApp` | Aplicación de escritorio (JVM). Solo arranca `App` de `shared`. |
| `androidApp` | Aplicación Android. |
| `iosApp` | Proyecto Xcode que embebe el framework `Shared`. |

Tecnologías: Kotlin Multiplatform, Compose Multiplatform (Material 3), SQLDelight (esquema en `shared/src/commonMain/sqldelight`), Ktor y kotlinx.serialization.

Paquete: `app.luxion.fitarchiveapp` (applicationId de Android: `app.luxion.fitarchiveapp.android`).

## Compilar y ejecutar

Requiere JDK 17 o superior. Android necesita el SDK instalado; iOS, un Mac con Xcode.

```
./gradlew :desktopApp:run                  # escritorio: abre la app
./gradlew :androidApp:assembleDebug        # Android: genera el APK de depuración
./gradlew :androidApp:installDebug         # Android: instala en un dispositivo o emulador
```

iOS: abrir `iosApp/iosApp.xcodeproj` en Xcode y ejecutar el esquema `iosApp`. Los pasos completos están en [docs/ios.md](docs/ios.md).

## Tests

```
./gradlew :shared:jvmTest                  # tests comunes y de JVM (SQLDelight, exportación, red local)
```

Para comprobar que compilan los targets de iOS sin Mac (solo klibs, no enlaza el framework):

```
./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64
```

## Datos y licencias

- **Productos con código de barras**: [Open Food Facts](https://world.openfoodfacts.org), base abierta aportada por la comunidad bajo licencia ODbL. Los datos pueden ser incorrectos o estar incompletos; la app los marca para revisión cuando detecta valores dudosos.
- **Alimentos genéricos** (fruta, pan, carne…): [USDA FoodData Central](https://fdc.nal.usda.gov/), conjunto SR Legacy, dominio público (CC0 1.0). Los nombres están traducidos al español (traducción propia con IA, embebida y offline). Detalles en [docs/generic-foods.md](docs/generic-foods.md).
- BEDCA se descartó por no ofrecer licencia de redistribución.

## Documentación

- [docs/design.md](docs/design.md): especificación de diseño (tokens, pantallas, componentes y navegación).
- [docs/generic-foods.md](docs/generic-foods.md): base genérica de alimentos, fuente, licencia y formato.
- [docs/ia.md](docs/ia.md): IA con la clave del usuario (API de Claude, privacidad, límites y almacenamiento de la clave).
- [docs/ios.md](docs/ios.md): app de iOS, cómo compilarla y problemas probables.
- [ROADMAP.md](ROADMAP.md): fases y tareas pendientes.
