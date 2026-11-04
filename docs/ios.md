# iOS

La app de iOS es un proyecto Xcode mínimo (`iosApp/`) que embebe el framework `Shared` generado por el módulo `shared` y muestra la UI de Compose Multiplatform. Toda la lógica y las pantallas están en `shared`; `iosApp` solo contiene el arranque en SwiftUI.

**Estado:** el proyecto se escribió sin Mac y **no se ha compilado nunca en Xcode**. La primera compilación real se hace en la sesión del Mac prestado (#29); es probable que haya que retocar algún detalle (ver «Problemas probables»).

## Estructura

| Archivo | Función |
|---|---|
| `shared/build.gradle.kts` | Declara los targets `iosArm64` e `iosSimulatorArm64` y el framework estático `Shared`. |
| `shared/src/iosMain/.../MainViewController.kt` | `MainViewController()`: crea el `ComposeUIViewController` con `App(...)` y la BD SQLDelight (`NativeSqliteDriver`). |
| `iosApp/iosApp/iOSApp.swift` | Punto de entrada SwiftUI (`@main`). |
| `iosApp/iosApp/ContentView.swift` | Envuelve el `UIViewController` de Compose en una vista SwiftUI. |
| `iosApp/iosApp/Info.plist` | Configuración de la app (solo vertical, `CADisableMinimumFrameDurationOnPhone` para Compose). |
| `iosApp/iosApp.xcodeproj` | Proyecto Xcode. Una fase de script llama a `./gradlew :shared:embedAndSignAppleFrameworkForXcode` antes de compilar el Swift. |

Bundle id: `app.luxion.fitarchiveapp.iosApp`. Despliegue mínimo: iOS 15.

## Compilar en un Mac

Requisitos: macOS con **Xcode** (con el SDK de iOS y un simulador) y **JDK 17+**. No hace falta instalar Gradle (se usa `./gradlew`) ni cuenta de desarrollador de pago para el simulador.

1. Clonar el repo y cambiar a la rama que corresponda.
2. Comprobar que `gradlew` es ejecutable: `chmod +x gradlew` (en git está marcado como ejecutable, pero conviene verificarlo).
3. Abrir `iosApp/iosApp.xcodeproj` en Xcode.
4. Elegir el esquema `iosApp` y un simulador (p. ej. iPhone 15) como destino.
5. Pulsar **Run** (⌘R). La primera vez tarda varios minutos: Gradle descarga Kotlin/Native y compila `shared`.

Desde terminal, equivalente:

```
cd iosApp
xcodebuild -project iosApp.xcodeproj -scheme iosApp \
  -destination 'platform=iOS Simulator,name=iPhone 15' build
```

### En un iPhone físico

1. En Xcode, target `iosApp` → *Signing & Capabilities* → marcar *Automatically manage signing* y elegir un *Team* (vale un Apple ID gratuito).
2. Si hace falta, cambiar el *Bundle Identifier* a uno único (p. ej. `com.<tuusuario>.easyfood`).
3. Conectar el iPhone, activar *Modo desarrollador* (Ajustes → Privacidad y seguridad) y elegirlo como destino.
4. La primera vez, confiar en el certificado en Ajustes → General → VPN y gestión de dispositivos.

El escáner (#28) necesitará la clave `NSCameraUsageDescription` en `Info.plist`; todavía no está porque no hay escáner.

## Qué se verificó sin Mac

En Windows, Kotlin/Native no puede enlazar frameworks de Apple, pero sí compilar los klibs de los targets iOS (incluido `MainViewController.kt`):

```
./gradlew :shared:compileIosMainKotlinMetadata :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64
./gradlew :shared:jvmTest :desktopApp:compileKotlinJvm
```

Todo pasa. **No** se pueden ejecutar fuera de un Mac: `linkDebugFrameworkIosSimulatorArm64`, `embedAndSignAppleFrameworkForXcode` ni la compilación del Swift/Xcode.

## Problemas probables en la primera compilación

- **`Permission denied` al ejecutar `./gradlew` desde Xcode**: `chmod +x gradlew`.
- **Xcode no encuentra `JAVA_HOME`/Java**: la fase de script hereda un entorno reducido. Exportar `JAVA_HOME` en la fase de script o en `~/.zprofile`.
- **`No such module 'Shared'`**: la fase «Compile Kotlin Framework» falló o no se ejecutó antes del Swift; mirar el log de esa fase. Se puede lanzar a mano con `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` para ver el error de Gradle.
- **Errores de enlazado con `sqlite3`**: ya se pasa `-lsqlite3` desde `shared/build.gradle.kts`; si persisten, añadir `libsqlite3.tbd` a *Link Binary With Libraries*.
- **Nombre del símbolo Swift**: si `MainViewControllerKt.MainViewController()` no compila, abrir `Shared.h` (en `shared/build/bin/iosSimulatorArm64/debugFramework/Shared.framework/Headers`) y ver el nombre exacto.
- **Safe areas / teclado**: `MainViewController` aplica `safeDrawingPadding()` como en Android; ajustar si se ve mal.
- **Advertencias «The same unique_name found in more than one library»**: son del grafo de dependencias de Compose y no impiden compilar.

## Pendiente en iOS (pantalla «Datos»)

- Guardar y abrir archivos JSON (`DataFiles.ios.kt`) es un stub: falta `UIDocumentPickerViewController` / `UIActivityViewController`. Por ahora iOS transfiere por QR (emitir) y red local.
- Recibir por QR necesita el escáner con AVFoundation (el `BarcodeScanner` de iOS aún no existe).
- `localIpAddresses()` devuelve vacío: falta leer las interfaces con `getifaddrs`; la UI avisa de que no hay dirección.
