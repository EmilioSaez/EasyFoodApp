# iOS: lo no verificado sin Mac

Todo lo siguiente está escrito pero **no se ha ejecutado nunca**. Sin Mac local, se comprobará en CI de macOS (fase 7) o en un Mac/iPhone.

## Verificado desde Windows
- `:shared:compileKotlinIosSimulatorArm64` y `:shared:compileKotlinIosArm64` compilan (tipos de Kotlin/Native, incluido `MainViewController.kt`).

## Sin verificar
- **XcodeGen**: que `iosApp/project.yml` genere un proyecto válido (`xcodegen generate`) y que el esquema `iosApp` exista.
- **Enlazado**: `linkDebugFramework…`/`embedAndSignAppleFrameworkForXcode`; que el framework estático `Shared` quede en `shared/build/xcode-frameworks/<config>/<sdk>` y `-framework Shared` + `-lsqlite3` enlacen.
- **Fase de script**: que Xcode encuentre Java (`JAVA_HOME`) y que `./gradlew` tenga permiso de ejecución.
- **Compilación del Swift** (`iOSApp.swift`, `ContentView.swift`), incluido el nombre del símbolo `MainViewControllerKt.MainViewController()`.
- **Arranque en simulador/iPhone**: Compose, SQLDelight (`NativeSqliteDriver`), red con Ktor, safe areas y teclado.
- **Info.plist y `PrivacyInfo.xcprivacy`**: validez para App Store Connect; los motivos C617.1 y 35F9.1 se copiaron del proyecto hermano y no se han contrastado con las APIs que usa esta app.
- **Icono**: `AppIcon` no tiene imagen todavía (falta un PNG de 1024×1024 sin alfa).
- **Escáner de cámara** (AVFoundation): la cámara solo se puede probar en un iPhone real.
- **Firma, TestFlight y fastlane**: requieren cuenta de Apple Developer de pago (por decidir).
- **CI en macOS**: aún no existe (no copiar workflows hasta que el CI de FitArchiveApp pase en verde).
