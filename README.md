# EasyFoodApp

App multiplataforma (Kotlin Multiplatform + Compose Multiplatform) para **contar calorías con productos de supermercados españoles** (Mercadona/Hacendado, Carrefour, Dia, Lidl…).

## Qué busca

- Que registrar lo que comes sea rápido: **escaneas el código de barras** del producto y ves sus calorías y macros.
- Cubrir los supermercados españoles sin depender de scraping frágil: los datos salen de [Open Food Facts](https://world.openfoodfacts.org) (consulta por EAN, gratuita y abierta).
- **Sin cuentas ni servidor**: los datos viven en tu dispositivo (offline-first). Para moverlos entre dispositivos se exportan/importan (archivo JSON; más adelante QR / transferencia por red local).
- Android, iOS y escritorio con una sola base de código.

## Alcance del MVP

1. Buscar un producto por EAN y mostrar kcal/macros por 100 g. **(esqueleto hecho)**
2. Aviso cuando faltan datos o parecen erróneos (p. ej. >900 kcal/100 g).
3. Diario de comidas y objetivo diario, guardados en local (SQLDelight).
4. Escáner de códigos de barras (cámara) por plataforma.
5. Productos sin código (fruta, pan, carne): base genérica (BEDCA/USDA) y entrada manual.
6. Exportar/importar el perfil (JSON → QR / red local).

Fuera del MVP: cuentas y sincronización en la nube, foto de comida o etiqueta con IA, catálogo de Mercadona (precios).

## Estructura

| Módulo | Contenido |
|---|---|
| `shared` | Modelo, cliente de Open Food Facts (Ktor) y UI (Compose). Código común. |
| `desktopApp` | Aplicación de escritorio (JVM). |

Android e iOS se añadirán como módulos aparte (Android necesita el SDK instalado; iOS, un Mac).

## Ejecutar

Requiere JDK 17 o superior.

```
./gradlew :desktopApp:run     # abre la app de escritorio
./gradlew :shared:jvmTest     # tests
```

## Datos y privacidad

Los datos nutricionales son aportados por la comunidad de Open Food Facts y pueden ser incorrectos o estar incompletos. La app los marca para revisión cuando detecta valores dudosos.
