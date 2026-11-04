# Roadmap

Ritmo estimado: 5-10 h/semana, hitos de 1-2 semanas. Uso personal: sin CI ni publicación en tiendas.
Cada fase es un milestone de GitHub y cada tarea una issue.

Android, iOS y escritorio compilan desde la fase 1. Lo único que se retrasa en iOS es probarlo, porque necesita un Mac prestado (fase 7).

## Fase 0: Esqueleto (hecho)

- [x] Cliente de Open Food Facts por EAN, modelo `Product` y app de escritorio (PR #1)

## Fase 1: Datos sólidos (~2 sem)

Riesgo: calidad de datos de Open Food Facts.

- [ ] Ampliar `Product`: macros completos (grasas, saturadas, hidratos, azúcares, fibra, proteína, sal) y tamaño de porción
- [ ] Aviso de datos dudosos: >900 kcal/100 g, macros incoherentes con las kcal, campos ausentes
- [ ] Declarar los targets Android e iOS en `shared` y comprobar que compilan
- [ ] Integrar SQLDelight y esquema de productos
- [ ] Repositorio con caché local (red → base de datos)

## Fase 2: Diario (~2 sem)

- [x] Esquema del diario: entradas por día y por comida (desayuno, comida, cena, snacks)
- [x] Registrar cantidad en gramos o porciones
- [ ] Pantalla de diario con totales diarios de kcal y macros
- [ ] Navegación entre pantallas
- [ ] Editar y borrar entradas

## Fase 3: Android y escáner (~2 sem)

Riesgo: escáner distinto en cada plataforma.

- [x] Módulo `androidApp`
- [x] Escáner de códigos de barras con CameraX y ML Kit (`expect`/`actual`)
- [x] Permiso de cámara y manejo de errores
- [x] Entrada manual de EAN en escritorio
- [ ] (Opcional) Flujo Maestro de escaneo en emulador

## Fase 4: Alimentos sin código (~2-3 sem)

Riesgo: productos sin código de barras (fruta, pan, carne).

- [x] Alimentos propios con entrada manual (almacén; UI pendiente)
- [ ] Importar base genérica BEDCA/USDA embebida
- [x] Búsqueda por texto (FTS)
- [ ] Mostrar el origen del dato en la UI (Open Food Facts, genérico, propio)

## Fase 5: Objetivos (~1-2 sem)

- [x] Objetivo diario de kcal
- [x] Objetivo de macros (proteína, hidratos, grasa)
- [x] Calculadora de necesidades (TDEE) a partir de edad, peso, altura y meta
- [ ] Progreso frente al objetivo en el diario

## Fase 6: Exportar e importar (~2 sem)

Riesgo: mover datos entre dispositivos sin servidor ni cuentas.

- [x] Formato JSON versionado y exportación
- [x] Importación con fusión y detección de duplicados
- [ ] Transferencia por QR
- [ ] Transferencia por red local

## Fase 7: iOS (~1-2 sem + Mac)

- [ ] Módulo `iosApp`
- [ ] Escáner con AVFoundation
- [ ] Sesión de prueba en un Mac prestado y ajustes

## Fase 8: Pulido (~1 sem)

- [ ] Estados vacíos y mensajes de error
- [ ] Rendimiento y revisión de datos dudosos
- [ ] Actualizar el README con el estado real
