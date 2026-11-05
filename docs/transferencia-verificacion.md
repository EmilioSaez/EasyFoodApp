# Transferencia de datos: verificación

Verificación de punta a punta de la transferencia por red local y por QR (tarea p10). Los tests están en
`shared/src/jvmTest/.../transfer/TransferEndToEndTest.kt` y `.../export/qr/QrCodeEncoderTest.kt`.

## Cómo se ha verificado

Escritorio contra escritorio (el emulador Android va detrás de NAT): dos dispositivos simulados con su propia base
SQLite, un `LanTransferHost` y un `LanTransferClient` con sockets TCP reales en loopback, y el mismo
`DataPresenter` / `QrReceiveModel` que usa la pantalla «Datos».

| Caso | Resultado |
| --- | --- |
| Red local: emisión, código, vista previa, confirmación e importación; las dos bases quedan con el mismo contenido | OK |
| Red local: segunda transferencia idéntica, todo duplicado y sin nada que confirmar | OK |
| Red local: código erróneo (fase de error, nada escrito, el código bueno aún funciona), un solo uso, caducidad, intentos máximos, peticiones basura | OK |
| Red local: 5 clientes simultáneos, solo uno recibe los datos | OK |
| Red local: exportación de más de 2 MB, 15 veces seguidas, íntegra | OK (tras corregir el bug 1) |
| Fusión: añade lo nuevo, cuenta duplicados y conflictos, conserva lo local y no borra nada | OK |
| QR: cada fragmento se dibuja con el generador propio y se decodifica con ZXing; orden aleatorio, repeticiones, un QR ajeno y un fragmento dañado; reensamblado, vista previa e importación | OK |
| QR: textos con Unicode (ñ, emoji) con varios tamaños de fragmento; repetir la transferencia deja solo duplicados | OK |
| Generador de QR: todas las longitudes de 1 a 900 y las 8 máscaras | OK |

## Bugs encontrados y corregidos

1. **Transferencias grandes por red local fallaban de forma intermitente** («No se pudo conectar»). El anfitrión
   cerraba el socket justo después de escribir, cuando parte de los bytes seguía en el búfer, y el cliente recibía
   la respuesta truncada. Ahora espera a que el cliente cierre su lado (con límite de 30 s). Test:
   `lan transfers a multi megabyte export intact every time`.

## Hallazgo que no era un bug

El detector de ZXing falla con ~3 % de los QR sintéticos perfectos, incluidos los de su propio `QRCodeWriter`
(1200 longitudes probadas). Con `PURE_BARCODE` decodifica todos los nuestros y la decodificación directa de la
matriz también. Por eso los tests decodifican con `PURE_BARCODE` (`QrTestSupport.kt`). Si en un móvil real algún
fragmento no se lee, basta con volver a escanearlo: el receptor tolera repeticiones y cualquier orden.

## No verificado

- **Cámara real** (Android): la lectura de QR con CameraX y ML Kit se simula en los tests; no se ha probado
  apuntando a otra pantalla (enfoque, brillo, tamaño de los códigos con `QrFrames.CHUNK_SIZE = 400`).
- **Red local en dispositivos reales**: solo se ha probado en loopback. Falta comprobar entre dos dispositivos en
  la misma wifi (cortafuegos de Windows, aislamiento de clientes del router, detección de la IP local en Android).
- **iOS** (sin Mac): la detección de la IP local no existe (hay que escribirla a mano) y guardar/abrir archivos es
  un stub; ver `docs/ios.md` y `docs/ios-pendiente.md`. El escáner de QR de iOS tampoco se ha probado en un iPhone.
- El tráfico por red local va **sin cifrar**; el código solo evita que un tercero lo pida (ver `LanTransfer.kt`).
