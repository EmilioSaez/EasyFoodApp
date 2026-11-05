# iOS

La app de iOS es un proyecto Xcode mínimo (`iosApp/`) que embebe el framework `Shared` generado por el módulo `shared` y muestra la UI de Compose Multiplatform. Toda la lógica y las pantallas están en `shared`; `iosApp` solo contiene el arranque en SwiftUI.

**Estado:** el proyecto se escribió sin Mac y **no se ha compilado nunca en Xcode**. El `.xcodeproj` ya no se versiona: se genera con [XcodeGen](https://github.com/yonaskolb/XcodeGen) desde `iosApp/project.yml`. Lo no verificable sin Mac está en [`ios-pendiente.md`](ios-pendiente.md).

## Estructura

| Archivo | Función |
|---|---|
| `shared/build.gradle.kts` | Declara los targets `iosArm64` e `iosSimulatorArm64` y el framework estático `Shared`. |
| `shared/src/iosMain/.../MainViewController.kt` | `MainViewController()`: crea el `ComposeUIViewController` con `App(...)` y la BD SQLDelight (`NativeSqliteDriver`). |
| `iosApp/project.yml` | Definición del proyecto (fuente de verdad). Incluye la fase de script que llama a `./gradlew :shared:embedAndSignAppleFrameworkForXcode` antes de compilar el Swift. |
| `iosApp/iosApp/iOSApp.swift` | Punto de entrada SwiftUI (`@main`). |
| `iosApp/iosApp/ContentView.swift` | Envuelve el `UIViewController` de Compose en una vista SwiftUI. |
| `iosApp/iosApp/Info.plist` | Solo vertical, `NSCameraUsageDescription` (escáner), `ITSAppUsesNonExemptEncryption=NO`, `CADisableMinimumFrameDurationOnPhone` para Compose. |
| `iosApp/iosApp/PrivacyInfo.xcprivacy` | Sin tracking ni datos recopilados; APIs de motivo requerido: marcas de tiempo de archivos (C617.1) y hora de arranque (35F9.1). |
| `iosApp/iosApp/Assets.xcassets` | Catálogo con `AccentColor` y `AppIcon` (aún sin imagen: falta el icono de 1024×1024 sin alfa). |

Bundle id: `app.luxion.fitarchiveapp`. Despliegue mínimo: **iOS 17**. Producto: `EasyFood`. Versión: `MARKETING_VERSION` / `CURRENT_PROJECT_VERSION` en `project.yml`.

## Compilar en un Mac

Requisitos: macOS con **Xcode** (SDK de iOS y un simulador), **JDK 17+** y XcodeGen (`brew install xcodegen`). No hace falta instalar Gradle (se usa `./gradlew`) ni cuenta de desarrollador de pago para el simulador.

1. Clonar el repo y cambiar a la rama que corresponda.
2. Comprobar que `gradlew` es ejecutable: `chmod +x gradlew`.
3. Generar el proyecto: `cd iosApp && xcodegen generate` (crea `iosApp/iosApp.xcodeproj`, ignorado por git; repetir si cambia `project.yml` o se añaden archivos).
4. Abrir `iosApp/iosApp.xcodeproj` en Xcode, elegir el esquema `iosApp` y un simulador como destino.
5. Pulsar **Run** (⌘R). La primera vez tarda varios minutos: Gradle descarga Kotlin/Native y compila `shared`.

Desde terminal, equivalente (sin firma):

```
cd iosApp && xcodegen generate
xcodebuild -project iosApp.xcodeproj -scheme iosApp -destination 'generic/platform=iOS Simulator' ARCHS=arm64 CODE_SIGNING_ALLOWED=NO build
```

### En un iPhone físico

1. En Xcode, target `iosApp` → *Signing & Capabilities* → *Automatically manage signing* y elegir un *Team* (vale un Apple ID gratuito). La firma no está en `project.yml`.
2. Si hace falta, cambiar el *Bundle Identifier* a uno único (con Apple ID gratuito, p. ej. `com.<tuusuario>.easyfood`).
3. Conectar el iPhone, activar *Modo desarrollador* (Ajustes → Privacidad y seguridad) y elegirlo como destino.
4. La primera vez, confiar en el certificado en Ajustes → General → VPN y gestión de dispositivos.

El escáner (AVFoundation) necesita la clave `NSCameraUsageDescription`, ya presente en `Info.plist`. El simulador no tiene cámara: allí el escáner muestra «No se pudo abrir la cámara» y queda la entrada manual.

## Qué se verificó sin Mac

En Windows, Kotlin/Native no puede enlazar frameworks de Apple, pero sí compilar los klibs de los targets iOS (incluido `MainViewController.kt`):

```
./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64
./gradlew :shared:jvmTest :desktopApp:compileKotlinJvm
```

Estado (fase 7): ambos targets compilan. La primera comprobación falló por un error real que el resto de targets no veía: `LanTransfer.kt` (commonMain) usaba `Dispatchers.IO`, que es `internal` al compilar para Native. Se sustituyó por `Dispatchers.Default` (Ktor trabaja con E/S no bloqueante, así que no cambia el comportamiento).

**No** se pueden ejecutar fuera de un Mac: `linkDebugFrameworkIosSimulatorArm64`, `embedAndSignAppleFrameworkForXcode`, `xcodegen generate` ni la compilación del Swift/Xcode. Detalle en [`ios-pendiente.md`](ios-pendiente.md).

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
