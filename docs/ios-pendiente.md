# iOS: pendiente de verificar

Lo que se escribió sin Mac y todavía no se ha probado en un dispositivo real. Qué cubre cada capa de verificación:

- **Windows:** solo tipado de Kotlin/Native (`./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64`).
- **CI de macOS** (`.github/workflows/ios.yml`, `macos-latest`): compila los klibs, enlaza el framework `Shared` para simulador, ejecuta `:shared:iosSimulatorArm64Test`, genera el proyecto con XcodeGen y hace `xcodebuild` al simulador con `CODE_SIGNING_ALLOWED=NO`. Demuestra que compila y enlaza; **no** ejecuta la app ni usa cámara, red real ni firma.
- **iPhone real:** nada se ha probado todavía.

Resuelto desde la versión anterior de este documento: `Dispatchers.IO` ya no está en `commonMain` (`transfer/LanTransfer.kt` usa `Dispatchers.Default`), por lo que los targets iOS compilan.

## Limitaciones del CI actual

- No hay carpeta `shared/src/iosTest`: el paso «Tests iOS (simulador)» no ejecuta tests específicos de iOS. Los tests de `commonTest` solo corren si se ejecutan en ese target.
- El workflow no ejecuta la app en el simulador (no hay UI tests ni arranque).
- Sin firma, sin `archive` y sin subida a TestFlight.

## Escáner de códigos de barras (AVFoundation, #28)

Implementación: `shared/src/iosMain/.../scanner/BarcodeScanner.ios.kt` (`AVCaptureSession` + `AVCaptureMetadataOutput`, EAN-8 y EAN-13). Compila y enlaza en CI; la cámara solo se puede probar en un iPhone (el simulador no tiene cámara).

- [ ] Primer arranque: aparece el diálogo de permiso con el texto de `NSCameraUsageDescription`.
- [ ] Conceder el permiso muestra la previsualización sin recargar la pantalla.
- [ ] Denegar el permiso muestra «Sin acceso a la cámara» y «Abrir ajustes» abre los ajustes de la app (`AppSettings.ios.kt`).
- [ ] Tras activar el permiso en Ajustes y volver, «Conceder permiso» recrea la cámara y funciona.
- [ ] Un EAN-13 y un EAN-8 reales se leen y navegan a la ficha; un UPC-A llega como EAN-13 con un 0 delante.
- [ ] La previsualización respeta el recorte (`clipToBounds`) y el marco de `ScanFrame`; sin barras negras ni desfase al girar o con teclado.
- [ ] Al salir de la pantalla (cerrar, «Escanear otro», navegar a la ficha) se libera la cámara: desaparece el indicador verde de iOS.
- [ ] «Escanear otro» y las lecturas inválidas reabren la sesión sin congelar la vista.
- [ ] Sin cámara (simulador): muestra «No se pudo abrir la cámara» y la entrada manual sigue funcionando.
- [ ] Rendimiento: `startRunning`/`stopRunning` van en una cola aparte; comprobar que no hay tirones al abrir la pantalla.
- [ ] Posible ajuste: `UIKitView` con `UIView` hijo de capa; si hubiera problemas de superposición con Compose, revisar `UIKitInteropProperties`.
- [ ] Recibir datos por QR en la pantalla «Datos» (depende de este escáner) de extremo a extremo con un emisor real.

## Pasos diarios (HealthKit)

Implementación: `shared/src/iosMain/.../steps/StepsProvider.ios.kt` (`HKStatisticsCollectionQuery` de `stepCount`, solo lectura). Solo se ha comprobado el tipado en Windows; el simulador no tiene datos de Salud reales.

- [ ] Habilitar la capability **HealthKit** en el equipo de firma: el entitlement `com.apple.developer.healthkit` está en `iosApp/iosApp/iosApp.entitlements` y requiere una cuenta que lo permita.
- [ ] `NSHealthShareUsageDescription` en `Info.plist` y `-framework HealthKit` en `project.yml`: comprobar que `xcodegen` + `xcodebuild` enlazan.
- [ ] Primer uso en Objetivos: «Permitir acceso a pasos» abre el diálogo de Salud.
- [ ] HealthKit no revela si se denegó la lectura: tras pedir el permiso la app muestra «Hoy: sin datos». Comprobar que ese caso se entiende (y, si no, añadir un aviso para revisar Ajustes > Salud).
- [ ] Los pasos de «hoy» y la media de 14 días coinciden con la app Salud (fecha según el calendario del dispositivo).
- [ ] Revisar `getRequestStatusForAuthorizationToShareTypes` (interpretación de `ShouldRequest`/`Unnecessary`) y que `HKUnit.unitFromString("count")` devuelve la unidad esperada.

## IA: clave en el Keychain y reducción de imágenes
Código en `shared/src/iosMain/.../ai/`. Se comprueba el tipado desde Windows y en CI, pero no se ha ejecutado en iPhone.
- [ ] `KeychainAiKeyVault`: guardar, leer y borrar la clave (`kSecClassGenericPassword`, `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`); comprobar que no pasa a copias de seguridad ni a otro dispositivo.
- [ ] `downscaleToJpeg` (UIKit): foto de la cámara en vertical y horizontal sale derecha, con lado mayor de 1024 px y sin metadatos.
- [ ] `IosAiPreferences` (`NSUserDefaults`): el modelo, el tope y el contador sobreviven a reiniciar la app.

## Pantalla «Datos»: funciones no implementadas en iOS

- **Guardar y abrir archivos JSON** (`files/DataFiles.ios.kt`): es un stub (`canSave`/`canOpen` = `false`, devuelve «aún no está disponible en iOS»). Falta `UIActivityViewController`/`UIDocumentPickerViewController`. Mientras tanto iOS transfiere por QR y red local.
- **Detección de IP local** (`transfer/LocalAddress.ios.kt`): `localIpAddresses()` devuelve una lista vacía. Falta leer las interfaces con `getifaddrs`. La UI avisa de que no hay dirección, así que en iOS no se puede mostrar la IP para recibir por red local.

## Red local (transferencia LAN)

Sin probar en iOS (ni en el simulador ni en iPhone): servidor/cliente Ktor sobre `Dispatchers.Default`, con conexión entre un iPhone y otro dispositivo.

- [ ] Primera conexión: iOS 14+ pide el permiso de **red local**. Comprobar si hace falta `NSLocalNetworkUsageDescription` (no está en `Info.plist`) y, si se usa Bonjour, `NSBonjourServices`.
- [ ] Enviar y recibir entre iPhone y Android/escritorio en la misma wifi.
- [ ] Comportamiento al pasar la app a segundo plano durante la transferencia.
- [ ] Origen del código de emparejamiento aleatorio (`SecureRandom.ios.kt`): comprobar que usa una fuente segura en dispositivo.

## Recordatorios (notificaciones locales)

Implementación: `shared/src/iosMain/.../reminders/ReminderScheduler.ios.kt` (`UNUserNotificationCenter`, `UNCalendarNotificationTrigger` de un solo disparo). Solo se ha comprobado el tipado en Windows (`compileKotlinIosSimulatorArm64` y `compileKotlinIosArm64`).

- [ ] Al activar «Recordatorios» en Objetivos aparece el diálogo de permiso de notificaciones; denegarlo muestra el aviso y «Abrir ajustes» abre los ajustes de la app.
- [ ] Las notificaciones llegan a la hora elegida con la app cerrada (hora local del dispositivo, también tras cambiar de zona horaria u hora de verano).
- [ ] Con la app en primer plano no salen (no hay `UNUserNotificationCenterDelegate`): decidir si se quiere mostrarlas con banner.
- [ ] Registrar una comida o el peso y volver a la pantalla anterior retira el aviso de hoy (reprograma con `removeAllPendingNotificationRequests`).
- [ ] Límite de 64 notificaciones pendientes: el plan de 7 días con 6 comidas, peso y agua suma 56 como máximo.
- [ ] Tocar la notificación abre la app (sin navegar a una pantalla concreta).
- [ ] Las propiedades de `NSDateComponents` (`year`, `month`, `day`, `hour`, `minute`) como `Long` compilan en el CI de macOS.

## Aplicación y proyecto Xcode

- [ ] La app arranca en el simulador y en un iPhone (nunca se ha ejecutado, solo compilado en CI).
- [ ] Base de datos SQLDelight con `NativeSqliteDriver`: crea y migra la BD en el dispositivo; `-lsqlite3` enlaza (lo hace el CI) y funciona en ejecución.
- [ ] Copias de seguridad (`BackupFiles.ios.kt`, `MainViewController`): solo se ha escrito, no compilado ni probado. Comprobar que `databasePath()` encuentra el archivo real de `NativeSqliteDriver`, que se hace la copia antes de migrar (instalar una versión, subir `Schema.version` y reabrir), que se restaura si la migración falla y que la copia semanal aparece en «Datos».
- [ ] Safe areas, teclado y orientación solo vertical (`safeDrawingPadding()` en `MainViewController`).
- [ ] Botón/gesto de atrás (`PlatformBackHandler.ios.kt`): gesto de borde de iOS frente a la navegación de Compose.
- [ ] `CADisableMinimumFrameDurationOnPhone` y fluidez en ProMotion.
- [ ] El nombre del símbolo Swift `MainViewControllerKt.MainViewController()` se resolvió en CI; revisar solo si cambia el módulo.
- [ ] `PrivacyInfo.xcprivacy`: validar en la subida a App Store Connect que las APIs de motivo requerido declaradas (C617.1 y 35F9.1) son las que se usan y que no falta ninguna.

## Firma, distribución y publicación (sin hacer)

- [ ] **Icono de la app:** `AppIcon.appiconset` no tiene imagen; hace falta un PNG de 1024×1024 sin canal alfa antes de subir a App Store Connect.
- [ ] **Firma:** no hay equipo ni perfiles en `project.yml` (`CODE_SIGN_STYLE: Automatic`, sin `DEVELOPMENT_TEAM`). Probada solo la compilación sin firma.
- [ ] **Bundle id:** `app.luxion.fitarchiveapp` (heredado de FitArchiveApp). Confirmar que es el definitivo y que no choca con la otra app.
- [ ] **Cuenta de Apple Developer de pago:** decidir si compensa (ver ROADMAP, fase 7).
- [ ] **TestFlight con fastlane:** clave API de App Store Connect, `archive`/export del `.ipa`, subida y número de build incremental. Ni fastlane ni secretos en CI están configurados.
- [ ] **Prueba en iPhone real** vía TestFlight, o en un Mac prestado (Xcode con Apple ID gratuito, ver `docs/ios.md`).
- [ ] Cumplimiento de App Store: `ITSAppUsesNonExemptEncryption=NO` declarado; revisar descripción de privacidad y capturas.
