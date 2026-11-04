# iOS: pendiente de verificar

Lo que se escribió sin Mac y solo se puede comprobar en un iPhone (o en CI de macOS). Desde Windows solo se verifica el tipado con `./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64`.

## Escáner de códigos de barras (AVFoundation, #28)

Implementación: `shared/src/iosMain/.../scanner/BarcodeScanner.ios.kt` (`AVCaptureSession` + `AVCaptureMetadataOutput`, EAN-8 y EAN-13).

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

## Problemas conocidos del tipado

- `Dispatchers.IO` en `commonMain` (`transfer/LanTransfer.kt`, líneas 96, 97 y 199) no compila para iOS con coroutines 1.11.0 (`it is internal`). Es anterior a este cambio; para comprobar el escáner se sustituyó temporalmente por `Dispatchers.Default` sin commitearlo.
