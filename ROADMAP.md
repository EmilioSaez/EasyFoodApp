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
- [x] Transferencia por QR (lógica en `shared`)
- [x] Transferencia por red local (protocolo en `shared`)
- [x] Pantalla «Datos»: exportar e importar JSON con vista previa de duplicados y fusión, QR (emitir con generador propio, recibir con la cámara en Android) y red local (código de emparejamiento, enviar y recibir). Acceso desde el diario y Objetivos. En iOS, guardar/abrir archivos queda como stub y la IP local no se detecta (`docs/ios.md`)
- [x] Verificación de punta a punta de la transferencia por red local y QR (tests de integración, bug de transferencias grandes corregido); lo no verificado en `docs/transferencia-verificacion.md`

## Fase 7: iOS sin Mac (~1-2 sem)

Riesgo: no hay Mac local. La ruta es la que sigue FitArchiveApp (sesión hermana): comprobar tipos de Kotlin/Native desde Windows, compilar y probar en CI de macOS y distribuir por TestFlight. Referencia: PRs #69, #70 y #71 de FitArchiveApp; no copiar workflows hasta que su CI pase en verde.

- [x] Módulo `iosApp` (proyecto Xcode mínimo, `docs/ios.md`)
- [x] Generar el proyecto Xcode con XcodeGen (`iosApp/project.yml`, `.xcodeproj` fuera del repo, build phase `embedAndSignAppleFrameworkForXcode`)
- [x] Comprobación local desde Windows: `./gradlew :shared:compileKotlinIosSimulatorArm64` y `compileKotlinIosArm64`
- [x] CI en macOS (GitHub Actions `macos-latest`, repo público = minutos gratis): framework, tests iOS y `xcodebuild` al simulador con `CODE_SIGNING_ALLOWED=NO`
- [x] Escáner con AVFoundation (se comprueba tipado en Windows y en CI; la cámara solo se prueba en iPhone)
- [x] Anotar lo no verificado en `docs/ios-pendiente.md`
- [ ] Firma y distribución por TestFlight con fastlane y clave API de App Store Connect (requiere cuenta de Apple Developer de pago; decidir si compensa)
- [ ] Prueba en iPhone real vía TestFlight, o en un Mac prestado como alternativa, y ajustes

## Fase 8: Pulido (~1 sem)

- [x] Estados vacíos y mensajes de error
- [x] Rendimiento y revisión de datos dudosos
- [x] Actualizar el README con el estado real

## Fase 9: Seguimiento

- [x] Objetivo fijo: interruptor en Objetivos que impide que la calculadora TDEE recalcule lo guardado (la calculadora solo sugiere); columna `fixed` con migración v3 → v4
- [x] Recetas: pestaña nueva donde se crea una receta con ingredientes de la BD y la cantidad de cada uno; los macros salen de la suma y la receta se añade al diario como un conjunto de alimentos (una entrada por ingrediente), incluidas en exportar e importar (migración v6 → v7)
- [x] Pasos diarios leídos del sensor (Health Connect en Android, HealthKit en iOS) que permitan reevaluar el gasto (TDEE): sugiere un nivel de actividad según la media de pasos (fórmula en `StepsActivity`) y, con «Objetivo fijo», solo sugiere; sin tabla nueva (no se guardan pasos)
- [x] Registro de peso con gráfica de evolución y progreso, incluido en exportar e importar (migración v4 → v5)
- [x] Aviso dentro de la app cuando toca registrar el peso (cada semana): tarjeta en el Diario derivada de los registros, sin tabla nueva
- [x] Peso objetivo elegido por el usuario; al actualizar el peso, mostrar lo lejos que se está de lograrlo y una estimación de cuándo se llegará (migración v5 → v6, incluido en exportar e importar)
- [ ] (Pendiente, sin decidir) IA para guiar el registro de comidas, opcional y con la clave de API propia del usuario (guardada solo en el dispositivo, nunca en el repo)

## Fase 10: Pulido del uso diario

Especificación en `docs/design.md` §2.1, §2.2, §2.9, §2.10 y §2.11. Cada punto es un PR.

- [x] Pestaña Alimentos sin filtros: sin selector de comida (Desayuno/Comida/Cena/Snacks) ni fichas de origen (Todos/Genérico/Open Food Facts/Propios); accesos rápidos «Recientes · Escáner · Alimento propio» (antes «Escanear · Código EAN · Alimento propio») y lista de recientes derivada del diario
- [x] Gráfica de peso con un punto por cada registro
- [x] Diario con cabecera fija arriba que siempre muestra el objetivo y cómo se va (consumo, restantes y macros)
- [x] Comidas al día: pantalla de primera vez que pregunta cuántas comidas se hacen (3 a 6), secciones del Diario según el preset (Desayuno, Almuerzo, Comida, Merienda, Cena, Recena) y alimentos añadidos a una comida concreta; ajuste en Objetivos; migración v7 a v8, incluido en exportar e importar

## Fase 11: Tema y Diario nuevo

Especificación en `docs/design.md` §1.7, §2.6 y §2.12. Cada punto es un PR; el Diario va después del tema.

- [ ] Ritmo de perder/ganar sin mínimo de 0 kg/semana: el slider empieza en 0,25 kg/sem (`GoalsPlanner.PACES` sin el 0,0); «Mantener» sigue siendo la forma de no perder ni ganar
- [x] Tema de ApexApp: paleta oscura y clara, tipografías Sora e Inter, formas de 4 dp, espaciado y barra de navegación estándar (§1.7)
- [x] Ritmo de perder/ganar sin mínimo de 0 kg/semana: el slider empieza en 0,25 kg/sem (`GoalsPlanner.PACES` sin el 0,0); «Mantener» sigue siendo la forma de no perder ni ganar
- [ ] Tema de ApexApp: paleta oscura y clara, tipografías Sora e Inter, formas de 4 dp, espaciado y barra de navegación estándar (§1.7)
- [x] Diario rediseñado (las tarjetas de comida salen siempre, también con el día vacío): franja semanal con los días coloreados según se hayan completado (objetivo ±10 %), racha de días completados, tarjeta de objetivo con el margen marcado y tarjetas de comida con «+», con cabecera colapsable (§2.12)
- [ ] (Pendiente, sin decidir) «Coach» con IA y clave de API propia del usuario; se une al punto de IA de la Fase 9

## Fase 12: Atajos y base en español

Cada punto es un PR.

- [x] Copiar una comida o un día entero a otra fecha o comida desde el Diario («Copiar» en cada tarjeta y «Copiar día»), sin tabla nueva (`docs/design.md` §2.13)
