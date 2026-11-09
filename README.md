# EasyFoodApp

App multiplataforma (Kotlin Multiplatform + Compose Multiplatform) para **contar calorías con productos de supermercados españoles** (Mercadona/Hacendado, Carrefour, Dia, Lidl…).

## Qué hace

- **Diario del día**: cabecera con el objetivo y cómo se va, franja semanal con racha de días completados, secciones por comida (3 a 6 al día), edición y borrado de entradas.
- **Añadir alimento**: búsqueda de texto en la base local (genérica USDA, Open Food Facts y propios), recientes y accesos rápidos.
- **Escáner de códigos de barras** (Android y iOS) y entrada manual de EAN. La consulta por EAN va a [Open Food Facts](https://world.openfoodfacts.org).
- **Ficha de producto**: cantidad, tabla nutricional por 100 g y aviso cuando los datos faltan o parecen erróneos.
- **Alimento propio**: crear, editar y borrar alimentos introducidos a mano.
- **Recetas**: combinan ingredientes de la base y se añaden al diario.
- **Objetivos y TDEE**: cálculo del gasto energético, ritmo, objetivo fijo y ajuste manual de kcal/macros; actividad sugerida por los pasos diarios (Health Connect / HealthKit).
- **Peso**: registro con gráfica, peso objetivo, estimación de cuándo se alcanza y aviso semanal.
- **Sin cuentas ni servidor**: los datos viven en el dispositivo (offline-first, SQLDelight).

## Plataformas

| Plataforma | Estado |
|---|---|
| Android | Funciona, con escáner de cámara (CameraX + ML Kit). |
| Escritorio (JVM) | Funciona. Sin escáner: se introduce el EAN a mano. |
| iOS | Compila y pasa los tests en el CI de macOS (simulador, sin firma). Escáner con AVFoundation. **Sin probar en iPhone real** ni TestFlight todavía. Ver [docs/ios.md](docs/ios.md). |

## Datos: exportar e importar

La pantalla **Datos** (icono ⇅ del Diario y de Objetivos) exporta el perfil a un JSON versionado (alimentos propios, diario y productos consultados; la base genérica no se exporta porque ya viene con la app) e importa fusionando, con vista previa y detección de duplicados. También transfiere por QR y por red local con código de emparejamiento de un solo uso.

El seguimiento de lo que falta está en [ROADMAP.md](ROADMAP.md).

## Arquitectura

Casi todo el código está en `shared`: una sola base de código para las tres plataformas.

| Módulo | Contenido |
|---|---|
| `shared` | Modelo, dominio (TDEE), datos (Open Food Facts con Ktor, repositorios y stores SQLDelight), exportación/transferencia y toda la UI en Compose Multiplatform. Con código específico por plataforma (`androidMain`, `jvmMain`, `iosMain`) para escáner, ajustes, aleatoriedad segura y drivers de base de datos. |
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
- **Alimentos genéricos** (fruta, pan, carne…): [USDA FoodData Central](https://fdc.nal.usda.gov/), conjunto SR Legacy, dominio público (CC0 1.0). Los nombres están en inglés. Detalles en [docs/generic-foods.md](docs/generic-foods.md).
- BEDCA se descartó por no ofrecer licencia de redistribución.

## Documentación

- [docs/design.md](docs/design.md): especificación de diseño (tokens, pantallas, componentes y navegación).
- [docs/generic-foods.md](docs/generic-foods.md): base genérica de alimentos, fuente, licencia y formato.
- [docs/ios.md](docs/ios.md): app de iOS, cómo compilarla y problemas probables.
- [ROADMAP.md](ROADMAP.md): fases y tareas pendientes.
