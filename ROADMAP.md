# Roadmap

Ritmo estimado: 5-10 h/semana, hitos de 1-2 semanas. Uso personal: sin publicación en tiendas. El único CI previsto es el de macOS para iOS (fase 7).
Cada fase es un milestone de GitHub y cada tarea una issue.

Android, iOS y escritorio compilan desde la fase 1. Lo único que se retrasa en iOS es probarlo: sin Mac, se compila y prueba en CI de macOS (GitHub Actions) y en iPhone real vía TestFlight (fase 7).

## Fase 0: Esqueleto (hecho)

- [x] Cliente de Open Food Facts por EAN, modelo `Product` y app de escritorio (PR #1)

## Fase 1: Datos sólidos (~2 sem)

Riesgo: calidad de datos de Open Food Facts.

- [x] Ampliar `Product`: macros completos (grasas, saturadas, hidratos, azúcares, fibra, proteína, sal) y tamaño de porción
- [x] Aviso de datos dudosos: >900 kcal/100 g, macros incoherentes con las kcal, campos ausentes
- [x] Declarar los targets Android e iOS en `shared` y comprobar que compilan
- [x] Integrar SQLDelight y esquema de productos
- [x] Repositorio con caché local (red → base de datos)

## Fase 2: Diario (~2 sem)

- [x] Esquema del diario: entradas por día y por comida (desayuno, comida, cena, snacks)
- [x] Registrar cantidad en gramos o porciones
- [x] Pantalla de diario con totales diarios de kcal y macros
- [x] Navegación entre pantallas
- [x] Editar y borrar entradas

## Fase 3: Android y escáner (~2 sem)

Riesgo: escáner distinto en cada plataforma.

- [x] Módulo `androidApp`
- [x] Escáner de códigos de barras con CameraX y ML Kit (`expect`/`actual`)
- [x] Permiso de cámara y manejo de errores
- [x] Entrada manual de EAN en escritorio
- [ ] (Opcional) Flujo Maestro de escaneo en emulador

## Fase 4: Alimentos sin código (~2-3 sem)

Riesgo: productos sin código de barras (fruta, pan, carne).

- [x] Alimentos propios con entrada manual
- [x] Importar base genérica USDA (SR Legacy, CC0) embebida; BEDCA descartada por licencia (ver docs/generic-foods.md). Falta lanzar la importación en el arranque
- [x] Búsqueda por texto (FTS)
- [x] Mostrar el origen del dato en la UI (Open Food Facts, genérico, propio)

## Fase 5: Objetivos (~1-2 sem)

- [x] Objetivo diario de kcal
- [x] Objetivo de macros (proteína, hidratos, grasa)
- [x] Calculadora de necesidades (TDEE) a partir de edad, peso, altura y meta
- [x] Progreso frente al objetivo en el diario

## Fase 6: Exportar e importar (~2 sem)

Riesgo: mover datos entre dispositivos sin servidor ni cuentas.

- [x] Formato JSON versionado y exportación
- [x] Importación con fusión y detección de duplicados
- [x] Transferencia por QR (lógica en `shared`; falta la UI)
- [x] Transferencia por red local (protocolo en `shared`; falta la UI)

## Fase 7: iOS sin Mac (~1-2 sem)

Riesgo: no hay Mac local. La ruta es la que sigue FitArchiveApp (sesión hermana): comprobar tipos de Kotlin/Native desde Windows, compilar y probar en CI de macOS y distribuir por TestFlight. Referencia: PRs #69, #70 y #71 de FitArchiveApp; no copiar workflows hasta que su CI pase en verde.

- [x] Módulo `iosApp` (proyecto Xcode mínimo, `docs/ios.md`)
- [x] Generar el proyecto Xcode con XcodeGen (`iosApp/project.yml`, `.xcodeproj` fuera del repo, build phase `embedAndSignAppleFrameworkForXcode`)
- [x] Comprobación local desde Windows: `./gradlew :shared:compileKotlinIosSimulatorArm64` y `compileKotlinIosArm64`
- [ ] CI en macOS (GitHub Actions `macos-latest`, repo público = minutos gratis): framework, tests iOS y `xcodebuild` al simulador con `CODE_SIGNING_ALLOWED=NO`
- [ ] Escáner con AVFoundation (se comprueba tipado en Windows y en CI; la cámara solo se prueba en iPhone)
- [x] Anotar lo no verificado en `docs/ios-pendiente.md`
- [ ] Firma y distribución por TestFlight con fastlane y clave API de App Store Connect (requiere cuenta de Apple Developer de pago; decidir si compensa)
- [ ] Prueba en iPhone real vía TestFlight, o en un Mac prestado como alternativa, y ajustes

## Fase 8: Pulido (~1 sem)

- [ ] Estados vacíos y mensajes de error
- [x] Rendimiento y revisión de datos dudosos
- [x] Estados vacíos y mensajes de error
- [ ] Rendimiento y revisión de datos dudosos
- [ ] Actualizar el README con el estado real
