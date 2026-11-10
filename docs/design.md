# EasyFood: especificación de diseño (Material 3 / Compose Multiplatform)

Lienzo con las pantallas: https://claude.ai/artifact/8K3RKHCUXz6peLbMoUVom5
Mockups a 390 dp de ancho (teléfono). En escritorio se usan las mismas pantallas en una columna de 600 dp como máximo, centrada.

## 1. Tokens

### 1.1 Color: tema rojo y negro (oscuro por defecto)
> **Fase 11:** el tema pasa a ser el de ApexApp (§1.7). Si hay diferencias entre esta sección y §1.7, manda §1.7.
El tema principal es oscuro: fondos casi negros y el rojo como acento. Se pasa a `darkColorScheme(...)`. El esquema claro es opcional; si se quiere, sale de la misma semilla roja (#C00010).

| Rol M3 | Valor |
|---|---|
| primary | #FF5449 |
| onPrimary | #1A0000 (los botones rojos llevan texto casi negro) |
| primaryContainer | #8C0009 |
| onPrimaryContainer | #FFDAD5 |
| secondary | #E7BDB7 |
| secondaryContainer | #4A1C19 (chips seleccionados, indicador de NavigationBar) |
| onSecondaryContainer | #FFDAD5 |
| tertiaryContainer | #333333 |
| onTertiaryContainer | #F2F2F2 |
| error | #FFB4AB |
| errorContainer | #690005 |
| onErrorContainer | #FFDAD6 |
| surface / background | #0E0E0E |
| surfaceContainerLowest | #141414 |
| surfaceContainerLow | #1A1A1A |
| surfaceContainer | #1F1F1F |
| surfaceContainerHigh | #2A2A2A |
| surfaceContainerHighest | #353535 |
| onSurface | #F2F2F2 |
| onSurfaceVariant | #BDBDBD |
| outline | #8A8A8A |
| outlineVariant | #3D3D3D |
| scrim (fondo del escáner) | #000000 |
| inverseSurface | #F2F2F2 |
| inverseOnSurface | #1A1A1A |
| inversePrimary | #C00010 |

### 1.2 Colores extendidos (no existen en M3)
Van en un `data class EasyFoodColors` y se exponen con `staticCompositionLocalOf` (`LocalEasyFoodColors`).

| Token | Valor | Uso |
|---|---|---|
| macroProtein | #FF5449 | barra, punto y segmento de proteína |
| macroCarbs | #FFB067 | hidratos |
| macroFat | #BDBDBD | grasa |
| warningContainer | #4D3800 | aviso de datos dudosos, chip «Dudoso» |
| onWarningContainer | #FFDF9E | texto e icono sobre warningContainer |
| warning | #FFC94D | valor «Sin dato» en la tabla |

El aviso se queda en ámbar para no confundirlo con el rojo principal ni con los errores.

Origen del dato (avatar y chip):
- Genérico → secondaryContainer / onSecondaryContainer (rojo oscuro), inicial «G»
- Open Food Facts → tertiaryContainer / onTertiaryContainer (gris), «OFF», icono de globo
- Propio → warningContainer / onWarningContainer (ámbar), «P», icono de lápiz

Cada macro va siempre con su texto, nunca se distingue solo por el color.

### 1.3 Tipografía
`Typography()` por defecto de M3 con Roboto (en escritorio e iOS, empaqueta Roboto con `compose.components.resources`). Para EAN y números tabulares, Roboto Mono Medium.

| Estilo | Tamaño/interlínea | Peso | Dónde |
|---|---|---|---|
| displaySmall | 36/44 | 400 | total de kcal en la ficha |
| displayMedium | 45/52 | 400 | objetivo de kcal en Objetivos |
| headlineSmall | 24/32 | 400 | nombre del producto, kcal restantes en el anillo |
| titleLarge | 22/28 | 400 | TopAppBar, títulos de estados vacíos y de hojas |
| titleMedium | 16/24 | 500 | cabecera de comida, macros totales |
| titleSmall | 14/20 | 500 | encabezados de sección (color primary) |
| bodyLarge | 16/24 | 400 | nombre del alimento en listas, campos |
| bodyMedium | 14/20 | 400 | textos secundarios y avisos |
| bodySmall | 12/16 | 400 | cantidad · origen, texto de ayuda |
| labelLarge | 14/20 | 500 | botones y chips |
| labelMedium | 12/16 | 500 | NavigationBar, etiquetas de barras de macros |
| labelSmall | 11/16 | 500 | «kcal/100 g», etiquetas de estado |

### 1.4 Espaciado (`object Spacing`)
`xxs 2` · `xs 4` · `s 8` · `m 12` · `l 16` · `xl 20` · `xxl 24` · `xxxl 32` (dp)
- Margen lateral de pantalla: 16
- Separación entre tarjetas: 12; entre secciones de formulario: 20
- Padding interno de tarjeta: 16
- Fila de lista: alto mínimo 56 (una línea + soporte) o 72 (con avatar); padding horizontal 16
- Hueco inferior para FAB/barra inferior: 96–104
- Objetivos táctiles ≥ 48 dp

### 1.5 Formas (`Shapes`)
`extraSmall 4` (campos outlined, snackbar) · `small 8` (chips) · `medium 12` (aviso, accesos rápidos) · `large 16` (tarjetas, FAB) · `extraLarge 28` (bottom sheet, barra de búsqueda) · botones y segmentados: `CircleShape`/pill.

### 1.6 Elevación
Tarjetas planas: el contraste viene del color del contenedor, sin sombra. Solo llevan sombra el FAB (nivel 3) y el snackbar.

### 1.7 Tema de ApexApp (Fase 11)
Se copia la configuración de tema de ApexApp (`ApexApp/app/src/main/java/app/luxion/apexapp/ui/theme/`: `Color.kt`, `Theme.kt`, `Type.kt`, `Shape.kt`, `Spacing.kt`). Concepto: rojo sobre negro, un solo acento; `tertiary` = éxito, `error` = ámbar (aviso/fallo). Sin color dinámico (`dynamicColor = false`); modo por defecto: seguir al sistema.

**Oscuro:** primary `FF4D4F`, onPrimary `3A0004`, primaryContainer `8C1218`, onPrimaryContainer `FFDAD8` · secondary `BDBDBD`, onSecondary `1A1A1A`, secondaryContainer `2A2A2A`, onSecondaryContainer `EDEDED` · tertiary `5BD68A`, onTertiary `00391B`, tertiaryContainer `14522E`, onTertiaryContainer `B6F2CB` · error `FFB454`, onError `4A2A00`, errorContainer `5C3A0E`, onErrorContainer `FFE0B8` · background `0B0B0B`, onBackground `EDEDED`, surface `0B0B0B`, onSurface `EDEDED`, surfaceVariant `2A2A2A`, onSurfaceVariant `A8A8A8` · surfaceContainerLowest `000000`, Low `141414`, (base) `1A1A1A`, High `222222`, Highest `2A2A2A` · outline `7A7A7A`, outlineVariant `383838` · inverseSurface `EDEDED`, inverseOnSurface `0B0B0B`, inversePrimary `C4161C`.

**Claro:** primary `C4161C`, onPrimary `FFFFFF`, primaryContainer `FFDAD8`, onPrimaryContainer `3A0004` · secondary `4A4A4A`, onSecondary `FFFFFF`, secondaryContainer `E0DCDC`, onSecondaryContainer `0B0B0B` · tertiary `1B7A43`, onTertiary `FFFFFF`, tertiaryContainer `B6F2CB`, onTertiaryContainer `00210F` · error `9A5B00`, onError `FFFFFF`, errorContainer `FFDDB0`, onErrorContainer `2E1A00` · background `F7F5F5`, onBackground `0B0B0B`, surface `F7F5F5`, onSurface `0B0B0B`, surfaceVariant `E0DCDC`, onSurfaceVariant `4A4A4A` · surfaceContainerLowest `FFFFFF`, Low `F1EEEE`, (base) `EAE6E6`, High `E4E0E0`, Highest `E0DCDC` · outline `6E6A6A`, outlineVariant `CAC5C5` · inverseSurface `2A2A2A`, inverseOnSurface `F1EEEE`, inversePrimary `FF4D4F`.

**Tipografía:** Sora (hora y títulos) e Inter (resto), fuentes variables (`sora.ttf`, `inter.ttf`) con pesos Normal/Medium/SemiBold; cifras tabulares (`tnum`) en display y headlineLarge. displayLarge Sora Normal 88/92 ls −3 · displayMedium Sora Normal 56/60 ls −2 · headlineLarge Sora SemiBold 32/36 ls −1 · titleLarge Sora Medium 24/30 ls −0,5 · titleMedium Inter SemiBold 16/22 · bodyLarge Inter Normal 16/24 · bodyMedium Inter Normal 14/20 · labelLarge Inter Medium 14/20 · labelMedium Inter SemiBold 12/16 ls 1,2 (rótulos de sección en mayúsculas) · labelSmall Inter Medium 12/16. El resto de estilos M3, por defecto.

**Formas:** un único radio de 4 dp en extraSmall a extraLarge (esquinas casi rectas); los puntos de estado usan `CircleShape`. **Espaciado:** xs 4, sm 8, md 12, lg 16, xl 24, xxl 32, xxxl 48 y margen horizontal de pantalla 20 dp (se mapea a `object Spacing` existente sin cambiar sus nombres, o se amplía).

**Barra de navegación:** `NavigationBar` M3 estándar (no píldora flotante) con `containerColor = surfaceContainerLow`, indicador transparente, ítem seleccionado en primary y no seleccionado en onSurfaceVariant.

**Colores extra de EasyFood** (macros, aviso, dato dudoso; `easyFoodColors`): se conservan y se adaptan a la paleta nueva sin cambiar sus nombres (ApexApp no define macros). Todo texto cumple contraste AA (4,5:1).

**Implementación (Fase 11):** hoy EasyFood ya no fuerza oscuro: `EasyFoodTheme(darkTheme = isSystemInDarkTheme())` elige la paleta según el sistema en las tres plataformas (antes siempre oscuro; se cambia porque la especificación pide seguir al sistema y la paleta clara existe en el origen). En Android, `Theme.EasyFood` (`values` y `values-night`) da a la ventana el fondo y las barras del sistema de cada paleta. Los estilos tipográficos que ApexApp no define (displaySmall, headlineMedium/Small, titleSmall, bodySmall) conservan la escala M3 pero con Inter, para no mezclar con la fuente del sistema. `Spacing` conserva `xxs`, `s`, `m`, `l` (alias de `sm`, `md`, `lg`), pasa `xl`/`xxl`/`xxxl` a 24/32/48 y añade `screen` = 20. `easyFoodColors` oscuro: proteína `FF5C5E`, hidratos `6FB1FF`, grasa `BDBDBD`, aviso `FFB454` sobre `5C3A0E`/`FFE0B8`; claro: proteína `B41319`, hidratos `1F5FA8`, grasa `5F5F5F`, aviso `8A5000` sobre `FFDDB0`/`2E1A00` (contraste ≥ 4,5:1 sobre todos los `surfaceContainer*`). Licencia de las fuentes: `docs/licenses/fonts-OFL.md`.

**Hecho por:** copia de valores, no dependencia entre proyectos. Las fuentes se copian a `shared/src/commonMain/composeResources/font/` con su licencia (OFL) si la trae el origen.

## 2. Pantallas

### 2.1 Diario del día (`DiaryScreen`)
> **Sustituido por §2.12 (Fase 11)**: la estructura de abajo es la de la Fase 10; la vigente es la del Diario rediseñado.
> **Cambios de la Fase 10** (ver §2.9 y §2.10): el resumen del objetivo pasa a una cabecera fija que no se desplaza, y las secciones de comida dependen del número de comidas al día que elige el usuario.
```
Scaffold
├─ CenterAlignedTopAppBar: IconButton(‹) · título «Hoy» + subtítulo fecha (bodySmall) · IconButton(›)
├─ LazyColumn (padding 16, spacedBy 12)
│  ├─ DaySummaryCard  (surfaceContainerLow, large) — pasa a la cabecera fija (§2.10)
│  │  ├─ CalorieRing 120 dp: track surfaceContainerHighest, progreso primary, trazo 10 dp, extremo redondo
│  │  │   centro: restantes (headlineSmall) + «kcal restantes» (bodySmall)
│  │  │   si se pasa del objetivo: arco en error y texto «X kcal de más»
│  │  └─ Column: «1.215 / 2.100 kcal» + 3 × MacroProgressRow (label, «68 / 130 g», LinearProgressIndicator 6 dp con color de macro)
│  └─ MealSection × N (según «comidas al día», §2.9)  OutlinedCard (surfaceContainerLowest, outlineVariant)
│     ├─ cabecera: nombre (titleMedium) + «386 kcal · P 17 · H 62 · G 8» (bodySmall) + IconButton(+) → Añadir con la comida preseleccionada
│     ├─ vacía: texto bodyMedium onSurfaceVariant
│     └─ DiaryEntryRow × n (ListItem): nombre (1 línea, ellipsis) · «cantidad · origen» · SourceBadge/DoubtfulChip · kcal (labelLarge) a la derecha
│        toque → Ficha en modo edición; deslizar o pulsación larga → borrar (con snackbar «Deshacer»)
├─ ExtendedFloatingActionButton «Añadir alimento» (primaryContainer), encima de la barra
└─ NavigationBar: Diario · Alimentos · Objetivos (indicador secondaryContainer)
```
Estado: `DiaryUiState(date, goalKcal, consumedKcal, macros: List<MacroProgress>, meals: List<MealUi>)`.

### 2.2 Añadir alimento / pestaña Alimentos (`AddFoodScreen`)
> **Fase 10:** sin selector de comida ni filtros de origen; muestra todos los alimentos. Los accesos rápidos pasan a «Recientes · Escáner · Alimento propio».
> **Fase 12:** se añade el acceso rápido «Favoritos» junto a «Recientes» y una estrella de favorito en cada fila (ver «Favoritos» más abajo). Se añade también «Plantillas» (§2.17).
```
TopAppBar: ← · «Alimentos»   (sin selector de comida)
SearchBar (inline, extraLarge, surfaceContainerHigh): icono lupa · texto · IconButton borrar
Fila de 4 accesos rápidos (gap 8, alto 72, medium; el activo en primaryContainer, el resto en surfaceContainer):
   Recientes · Favoritos · Plantillas · Escáner · Alimento propio
Debajo, botón ancho «Entrada rápida · solo kcal y macros» (OutlinedButton): apunta kcal y macros sin crear un alimento (§2.16)
Sin texto en la búsqueda: cabecera «Últimos alimentos · 24» y la lista «Recientes» (los últimos alimentos distintos del diario, del más reciente al más antiguo, FoodResultRow); con «Favoritos» activo, cabecera «Favoritos · 5» y su lista
Con texto: cabecera «Resultados · 24» (titleSmall primary) y LazyColumn de FoodResultRow (ListItem 72):
   SourceAvatar 40 · nombre / «marca · tamaño · EAN» · «255» + «kcal/100 g» · estrella ☆/★ (IconButton 48)
```
- **Sin filtros:** se eliminan las fichas «Todos · Genérico · Open Food Facts · Propios» y el filtro por origen. Los resultados mezclan todos los orígenes (genérico, Open Food Facts y propios); el origen sigue visible en cada fila con `SourceAvatar`/`SourceChip`.
- **Sin comida preseleccionada:** la pestaña Alimentos ya no pide la comida arriba. Al abrir la ficha de un alimento desde la pestaña, la comida se elige en la propia ficha (por defecto la comida que toca por la hora, §2.9). Si se llega desde el «+» de una sección del Diario, la comida viene preseleccionada y no se pregunta de nuevo.
- **«Recientes»:** la lista de recientes se ve por defecto cuando no hay texto; el botón sirve para volver a ella tras buscar (borra el texto). No necesita tabla nueva ni migración: la consulta `selectRecent` toma la última entrada de cada alimento distinto de `DiaryEntry` (clave: EAN o, sin él, el nombre; hasta 30). Un alimento propio no guarda su id en el diario, así que se enlaza por nombre con los alimentos propios; si ya no existe se omite. Sin recientes: estado vacío «Aún no hay recientes».
- **Favoritos (Fase 12):** un alimento se marca con la estrella ☆ (pasa a ★) en las filas de resultados y recientes de esta pestaña y en la barra superior de su ficha (§2.4); tocarla de nuevo lo desmarca. No hay estrella en el selector de ingredientes de las recetas ni en la ficha de una entrada del diario de un alimento propio (no guarda su id). El acceso rápido «Favoritos» muestra la lista por nombre (mezcla genéricos, Open Food Facts y propios) cuando no hay texto; «Recientes» vuelve a los recientes y ambos borran el texto de búsqueda. Sin favoritos: estado vacío «Aún no tienes favoritos». Un favorito cuyo producto ya no está en la caché local, o cuyo alimento propio se borró, no se muestra (no hay ficha a la que ir) pero sigue guardado hasta que se desmarque.
  - **Datos:** tabla `FavoriteFoodEntity(key PK, created_at)` con la clave de navegación de la ficha (EAN, `generic:<id>` o `custom:<id>`), sin clave foránea a `ProductEntity` (es una caché purgable). Migración v9 → v10 (`9.sqm`), idempotente.
  - **Exportar e importar:** colección opcional `favorites` del JSON, **sin subir `schemaVersion`** (añadir una colección no lo cambia, ver `ExportFormat`): los archivos antiguos no la traen y se leen como vacía. Un producto viaja por su `barcode`; un alimento propio, como su id es local de cada dispositivo, por `customName` + `customBrand`. Fusión: identidad = código, o nombre + marca sin distinguir mayúsculas; los que ya existen cuentan como duplicados y nunca se desmarca nada local. Un favorito propio solo se admite si el alimento existe en local o llega en el mismo archivo; los inválidos se omiten y se listan en el informe («Favoritos» en la vista previa).
- **«Escáner»:** sustituye a «Código EAN». Abre `ScannerScreen`, que ya incluye la entrada manual de EAN (§2.3). En escritorio no hay cámara y el botón abre directamente la entrada manual.
- **Búsqueda mejorada:** el mismo FTS5 (`ProductFts`, sin cambios de esquema ni migración) con una expresión más lista, en `SearchText` y `ProductSearchQuery`:
  - **Acentos y mayúsculas:** ya los cubría el tokenizador (`unicode61 remove_diacritics 2`); el texto del usuario se normaliza igual (incluidas ñ y ç).
  - **Plurales:** cada término se busca por su raíz como prefijo (`manzanas` → `manzana`*, `limones` → `limon`*, `lápices` → `lapiz`*).
  - **Erratas:** si ningún término indexado empieza como lo escrito, se buscan también los más parecidos (Damerau-Levenshtein ≤ 1 hasta 5 letras, ≤ 2 desde 6; palabras de menos de 4 letras no se corrigen), con la misma letra inicial. El vocabulario (nombre y marca) se calcula en memoria y se rehace solo cuando cambia la caché de productos.
  - **Marca y supermercado:** se buscan en nombre y marca. «mercadona», «hacendado», «carrefour», «dia», «lidl», «eroski», «alcampo», «aldi» y «consum» se buscan en la **marca**, ampliada con sus marcas blancas (Mercadona → Hacendado, Deliplus, Bosque Verde, Compy; Lidl → Milbona, Deluxe; Alcampo → Auchan; Aldi → Milsani). Open Food Facts no guarda en qué tienda se vende, solo la marca, de ahí la tabla de alias (`SearchText.supermarkets`).
  - **Uso frecuente:** a igual relevancia (bm25), suben los alimentos que más veces están en el diario (por EAN o `generic:<id>`; bonus de hasta 8 puntos con 10 usos o más). Se deriva de `DiaryEntryEntity`, sin tabla nueva.
  - Los alimentos propios (que no están en el FTS) se filtran en memoria con las mismas reglas de plurales, erratas y supermercados.
- **Filtro «Solo mis productos»:** la Fase 10 quitó los chips de origen (Todos · Genérico · Open Food Facts · Propios) porque obligaban a elegir *dónde* buscar. Este filtro no es de origen sino de pertenencia, y se encaja sin recargar la pestaña: **un único `FilterChip` a la derecha de la fila «Resultados · N»**, que ya existía; no hay fila nueva, no aparece sin texto (los accesos rápidos y el estado vacío no cambian) y por defecto está apagado, así que el comportamiento de la Fase 10 (todo mezclado) se mantiene. Activo, deja los productos consultados (cualquier origen salvo la base genérica), los alimentos propios y los favoritos (también genéricos). Si no hay resultados, la fila sigue visible con el mensaje «No hay nada así entre tus productos» para poder quitarlo. No se guarda entre sesiones.
- Búsqueda: debounce de 300 ms y FTS local; Open Food Facts solo si hay red, mezclado detrás de los resultados locales.

### 2.3 Escáner + EAN manual (`ScannerScreen`)
> Se accede desde el botón «Escáner» (antes «Código EAN»); la entrada manual del EAN sigue dentro de esta pantalla.
```
Box fondo scrim #000000
├─ vista previa de cámara (expect/actual: CameraX+ML Kit / AVFoundation)
├─ barra superior transparente: IconButton cerrar · «Escanear código» · IconButton linterna
├─ marco 300×190, radio 16, borde 2 dp primary + línea guía horizontal
├─ ayuda bodyMedium centrada
└─ hoja fija inferior (surfaceContainerLow, extraLarge arriba, asa 32×4)
   «¿No se lee el código?» (titleLarge) · ayuda
   OutlinedTextField «Código EAN» (Roboto Mono, KeyboardType.Number, supportingText «8 o 13 dígitos · 13/13»)
   Button «Buscar producto» (ancho completo) – activo con 8 o 13 dígitos y dígito de control válido
```
Al leer un código: vibración corta y navegación directa a la Ficha. Si no está → estado «Código no encontrado».

### 2.4 Ficha de producto (`ProductDetailScreen`)
```
TopAppBar: ← · «Producto» · IconButton estrella de favorito ☆/★ · IconButton editar (copiar como propio)
LazyColumn (16, spacedBy 16)
├─ cabecera: nombre (headlineSmall) · «marca · tamaño · EAN» (bodyMedium, EAN en mono) · SourceChip (AssistChip con icono)
├─ DoubtfulDataBanner (si aplica): warningContainer, medium, icono ⚠, título «Revisa estos datos» + motivos + TextButton «Corregir y guardar como propio»
│    motivos: >900 kcal/100 g · kcal de macros (4P+4H+9G) con desviación >15 % · campos ausentes · saturadas > grasas · azúcares > hidratos
├─ QuantityCard (surfaceContainerLow, large)
│  ├─ SingleChoiceSegmentedButtonRow: Gramos | Porciones (Porciones desactivado si no hay tamaño de porción)
│  ├─ fila: OutlinedIconButton(−) · OutlinedTextField número centrado · OutlinedIconButton(+)  (paso 1 porción o 10 g)
│  ├─ «1 porción = 30 g (según el envase)»
│  ├─ Divider · «Total · 30 g» + kcal (displaySmall)
│  └─ grid 3 col: punto de color + Proteína / Hidratos / Grasa (titleMedium)
├─ NutritionTable «Por 100 g»: Energía, Grasas, ↳ saturadas, Hidratos, ↳ azúcares, Fibra, Proteína, Sal
│    filas hijas con sangría de 16 y onSurfaceVariant; valor ausente = «Sin dato» en warning
├─ Tarjeta de refresco (solo productos de Open Food Facts, no en edición de una entrada): «Datos de Open Food Facts actualizado hace 3 días» · último cambio relevante («Cambió hace 2 días: Energía 250 → 230 kcal. Tus entradas anteriores del diario conservan los valores…») · mensaje del último refresco · OutlinedButton «Actualizar datos». Ver `docs/refrescar-productos.md`.
├─ Tarjeta de corrección (productos OFF y genéricos; no en alimentos propios ni en la ficha de una entrada del diario): «Corregir valores» / «Editar corrección», «Volver al original» (si está corregido) y «Corregir en Open Food Facts» (solo EAN)
└─ «Fuente: Open Food Facts · actualizado hace 3 días» (bodySmall)
BottomBar (surfaceContainer, padding 16): Button «Añadir a Snacks» (48 dp, icono +). En modo edición: «Guardar cambios» + IconButton borrar.
```

**Corrección y reporte de productos (Fase 13).** El aviso de datos dudosos solo avisaba; ahora el usuario puede corregir los valores y avisar a Open Food Facts.
- **Corrección local.** «Corregir valores» abre un diálogo con energía, proteína, hidratos, grasas, saturadas, azúcares, fibra y sal por 100 g, precargado con los valores actuales. Energía y los tres macros principales son obligatorios; kcal ≤ ≈ 900 y ningún nutriente negativo ni > 100 g. Solo cambian los nutrientes: nombre, marca y porción siguen siendo los del producto. «Guardar como propio» (aviso de datos dudosos) sigue existiendo para crear un alimento aparte.
- **Marca en la UI.** Chip «Corregido por ti» junto al de origen, tarjeta «Corregido por ti» con los **valores originales** en una tabla y «Volver al original» (borra la corrección), y «· corregido por ti» en el detalle de la fila de resultados de búsqueda. Los avisos de datos dudosos se evalúan sobre los valores corregidos. Las entradas del diario ya registradas no cambian (guardan su copia); las nuevas y los ingredientes de recetas usan los valores corregidos.
- **Persistencia y refresco.** Tabla aparte `ProductCorrectionEntity(barcode PK, kcal, protein_g, carbs_g, sugars_g, fat_g, saturated_fat_g, fiber_g, salt_g, updated_at)` (`Product.sq`), sin clave foránea a `ProductEntity` (la corrección puede existir antes que el producto cacheado). Como es otra tabla, el refresco desde Open Food Facts, la reimportación o la resiembra de la base genérica **no la tocan**: `ProductLocalStore.find` y `applyCorrection` la superponen al leer (`Product.originalNutrients` guarda el valor de la fuente) y `ProductRepository` la aplica también al producto recién descargado. Revertir es borrar la fila. Migración v15 → v16 (`15.sqm`, solo `CREATE TABLE IF NOT EXISTS`: no toca datos existentes).
- **Reportar en Open Food Facts.** «Corregir en Open Food Facts» (solo productos con EAN, no genéricos) abre en el navegador `https://world.openfoodfacts.org/product/<EAN>` con el gestor de enlaces del sistema, donde se puede editar con una cuenta de OFF. La app no envía nada por sí misma: el enlace solo lleva el código del producto, ningún dato del usuario ni sus correcciones.
- **Exportar e importar.** Colección opcional `productCorrections` (`barcode`, `nutrients`, `updatedAtMillis`), **sin subir `schemaVersion`**: los archivos antiguos no la traen y se leen como vacía. `products` sigue llevando los valores originales de la fuente. Fusión como los pesos: identidad = código; no existe en local, se añade (aunque el producto aún no esté cacheado); mismos valores, duplicado; valores distintos, gana el `updatedAtMillis` más reciente (empate: el local). Las no válidas se omiten y se listan; la vista previa muestra «Correcciones de productos».

### 2.5 Alimento propio (`CustomFoodScreen`)
TopAppBar con ✕, título y TextButton «Guardar». OutlinedTextField «Nombre»; fila con «Porción» (texto) y «Peso porción» (g). «Valores por 100 g»: grid de 2 columnas con 8 campos numéricos y el sufijo de unidad. La validación aparece en línea con `isError` y supportingText (p. ej. «Mayor que las grasas»). Si se abre desde un EAN no encontrado, el código va prerrelleno y no se puede editar. Se guarda con origen = Propio.

### 2.6 Objetivos y TDEE (`GoalsScreen`)
```
TopAppBar «Objetivos»
├─ ResultCard (primaryContainer, large): «Objetivo diario» · 2.100 kcal (displayMedium) · grid 2 col: Metabolismo basal / Gasto total (TDEE) · fórmula (bodySmall)
├─ «Tus datos»: SegmentedButton Hombre | Mujer · grid 3 col de OutlinedTextField: Edad (años), Peso (kg), Altura (cm)
├─ «Actividad física»: 5 filas RadioButton con nombre, descripción y factor
│    Sedentaria ×1,2 · Ligera ×1,375 · Moderada ×1,55 · Alta ×1,725 · Muy alta ×1,9
├─ «Meta»: SegmentedButton Perder | Mantener | Ganar · Slider con ritmo (0,25 / 0,5 / 0,75 / 1 kg/sem; 1 kg/sem ≈ 1.100 kcal/día). **Fase 11:** sin posición de 0 kg/sem: con Perder o Ganar siempre hay un ritmo real (el mínimo es 0,25 kg/sem); «no perder ni ganar» se elige con «Mantener», que deshabilita el slider
└─ MacroSplitCard (surfaceContainerLow): barra apilada 12 dp (P/H/G) + 3 filas: punto, nombre, %, gramos · TextButton «Editar» (sliders o gramos/kg)
BottomBar: Button «Guardar objetivos»
```
Fórmula (Mifflin-St Jeor): TMB = 10·kg + 6,25·cm − 5·edad + 5 (hombre) / −161 (mujer). TDEE = TMB × factor. Objetivo = TDEE ± ajuste de la meta. No debe bajar de TMB×1,0 sin avisar.
Por defecto: proteína 1,6–2,0 g/kg, grasa 25–30 % y el resto en hidratos. Gramos: P y H a 4 kcal/g, G a 9 kcal/g.
El usuario puede escribir el objetivo a mano; en ese caso la tarjeta lo muestra como «Personalizado».

### 2.7 Estados vacíos y errores (`EmptyState`, composable reutilizable)
Estructura: icono 32 dentro de un círculo de 72 (color según el tono) · titleLarge · bodyMedium onSurfaceVariant (máx. 300 dp) · Button principal · OutlinedButton opcional.

| Caso | Tono | Título | Acciones |
|---|---|---|---|
| ~~Diario sin entradas~~ | — | Ya no es una pantalla vacía (§2.12): un día sin entradas muestra la cabecera y todas las tarjetas de comida, con una línea discreta «Nada apuntado hoy» | — |
| Búsqueda sin resultados | neutro | Sin resultados para «…» | Crear alimento propio · Escanear código |
| EAN no encontrado | aviso (warningContainer) | Código no encontrado | Crear con este código · Escanear otro |
| Permiso de cámara denegado | error (errorContainer) | Sin acceso a la cámara | Abrir ajustes · Escribir código |
| Sin conexión | aviso | Sin conexión | Buscar sin conexión (solo local) |
| Sin recetas | neutro | Aún no tienes recetas | Crear receta |
| Error de red puntual | — | Snackbar «Open Food Facts no responde.» + acción «Reintentar» | mantiene el contenido ya cargado |

Para las cargas, usa `LinearProgressIndicator` bajo la barra de búsqueda o filas placeholder (shimmer suave con surfaceContainerHigh), no spinners a pantalla completa.

### 2.8 Recetas (`RecipesScreen`, `RecipeDetailScreen`, `RecipeEditScreen`)
Una receta es un conjunto de alimentos de la BD (Open Food Facts, genéricos USDA y propios), cada uno con su cantidad en gramos (o ml), más un nombre y unas raciones opcionales. Los macros no se escriben: salen de la suma de los ingredientes (`RecipeCalculator`). Si a algún ingrediente le falta un dato, ese macro de la receta aparece como «Sin dato» (no se suma «lo que hay») y un aviso nombra al ingrediente. Cada ingrediente guarda una copia del nombre y de los valores por 100 g, como el diario: la receta no cambia si la caché de productos se purga o se edita el alimento propio.
```
Pestaña «Recetas» (barra inferior: Diario · Alimentos · Recetas · Mi peso · Objetivos)
TopAppBar «Recetas»
├─ Button «+ Nueva receta»
└─ ListItem por receta: nombre / «3 ingredientes · 4 raciones» · kcal por ración (o total si no hay raciones)
Sin recetas → EmptyState «Aún no tienes recetas» · Crear receta

Detalle (TopAppBar con ←, título «Receta» y ✎ editar)
├─ nombre (headlineSmall) · «N ingredientes · X raciones · peso total»
├─ aviso (warningContainer) si algún ingrediente no tiene todos los datos
├─ Card (surfaceContainerLow): «Por ración · 1 de N» (solo con raciones) y «Receta entera»; cada bloque = kcal grandes + 3 MacroCell (proteína/hidratos/grasa)
├─ «Ingredientes · N»: ListItem nombre / gramos · kcal
└─ BottomBar: chips de comida (por defecto Comida) · campo «Raciones» (solo con raciones; 1 por defecto) · Button «Añadir a Comida»

Crear/editar (TopAppBar con ←, «Nueva receta»/«Editar receta» y TextButton «Borrar» al editar)
├─ OutlinedTextField «Nombre» · NumberField «Raciones (opcional)» (entero de 1 a 100)
├─ «Ingredientes · N»: ListItem nombre / «200 g · 500 kcal» · ✕ quitar; tocar la fila abre el cuadro de cantidad
├─ OutlinedButton «+ Añadir ingrediente» → pantalla de búsqueda (mismo `FoodSearch` que «Añadir»: búsqueda, filtros por origen y resultados) → AlertDialog «Cantidad» en g (una porción si el alimento la define, si no 100 g) → vuelve al formulario
├─ Card «Suma de los ingredientes»: «386 kcal · P 17 · H 62 · G 8»
└─ BottomBar: Button «Guardar receta» / «Guardar cambios»
```
Validación en línea tras intentar guardar: nombre obligatorio y al menos un ingrediente. «Atrás» del sistema dentro del buscador vuelve al formulario sin perder lo escrito. Borrar pide confirmación y no afecta a las entradas del diario ya añadidas.

**Añadir al diario:** una receta entra en el diario como un conjunto de alimentos, **una entrada por ingrediente**, con los gramos del ingrediente × (raciones comidas ÷ raciones de la receta); sin raciones se añade la receta entera. Es lo más simple porque reutiliza el diario tal cual (cada entrada se edita o borra por separado, cuenta para los macros del día y se exporta sin cambios) y no hace falta un tipo de entrada nuevo. El diario no guarda de qué receta viene cada entrada.

**Exportar/importar:** las recetas viajan en el campo opcional `recipes` del JSON (los archivos anteriores no lo traen y se leen como vacío); política de duplicados en `DataImporter`.

### 2.9 Comidas del día (Fase 10)
Cuántas comidas hace el usuario al día y cómo se llaman.
- **Primera vez:** al abrir la app por primera vez (sin ajuste guardado) se muestra una pantalla `MealsSetupScreen` antes del Diario: «¿Cuántas comidas haces al día?» con opciones 3, 4, 5 y 6 (SegmentedButton o filas con RadioButton) y la lista de comidas que resulta de cada opción. Botón «Continuar» (no se puede saltar; hay una opción por defecto marcada: 4). Se puede cambiar después en Objetivos («Comidas al día»).
- **Presets** (en orden de aparición en el día):

| Comidas | Secciones |
|---|---|
| 3 | Desayuno · Comida · Cena |
| 4 | Desayuno · Comida · Merienda · Cena |
| 5 | Desayuno · Almuerzo · Comida · Merienda · Cena |
| 6 | Desayuno · Almuerzo · Comida · Merienda · Cena · Recena |

- **Modelo:** `MealType` se amplía con los tipos nuevos (Almuerzo = media mañana, Recena); los valores ya guardados (`BREAKFAST`, `LUNCH`, `DINNER`, `SNACK`) siguen siendo válidos y no se migran los datos: `SNACK` se muestra como «Merienda». Los ajustes se guardan en una tabla de ajustes nueva (migración 7.sqm, v7 a v8), con `meals_per_day` (3 a 6) y nulo si aún no se ha elegido (eso dispara la pantalla de primera vez).
- **Diario:** muestra solo las secciones del preset elegido. Si al reducir el número hay entradas en una comida que ya no existe, esa sección sigue apareciendo (con sus entradas) para no ocultar datos.
- **Añadir alimento:** cada alimento se añade a una comida concreta: desde el «+» de la sección (preseleccionada) o eligiéndola en la ficha del alimento (por defecto, la comida más cercana a la hora actual). El selector de la ficha lista solo las comidas activas.
- **Exportar/importar:** `meals_per_day` viaja en el JSON como campo opcional (compatible con archivos antiguos); importar un archivo sin él no cambia el ajuste.

### 2.10 Cabecera fija del Diario (Fase 10) — sustituida: ya no es fija
> Ya no se aplica: el resumen del objetivo se desplaza con la lista (§2.12). Se conserva como histórico.
- **Siempre visible:** bajo la barra de fecha, una cabecera que NO se desplaza con la lista (queda anclada arriba) con el objetivo y cómo se va: `CalorieRing` compacto o barra, «consumidas / objetivo kcal», «X kcal restantes» (o «X kcal de más» en error) y los tres macros (P/H/G) con su progreso.
- **Sin objetivo:** en lugar del progreso, la misma cabecera muestra «Sin objetivo diario» y el botón «Fijar objetivo» que abre Objetivos.
- **Sustituye** a `DaySummaryCard`, que deja de formar parte de la lista. La lista (comidas) se desplaza por debajo.
- El aviso «Toca pesarse» sigue siendo una tarjeta de la lista (no de la cabecera).
- Altura contenida (unos 120 dp) para no quitar espacio en móviles pequeños; en pantallas con poca altura, los macros pueden ir en una sola línea.

### 2.11 Gráfica de peso: un punto por registro (Fase 10)
En `WeightChart` (Mi peso) se dibuja un punto por cada registro, no solo el primero y el último. El último registro se destaca (punto relleno, mayor); los demás son puntos más pequeños. La línea los une. Accesibilidad: la descripción de la gráfica sigue resumiendo «N registros, de X a Y, último Z kg».

### 2.12 Diario rediseñado (Fase 11)
Mezcla de la captura de referencia (resumen grande arriba, semana con días y tarjetas de comida con «+») con el tema de EasyFood/ApexApp (§1.7): las formas y colores salen del tema, no de la captura (esquinas de 4 dp, rojo sobre negro). Sustituye a la estructura de §2.1 (conserva su lógica y §2.9/§2.10).
```
Scaffold
├─ Cabecera (la fila va anclada; la franja y la tarjeta se desplazan con la lista; ver «Cabecera»)
│  ├─ Fila: «Hoy ▾» (toca → selector de fecha) · derecha: 🔥 racha (icono de llama + nº de días) · ▥ Resumen (§2.16)
│  ├─ Franja semanal (7 días, lunes a domingo de la semana del día elegido): inicial (L M X J V S D) · número · punto de estado
│  └─ Tarjeta de objetivo
│       «kcal» · «consumidas / objetivo» (displayMedium, cifras tabulares)
│       arco/barra de progreso con DOS marcas: límite inferior y superior del margen de cumplimiento (objetivo ±10 %),
│          con sus kcal debajo (p. ej. 2.327 y 2.844 en la captura)
│       3 columnas de macros: «Proteínas · 0 / 141 g» + barra fina del color del macro (P/H/G)
├─ Lista que se desplaza: aviso «Toca pesarse» (si toca) · tarjeta de agua (§2.13) · tarjeta por comida (§2.9)
│     tarjeta de comida: título (titleLarge) · «🔥 386 kcal · 17 P | 62 H | 8 G» (bodySmall) · entradas (DiaryEntryRow) ·
│     botón ancho «+» (secondaryContainer) → Añadir alimento con la comida preseleccionada
└─ NavigationBar estándar (§1.7): Diario · Alimentos · Recetas · Mi peso · Objetivos
```
- **Días de la franja semanal.** Punto de estado bajo cada número, y el número con el mismo color:
  - **Completado** (verde, `tertiary`): hay comida registrada y las kcal del día quedan dentro del margen del objetivo (objetivo ±10 %).
  - **No completado** (ámbar, `error`): día pasado con comida registrada pero fuera de margen, o sin registro.
  - **Hoy** sin completar aún: anillo en `primary` (no cuenta como fallo hasta que acabe el día).
  - **Futuro**: punto neutro (`outlineVariant`).
  - **Sin objetivo definido:** no se puede evaluar; los días con registro salen neutros con punto relleno y los demás vacíos.
  - El día elegido se marca con un círculo (primaryContainer). Tocar un día lo selecciona; deslizar la franja cambia de semana.
- **Racha.** Número de días completados consecutivos que terminan hoy; si hoy aún no está completado, se cuenta hasta ayer y la racha no se rompe hasta que acaba el día. Se calcula del historial del diario y del objetivo vigente (el objetivo es el mismo todos los días, §2.10), sin tabla nueva. Con racha 0, la llama sale apagada.
- **Margen de cumplimiento:** constante única (±10 %) en la lógica (p. ej. `DayStatus.MARGIN`), usada por los colores de la franja, la racha y las marcas del arco.
- **Cabecera.** Solo la fila (‹ «Hoy ▾» › · racha · Datos) queda anclada. La franja y la tarjeta de objetivo se desplazan con la lista y desaparecen al bajar; no hay cabecera compacta anclada (sustituye a §2.10).
- **Día sin entradas (hoy, pasado o futuro).** No hay estado vacío que sustituya a la lista: se ven la cabecera con el objetivo y TODAS las tarjetas de comida del preset (§2.9), cada una con «🔥 0 kcal · 0 P | 0 H | 0 G», «Nada apuntado todavía» y su «+». Como ayuda de primer uso hay una línea discreta («Nada apuntado hoy» / «este día») al principio de la lista, sin acciones duplicadas. Sin objetivo, la tarjeta de objetivo trae «Sin objetivo diario» y «Fijar objetivo».
- **Implementación.** La fila anclada (‹ «Hoy ▾» › · racha · ⇅ Datos) va fuera de la lista; la franja y la tarjeta son el primer elemento de la lista. La barra del objetivo es una barra con dos marcas (arco descartado); la escala llega a max(objetivo × 1,3; consumidas). «Hoy ▾» abre el selector de fecha de Material (`DatePicker`). Las kcal por día salen de `DiaryStore.daySummaries` (consulta `selectDailyKcal`, sin migración); estado de día y racha, de `DayStatus`.
- **No se incluye** de la captura: banner Premium, foto de perfil, botón «Terminar día», pestañas Social y Coach, menús «⋯». «Coach» queda para más adelante, si se añade una IA con clave propia (Fase 11, pendiente).

### 2.13 Agua diaria (Fase 12)
Registro del agua bebida cada día, con progreso frente a un objetivo diario.
```
Diario: tarjeta «Agua» (entre el aviso de peso y las tarjetas de comida; `surfaceVariant`)
├─ Fila: «Agua» · «1,25 de 2 L» (titleMedium)
├─ Barra de progreso (se llena hasta el objetivo; pasarse no la desborda)
├─ Botones rápidos: «+ Vaso» (250 ml) · «+ 500 ml» · «+ 1 L»
└─ «Deshacer (−N ml)» tras sumar: quita la última suma; solo en esta pantalla y este día
Objetivos: sección «Agua» → «− 2 L al día +» en pasos de un vaso (250 ml); se guarda al pulsar
```
- **Datos.** Un total en ml por día (la fecha ISO es la clave; un día a 0 ml no tiene fila) y un objetivo único. Sin objetivo guardado vale 2 L (`WaterRules.DEFAULT_GOAL_ML`). Límites: objetivo de 500 a 10 000 ml (en la UI, en pasos de 250); total de un día de 0 a 20 000 ml (se recorta). `WaterStore.add(date, deltaMl, now)` suma (o resta, para deshacer) dentro de una transacción.
- **Día mostrado.** La tarjeta sigue al día elegido en el Diario (franja, ‹ ›, selector): se puede apuntar agua de otro día. «Deshacer» recuerda solo la última suma del día en pantalla.
- **Persistencia.** Migración `8.sqm` (v8 a v9, idempotente): tablas `WaterEntity(date, ml, updated_at)` y `WaterGoalEntity(id = 1, goal_ml, updated_at)` (`Water.sq`). Se guardan en tablas aparte (no en `SettingsEntity`) para no depender de otras migraciones.
- **Exportar e importar.** `ExportFile.water` (`date`, `ml`, `updatedAtMillis`) y `ExportFile.waterGoal` (`goalMl`, `updatedAtMillis`). Son campos opcionales nuevos, así que **`schemaVersion` sigue en 1**: los archivos anteriores se leen sin agua y los de ahora los ignora una app antigua. Fusión sin duplicados, igual que los pesos: la identidad es la fecha; igual total, se ignora; distinto, gana el de `updatedAtMillis` más reciente (empate: el local); la misma fecha dos veces en el archivo, la más reciente; el objetivo sigue la misma regla. Totales fuera de 1 a 20 000 ml, fechas mal formadas y objetivos fuera de rango se omiten y se listan. El informe de importación muestra una fila «Agua».
- **Implementación.** `WaterCard` y `WaterGoalRow` (`ui/components/WaterCard.kt`), `WaterStore` / `SqlDelightWaterStore` y `WaterRules` / `WaterDay` (`model/Water.kt`). `DiaryScreen`, `GoalsScreen` y `App` reciben `waterStore` (opcional: sin él no se muestra la tarjeta ni el ajuste). `SqlDelightImportTarget` y `SqlDelightExportSource` reciben `waterQueries` como último parámetro opcional.

### 2.14 Rediseño de composición (Claude Design)
Rediseño hecho en Claude Design (13 pantallas de 390 dp: Diario completo oscuro y claro, diálogos de copiar, Alimentos con Recientes/Favoritos/Favoritos vacío, Ficha, Objetivos, Mi peso y Recetas). **Los tokens no cambian** (§1.7: colores, Sora/Inter, 4 dp, margen 20); cambia la composición, inspirada en MacroFactor, Yazio y Lifesum. Donde lo de abajo difiere de §2.1 a §2.13, manda esta sección una vez implementado cada punto; hasta entonces manda lo implementado. Cada pantalla se implementa en un PR aparte.

**Decidido (entra):**
- **«+» cuadrado en la cabecera de la tarjeta de comida:** botón cuadrado `primary` de 36 dp a la derecha de la cabecera, en vez del botón ancho de abajo (§2.12).
- **Mensajes de copia dentro del diálogo** (§2.15), no en el snackbar.
- **Periodos 1M / 3M / Todo** en la gráfica de Mi peso.

**Decidido (no entra):** «Restantes» como número principal del Diario. Se mantiene la cabecera de §2.12 («consumidas / objetivo» y barra con las marcas ±10 %).

**Sin decidir (no se implementa de momento):** reparto de macros dentro de la tarjeta resumen de Objetivos (hoy es una tarjeta aparte, §2.6); escáner también dentro de la barra de búsqueda de Alimentos (hoy solo en los accesos).

**Por pantalla:**
- **Diario** (orden de la lista): cabecera, franja y tarjeta de objetivo de §2.12; aviso «Toca pesarse» a lo ancho (52 dp, ámbar); tarjeta **Agua** (§2.13: «1,25 de 2 L», barra de 8 dp en el color de hidratos que no desborda, «+ Vaso» · «+ 500 ml» · «+ 1 L» de 48 dp, «Deshacer (−250 ml)», error «No se pudo guardar o leer el agua.» en ámbar); fila con el rótulo «COMIDAS» y el botón «Copiar día» con icono a la derecha; tarjetas de comida con el título en Sora 18, «Copiar» justo a su derecha (solo con entradas), el resumen kcal/macros debajo, el «+» cuadrado a la derecha y filas con separadores de 1 px.
- **Alimentos:** título grande y «Añadiendo a Merienda» (si hay comida preseleccionada); búsqueda de 52 dp; **4 accesos en cuadrícula 4×72**: Recientes · Favoritos · Escáner · Alimento propio, donde Recientes y Favoritos son el selector de lista (el activo en `primaryContainer`, `aria-current`); rótulo «ÚLTIMOS ALIMENTOS · 24» / «FAVORITOS · 5»; filas de 72 con avatar de origen cuadrado (OFF = tertiaryContainer, G = secondaryContainer, P = warningContainer) y estrella de 48 dp al final (☆ en `onSurfaceVariant`, ★ rellena en `primary`); favoritos vacío con `EmptyState` (estrella de 32 en círculo de 72). Sin pestañas «Recientes · Favoritos · Propios» ni «+» de añadido rápido.
- **Ficha:** barra superior ← · «Producto» · ★ de favorito (`primary` si está marcado) · editar; chips «Open Food Facts» y «Dudoso»; aviso ámbar; tarjeta de cantidad con segmentado, −/campo/+, kcal en Sora 44 y barra apilada P/H/G con g y %; tabla «Por 100 g»; barra inferior con selector de comida y «Añadir · 163 kcal».
- **Objetivos:** tarjeta resumen (kcal en display, basal/TDEE, interruptor «Objetivo fijo»); Meta con segmentado y slider; «Tus datos» como 4 casillas; fila Actividad con la sugerencia por pasos; fila «Comidas al día»; sección «Agua» con − [2 L al día] + (§2.13); «Guardar objetivos» al final. El reparto de macros queda como hoy (tarjeta aparte) mientras no se decida.
- **Mi peso:** peso actual en display con chip verde de variación; tarjeta de progreso al objetivo con fecha estimada; gráfica con la línea del objetivo discontinua, el último punto destacado y selector de periodo **1M · 3M · Todo** (por defecto Todo); lista de registros; FAB «Registrar peso».
- **Recetas:** tarjetas con kcal por ración, barra apilada de macros y aviso ámbar si faltan datos; FAB «Nueva receta».
- **Navegación:** `NavigationBar` M3 estándar, activo en `primary`.

### 2.15 Copiar comida y copiar día (Fase 12, implementado)
Copia las entradas de una comida, o de todo el día, al diario de otra fecha. Implementación: `CopyDialog` (`ui/screens/CopyDialog.kt`), `DiaryPresenter.copyMeal` / `copyDay` (`DiaryState.kt`) y `DiaryCopy` (`domain`).
- **Dónde:** botón «Copiar» a la derecha del título de cada tarjeta de comida con entradas (las vacías no lo llevan) y botón «Copiar día» en la lista, solo si el día tiene alguna entrada.
- **Diálogo** (`AlertDialog`): título «Copiar Desayuno» (nombre de la comida) o «Copiar día»; «Copiar a» con ‹ [fecha larga] › (tocar la fecha abre el `DatePicker`; por defecto, el día anterior al mostrado); al copiar una comida, `FilterChip`s con scroll «La misma» (por defecto, la equivalente) y las comidas del preset activo; al copiar el día, el texto «Cada comida va a la misma del día elegido; si no existe en tu preset, a la más cercana.»; «Cancelar» / «Copiar».
- **Comportamiento:** las entradas (alimento y cantidad) se añaden al destino sin tocar lo que ya hubiera, al final de cada comida y en el mismo orden. Una comida de origen que no existe en el preset se reubica en la más cercana. El Diario sigue en el día de origen.
- **Mensajes.** Éxito: snackbar sin acción, «Copiado a 6 de noviembre: 5 alimentos» («1 alimento» en singular), con « (algunas comidas, a la más cercana)» si hubo reubicación; no tiene «Deshacer». Errores: «Elige otro día u otra comida.» (mismo día y misma comida), «No hay nada que copiar.» y «No se pudo copiar. Inténtalo de nuevo.». **Rediseño (§2.14):** los errores salen dentro del diálogo, encima de los botones, en ámbar con icono, y el diálogo no se cierra; hoy salen por el snackbar del Diario (`DiaryPresenter.fail`).

### 2.16 Entrada rápida de kcal y macros (Fase 12, implementado)
Para cuando se come fuera: una entrada del diario con nombre opcional y kcal y macros opcionales, sin crear un alimento. Implementación: `QuickEntryScreen` y `QuickEntryForm` (`ui/screens`), `QuickEntry` (`model/DiaryEntry.kt`) y `DiaryStore.updateQuick`.
- **Dónde:** botón «Entrada rápida · solo kcal y macros» bajo los accesos rápidos de `AddFoodScreen`. Se llega desde el «+» de una comida del Diario (comida y día vienen fijados: `Route.Add(meal, date)` → `Route.QuickEntry(meal, date)`), desde el botón «+ Añadir alimento» o desde la pestaña Alimentos (día = hoy; la comida se elige en la pantalla, por defecto la que toca por la hora).
- **Formulario:** `FilterChip`s con scroll para la comida (preset activo, §2.9), «Nombre (opcional)» (por defecto «Entrada rápida»), «Energía» (kcal) y «Proteínas / Hidratos / Grasa (opcional)» (g). Hace falta al menos un valor; los números van de 0 a 10 000 kcal y de 0 a 2 000 g. Errores en el propio campo; «Indica al menos las kcal o algún macro.» al intentar guardar sin nada. Botón «Añadir al diario».
- **En el Diario:** la fila muestra el nombre, los macros informados («30 P | 40 G») en lugar de la cantidad, sin origen ni chip «Dudoso», y las kcal a la derecha. Cuenta en el total de la comida, en el progreso y en la franja semanal igual que un alimento. Tocarla abre el formulario en modo edición («Editar entrada rápida», «Guardar cambios»; se puede cambiar comida, nombre y valores, no el día); pulsación larga: «Editar» o «Borrar» (con «Deshacer»), sin selector de cantidad.
- **Copiar:** «Copiar» comida / día conserva el carácter de entrada rápida y sus valores. Una entrada rápida no es un alimento: no sale en «Recientes» ni en las recetas.
- **Datos:** columna `quick INTEGER NOT NULL DEFAULT 0` en `DiaryEntryEntity` (migración v12 → v13, `12.sqm`; las filas existentes quedan en 0 y no se pierde nada). Se guarda como **100 g** (`GRAMS`) con los totales en las columnas «por 100 g», así `totals`, `selectDailyKcal`, la copia y la exportación funcionan sin cambios. Sin nombre se guarda «Entrada rápida».
- **Exportar e importar:** campo opcional `quick` en `diaryEntries`, **sin subir `schemaVersion`**: los archivos antiguos no lo traen y se leen como `false`; una versión anterior de la app que lo ignore verá un alimento de 100 g con esos valores. Con `quick = true` el importador exige cantidad 100 g; si no, la entrada se omite y se lista como no válida. La detección de duplicados es la del diario (fecha, código, nombre e instante de creación).
- **Limitación:** la ficha de un alimento sigue añadiendo siempre al día de hoy; solo la entrada rápida respeta el día mostrado en el Diario.

### 2.19 Resumen semanal y mensual (Fase 12)
Pantalla `SummaryScreen` (`ui/screens/SummaryScreen.kt`) con la lógica en `domain/PeriodSummary.kt` (`PeriodRange`, `PeriodSummaries`). Se deriva de datos existentes: entradas del diario (`DiaryStore.entriesBetween`, consulta `selectBetween`, sin migración), objetivo de kcal (`GoalStore`) y pesajes (`WeightStore`). No hay tabla nueva ni cambios en el formato de exportación.
- **Dónde vive y por qué.** Ruta `Route.Summary`, no es una pestaña. Se abre desde la fila anclada del Diario con el icono ▥ («Resumen semanal y mensual»), entre la racha y Datos (⇅). Motivos: (1) el resumen amplía lo que el Diario ya enseña (franja semanal con días cumplidos y racha, misma regla de ±10 % de `DayStatus`), así que el acceso natural está en esa misma fila; (2) la barra inferior ya tiene seis pestañas (§1.7), por encima del máximo cómodo de `NavigationBar`; (3) el peso es solo una de las cuatro tarjetas, por lo que no encaja como sección de «Mi peso». Se vuelve con «‹» o el gesto atrás de la plataforma.
```
Scaffold
├─ CenterAlignedTopAppBar: ‹ · «Resumen»
└─ LazyColumn (margen 20, separación 12)
   ├─ SingleChoiceSegmentedButtonRow: Semana · Mes
   ├─ ‹ [título del periodo] ›   («9–15 nov 2026» · «Noviembre de 2026»); bajo el título, «Esta semana» / «Este mes» si no se está en el actual
   ├─ MEDIA DIARIA: kcal (displayMedium, Sora) · «Objetivo 2.000 kcal · −120 kcal · sobre 5 días con registro» · StackedMacroBar con la media de P/H/G
   ├─ DÍAS CUMPLIDOS: «5 de 6» (headlineLarge) · 🔥 mejor racha · DailyKcalChart (barras por día + banda del objetivo)
   ├─ MEJOR DÍA | PEOR DÍA (dos tarjetas): fecha · kcal · diferencia con el objetivo
   └─ PESO FRENTE A CALORÍAS: «Peso: −0,6 kg, de 78,4 a 77,8 kg, con una media de 1.980 kcal al día (−120 kcal frente al objetivo).» · WeightChart del periodo
```
- **Periodos.** Semana de lunes a domingo y mes natural. «‹» retrocede sin límite; «›» se desactiva en el periodo actual. Cambiar de semana a mes vuelve al periodo actual.
- **Cálculos.** Las medias de kcal y macros son sobre los días **con registro** (los días vacíos no bajan la media; los futuros no cuentan). Una entrada sin dato de kcal o en porciones sin tamaño suma 0, igual que `daySummaries`. «Días cumplidos» usa `DayStatus` (objetivo ±10 %): se juzgan los días cerrados (cumplidos o fallados, incluidos los sin registro) y hoy solo cuenta si ya está cumplido. La mejor racha es la serie más larga de días cumplidos dentro del periodo (la racha actual sigue en el Diario). Mejor/peor día: el día registrado y juzgado más cerca/lejos del objetivo (empate: el más antiguo); con un solo día juzgable no hay «peor día».
- **Peso frente a calorías.** Con dos o más pesajes en el periodo se muestra la variación entre el primero y el último junto a la media de kcal del periodo y su diferencia con el objetivo; la gráfica es `WeightChart`. No se afirma causalidad: son los dos datos juntos.
- **Gráficas** (`DailyKcalChart`, `WeightChart`, `StackedMacroBar`): dibujadas con `Canvas`/`Row`, sin librerías, con los colores del tema (§1.7): dentro del margen en `tertiary`, fuera en `error`, sin objetivo o hoy pendiente en `primary`, banda del objetivo en líneas discontinuas y macros con `easyFoodColors`. El color nunca va solo: hay leyenda con texto, cada gráfica lleva `contentDescription` y las cifras salen en Inter/Sora con formato es-ES.
- **Estados.** Cargando: sin indicador (milisegundos, como el Diario). Error de lectura: `EmptyState` de error con «Reintentar». Sin registros: `EmptyState` «Aún no hay registros» con «Ir al Diario» (periodo actual) o «Sin registros en este periodo» con «Ver el periodo actual». Sin objetivo: la tarjeta de días cumplidos explica que hay que fijarlo en Objetivos, sin banda en la gráfica ni mejor/peor día. Con menos de dos pesajes: la tarjeta de peso lo explica en lugar de mostrar la gráfica.

### 2.17 Plantillas de comida (Fase 12)
«Mi desayuno de siempre»: un conjunto de alimentos con sus cantidades que se añade al diario de una vez. Implementación: `MealTemplate` / `TemplateItem` (`model`), `MealTemplateStore` (`data`), `TemplateList`, `TemplateEditScreen`, `TemplateDialogs` y `TemplateForm` (`ui/screens`).
- **Qué se guarda:** nombre y lista ordenada de alimentos; cada uno, como una entrada del diario, con copia del nombre y de los valores por 100 g (no cambia si se purga la caché) y su **cantidad con unidad** (gramos o porciones). Los nombres no se repiten (sin distinguir mayúsculas), porque la importación identifica por nombre.
- **Crear:** (1) desde el Diario, botón «Plantilla» junto a «Copiar» en cada comida con entradas: pide el nombre (por defecto, el de la comida) y guarda sus entradas tal cual; snackbar «Plantilla «Mi desayuno» guardada»; errores dentro del diálogo («Ya tienes una plantilla con ese nombre»). (2) Desde la pestaña Alimentos: acceso rápido «Plantillas» (junto a Recientes y Favoritos) → «Nueva», con el mismo buscador de alimentos que las recetas.
- **Añadir al diario:** en la lista «PLANTILLAS · n», **«Añadir»** apunta todos los alimentos de un toque en la comida preseleccionada (o la más cercana a la hora) y el día de hoy; tocar la fila abre el diálogo «Añadir a» con ‹ fecha › (`DateStepperRow`, compartido con «Copiar») y las comidas del preset. Crea una entrada por alimento, en orden, con su cantidad; la plantilla no cambia. Luego vuelve al Diario.
- **Gestionar:** ✎ abre `TemplateEditScreen`: renombrar, cambiar la cantidad de un alimento (tocándolo), quitarlo, añadir más y «Borrar» (con confirmación; lo ya añadido al diario no cambia).
- **Reutilización:** `FoodSearch`, el cuadro de cantidad y la barra superior de las recetas; el selector de fecha de «Copiar»; el orden por instantes crecientes de `DiaryCopy`/recetas. No se reutiliza `RecipeEntity` porque una receta se añade como un conjunto con raciones y solo en gramos, y la plantilla conserva las porciones.
- **Datos:** tablas `MealTemplateEntity` y `MealTemplateItemEntity` (`MealTemplate.sq`); migración v11 → v12 (`11.sqm`), idempotente y sin tocar tablas existentes.
- **Exportar e importar:** colección opcional `mealTemplates`, **sin subir `schemaVersion`**; los archivos antiguos se leen como vacía. Misma política que las recetas: identidad `(id, createdAtMillis)` o nombre sin distinguir mayúsculas; mismo contenido = duplicado; contenido distinto, gana el `updatedAtMillis` más reciente (empate: el local); plantillas sin nombre, sin alimentos, con cantidad ≤ 0 o nutrientes negativos se omiten y se listan («Plantillas de comida» en la vista previa).

### 2.16 IA: pestaña y ajustes (Fase 13)
Base técnica de la IA con la clave de API propia de cada usuario (sin servidor nuestro). Detalle de la API y decisiones en `docs/ia.md`.
- **Pestaña «IA»** (sexta de la barra inferior, icono ✦, etiqueta `labelSmall`). Sin clave, `EmptyState` (§2.7) centrado «Configura tu clave»; con clave pero desactivada o sin aviso de privacidad aceptado, «La IA está desactivada»; en ambos, acción «Ajustes de IA». En la barra superior, ⚙ abre los ajustes. Con la IA lista, columna con scroll (margen 20 dp, separación 16 dp) con, en este orden: **línea de uso** (`bodySmall`: «Hoy te quedan N de M llamadas»; con el tope alcanzado se convierte en la tarjeta «Límite diario alcanzado» con acceso a los ajustes) · **aviso «Antes de empezar»** (solo hasta pulsar «Entendido»; explica qué datos viajan en sugerencias y consejos; mientras no se acepta, los botones de IA quedan deshabilitados) · **fila «Herramientas»** (solo si hay acciones registradas: «Foto del plato», «Etiqueta»…) · **«¿Qué como?»** · **«¿Cómo voy?»** · **«Pregunta lo que quieras»** · tarjetas extra registradas · aviso fijo «Las respuestas de la IA son orientativas… No sustituyen a un profesional de la salud» (`bodySmall`). Las tarjetas usan `AiCard` (`surfaceContainerLow`, título `titleSmall`/`primary`, como Ajustes de IA).
- **«¿Qué como?»**: texto de ayuda, campo opcional «Preferencias (opcional): sin gluten, rápido…» (200 caracteres como máximo, no se guarda) y botón «Sugerir comida» (pasa a «Pensando…» y se deshabilita mientras espera). El resultado son 3–5 tarjetas `surfaceContainerHigh` con nombre y cantidad (`titleMedium`), «~210 kcal · P 10 g · H 12 g · G 13 g» (`bodyMedium`) y el motivo (`bodySmall`). Cada una tiene «Añadir al diario», que la apunta como **entrada rápida** (§2.16, «Entrada rápida») en la comida más cercana a la hora actual; después muestra «Añadido a Merienda» con «Ajustar» (abre la entrada rápida para editarla) y «Deshacer» (la borra). Bajo la lista: «Cifras aproximadas…».
- **«¿Cómo voy?»**: dos chips «Hoy» / «Esta semana»; al elegir uno se hace la llamada y se muestra un resumen de una o dos frases y hasta 4 puntos con marca (✓ bien, ▲ alto en `error`, ▼ bajo en `tertiary`), tema (kcal, proteína, fibra, sal, azúcares, agua, ejercicio) y consejo breve.
- **«Pregunta lo que quieras»**: campo «Tu pregunta» (300 caracteres, con contador) y «Preguntar». La conversación muestra solo los **últimos 4 mensajes** (también los únicos que se envían, más la pregunta nueva), solo en memoria; «Borrar conversación» la vacía.
- **Estados** de las tres tarjetas: sin clave / desactivada (estado vacío de toda la pestaña) · tope diario (tarjeta de aviso y botones deshabilitados) · error (texto en `error` bajo el botón, con «Reintentar» si merece la pena: red, 429, 5xx, respuesta cortada) · rechazo de la IA (mismo sitio pero en `onSurfaceVariant`, sin reintento) · «No se pudieron leer tus datos locales».
- **Puntos de extensión** (`AiTabRegistry.kt`): `aiTabActions` (botones de la fila «Herramientas», con `onClick(env)` que suele hacer `navigator.push(Route…)`) y `aiTabCards` (tarjetas propias bajo las de sugerencias, con `AiCard`). Cada función define la suya en su archivo y añade una línea a la lista; la pestaña ya resuelve sin clave, desactivada, aviso y tope.
- **Ajustes de IA** (`AiSettingsScreen`, ruta `Route.AiSettings`, con «atrás»): tarjetas `surfaceContainerLow` con el título en `titleSmall`/`primary`, en este orden: **Clave de API** (campo de contraseña «Pega aquí tu clave (sk-ant-…)» + «Guardar y probar»; con clave guardada se muestra «Clave guardada (…ABCD)» y «Borrar clave»; el texto explica que solo vive en el dispositivo) · **Activar la IA** (interruptor, deshabilitado sin clave; al activarlo por primera vez sale el aviso de privacidad) · **Modelo** (lista de radio: Haiku 5.5 por defecto, Sonnet 5.5, Opus 5.5, con precio por millón de tokens) · **Límite de llamadas al día** (− / +, pasos de 5; 30 por defecto, de 1 a 500) · **Probar conexión** (consulta la Models API y enseña si el modelo admite imágenes y salida estructurada) · **Uso** (llamadas de hoy y totales, gasto estimado, «Reiniciar contador»).
- **Aviso de privacidad** (`AlertDialog`): «Se envía a Anthropic» (la foto reducida a 1024 px, un resumen mínimo del día —kcal y macros consumidos y objetivos— y la indicación de la tarea; en sugerencias y consejos, además, nombre y kcal de unos pocos favoritos, recientes, plantillas y recetas, agua, ejercicio, fibra, sal y azúcares y medias de la semana) y «No se envía» (nombre, correo, historial completo, pesos, contenido de las recetas; la clave solo viaja a Anthropic en la cabecera). Hay que aceptarlo antes de la primera llamada: sin aceptar, `AiService` no envía nada.
- **Mensajes de error** en español, en el color `error` bajo la acción que los provocó (clave no válida, saldo, límite de peticiones, servicio caído, sin conexión, negativa de la IA, respuesta cortada o mal formada, límite diario).
- **Sin clave, la IA queda desactivada y el resto de la app no cambia.**

### 2.20 Alta por foto de la etiqueta (Fase 13)
Cuando un alimento no aparece, se puede crear leyendo su etiqueta con la IA (§2.16). Todo el flujo termina en el formulario de alimento propio (§2.5): no hay un segundo formulario ni un segundo almacén.
- **Entradas:** «Fotografiar etiqueta» en el estado «Código no encontrado» del escáner (§2.3; el EAN viaja a la pantalla y se conserva al guardar) y en el estado «Sin resultados» de la búsqueda de Alimentos (sin EAN). En `EmptyState` es una tercera acción (`tertiaryAction`, `TextButton`). También hay una acción «Etiqueta» (▣) en la fila «Herramientas» de la pestaña IA (`aiLabelAction`), sin EAN.
- **Pantalla «Foto de la etiqueta»** (`LabelPhotoScreen`, `Route.LabelPhoto(barcode, meal)`, ✕ para cerrar). Antes de pedir la foto se comprueba la IA (`AiService.blocker()`): sin clave, desactivada, sin aviso aceptado o con el tope diario alcanzado se muestra el motivo en un bloque `errorContainer` (con «Ajustes de IA», salvo el tope) y no se abre la cámara. Si todo está listo: título, explicación, nota de privacidad (la foto va a Anthropic con tu clave, reducida a 1024 px y sin metadatos, y no se guarda) y los botones **Hacer foto** (Android e iOS; cámara a pantalla completa con «Hacer foto» y «Cancelar») y **Elegir imagen** (Android, galería) o **Subir imagen** (escritorio, diálogo de archivo). «Rellenar a mano» siempre abre el formulario normal.
- **Leyendo:** miniatura de la foto (512 px), «Leyendo la etiqueta…» y barra de progreso.
- **Error o rechazo:** la foto sigue en pantalla con el mensaje de `AiError` en `errorContainer` y, según el caso, **Reintentar** (red, límite de peticiones, servicio caído, respuesta cortada: `retryable`), **Probar con otra foto** y **Rellenar a mano**. Si la IA no lee nada (no es una etiqueta), se explica y no se ofrece reintentar.
- **Resultado:** `replace` por `Route.CustomFood(barcode, prefill, brand, unreadFields)`. El formulario muestra «Leído de la foto de la etiqueta por la IA: compruébalo y corrígelo antes de guardar», el aviso ámbar «Revisa estos datos» (`DoubtfulDataBanner` sin acción, mismas reglas que la ficha: kcal frente a 4·P + 4·H + 9·G, macros por encima de 100 g, saturadas > grasas, azúcares > hidratos; se recalcula al editar) y, bajo cada campo vacío que la IA no pudo leer, «No se leyó en la etiqueta». Los obligatorios vacíos siguen impidiendo guardar. Los ingredientes se leen pero **no se guardan**: no hay columna para ellos (evita una migración).

### 2.18 Ejercicio que suma al objetivo (implementado)
Registro manual de actividades por día; las kcal gastadas suben el objetivo de ese día.
```
Diario: tarjeta «Ejercicio» (tras la de agua; `surfaceVariant`)
├─ Fila: «Ejercicio» · «N kcal» (si hay) y a la derecha «+ Añadir»
├─ Efecto: «+200 kcal al objetivo (50 % de 400)» · sin actividades: «Suma el 50 % de lo que gastes al objetivo del día.» · desactivado: «Desactivado en Objetivos: no suma al objetivo.»
└─ Una fila por actividad: nombre, duración opcional («40 min», «1 h 30 min»), kcal y «Quitar»
Diálogo «Añadir ejercicio»: Actividad (hasta 60 caracteres) · Kcal gastadas (1 a 10 000) · Duración opcional (1 a 1440 min)
Objetivos: sección «Ejercicio» (antes de «Agua») → No sumar · 25 % · 50 % (recomendado) · 75 % · Sumar todo; se guarda al elegir
```
- **Objetivo efectivo.** `objetivo del día = objetivo guardado + kcal de ejercicio × porcentaje / 100` (redondeado, `ExerciseRules.bonusKcal`). Solo cambia las kcal: los macros no se tocan. Sin objetivo de kcal definido, el ejercicio no crea uno. El objetivo efectivo es el que ven la tarjeta de objetivo, la franja semanal y la racha (cada día usa el ejercicio de ese día). El porcentaje es global, no se guarda por día: al cambiarlo se recalculan también los días pasados.
- **Criterio contra el doble conteo.** El TDEE ya incluye la actividad habitual: el factor del nivel de actividad (§2.6) y, si se usa, la sugerencia por pasos (`StepsActivity`) cuentan el ejercicio medio de tu semana. Por eso (1) se registra solo el ejercicio que **no** está ya en ese nivel (una sesión extra, no el entrenamiento de siempre), y (2) por defecto se suma solo el **50 %**, porque tablas y relojes sobreestiman y el solapamiento con el nivel nunca es nulo. Quien prefiera que el objetivo no cambie pone «No sumar»; quien lleve nivel «Sedentaria» y registre todo su ejercicio puede subir a 100 %. Los pasos del móvil no se suman a mano: ya entran por el nivel.
- **Datos.** `ExerciseEntity(id, date, name, kcal, minutes?, created_at)` (varias por día, índice por fecha) y `ExerciseSettingsEntity(id = 1, percent, updated_at)`; sin fila de ajuste vale `ExerciseRules.DEFAULT_PERCENT` (50). Migración `13.sqm` (v13 a v14, idempotente, solo crea tablas: no toca datos existentes), con `Migration13Test`.
- **Exportar e importar.** `ExportFile.exercises` (`date`, `name`, `kcal`, `minutes`, `createdAtMillis`) y `ExportFile.exercisePercent` (`percent`, `updatedAtMillis`): colecciones opcionales nuevas, así que **`schemaVersion` sigue en 1**. Las actividades no tienen id estable entre dispositivos: se reconocen por fecha, nombre (sin distinguir mayúsculas), kcal y duración; las idénticas se cuentan como duplicadas (y dos sesiones idénticas del mismo día se conservan) y el resto se añade. El porcentaje sigue la regla del agua (gana el más reciente; empate, el local). Los inválidos se omiten y se informan. Vista previa y resumen incluyen la fila «Ejercicio».
- **Implementación.** `ExerciseCard`, `ExerciseDialog` y `ExercisePercentPicker` (`ui/components/ExerciseCard.kt`), `ExerciseStore` / `SqlDelightExerciseStore`, `ExerciseRules` / `NewExercise` / `ExerciseEntry` (`model/Exercise.kt`). `DiaryPresenter` recibe el almacén (opcional) y expone `exercises`, `exerciseBonusKcal` y el `goalKcal` efectivo; `DayStatus.streak` y `buildWeek` admiten el extra por día.

### 2.19 Fibra, sal, azúcares y saturadas del día (implementado)

Tarjeta «OTROS NUTRIENTES» al final de la lista del Diario (`IntakeCard`), solo si el día tiene entradas. Una fila por nutriente: nombre a la izquierda y «12 g · mín. 25 g» a la derecha.

- **Objetivos orientativos** (`IntakeGuidelines`, no son consejo médico, no editables): fibra mínimo 25 g; sal máximo 5 g (OMS); azúcares máximo el 10 % de la energía a 4 kcal/g (50 g con 2 000 kcal) y saturadas máximo el 10 % a 9 kcal/g (~22 g). La energía es el objetivo de kcal de Objetivos o, sin él, 2 000 kcal. Hacerlos editables exigiría columnas nuevas en `DailyGoalEntity`; queda fuera.
- **Datos ausentes**: un valor `null` nunca se suma como 0 sin avisar. Si alguna entrada no tiene el dato (o está en porciones sin tamaño) la fila añade «· incompleto» y el total es solo lo conocido; si ninguna lo tiene, «sin datos · máx. 5 g». Se pinta en `onSurfaceVariant`. Pasarse de un máximo se pinta en `error`; no llegar a un mínimo no es un fallo.
- **Datos**: la entrada del Diario copia al registrar saturadas, azúcares, fibra y sal por 100 g (migración 16, 4 columnas nullable; las entradas rápidas tampoco las traen y salen «incompleto»). Las entradas anteriores quedan en `NULL` y salen «incompleto» hasta que se vuelvan a apuntar.
- **Por comida**: no se desglosa; la tarjeta de cada comida mantiene «kcal · P | H | G».
- **Micronutrientes (vitaminas y minerales)**: la base USDA embebida (`generic_foods.tsv`) solo tiene las 9 columnas de macros; no se guardan. Añadirlos pide regenerar el TSV, ampliar `ProductEntity` y `DiaryEntryEntity` y subir `DATA_VERSION`, y solo cubriría los alimentos genéricos (Open Food Facts y los propios no los traen de forma fiable). No compensa ahora.
- **Exportar**: sin cambios de formato; `ExportedNutrients` ya llevaba los 8 campos y ahora van rellenos también en el diario.

### 2.20 Recordatorios locales (Fase 12)

- **Pantalla.** «Recordatorios» (🔔 en la barra de Objetivos, `Route.Reminders`): interruptor general (al activarlo pide el permiso de notificaciones; si se deniega, explica cómo activarlo y abre los ajustes de la app), bloque «Comidas» (una hora por cada comida del preset de comidas al día, con − / + de 15 min, y los días L-D), bloque «Pesarme» (día de la semana y hora) y bloque «Agua» (opcional, una hora al día). Todo apagado por defecto; cada cambio se guarda al momento.
- **Qué avisa y cuándo no.** `ReminderPlanner` (función pura) calcula los avisos de los próximos 7 días. Hoy no avisa de una comida ya registrada ni de una hora que ya pasó; el peso no avisa si ya hay un registro esa semana (de lunes al día del aviso); el agua no avisa hoy si ya se ha llegado al objetivo. `ReminderSync` recalcula y reemplaza lo programado al arrancar, al cambiar de pantalla (p. ej. tras registrar una comida) y al editar los ajustes. Los días futuros no se pueden saber de antemano: si se registra algo ese día con la app cerrada, el aviso saldrá igualmente.
- **Plataformas** (`expect`/`actual` `rememberReminderScheduler()`): Android, `AlarmManager.setAndAllowWhileIdle` (alarma inexacta, sin permiso de alarmas exactas: puede retrasarse unos minutos), canal «Recordatorios», `POST_NOTIFICATIONS` en Android 13+ y `ReminderBootReceiver` que reprograma el último plan guardado tras reiniciar o actualizar la app; iOS, `UNUserNotificationCenter` con notificaciones de calendario de un solo disparo; escritorio, notificación de la bandeja del sistema con un `Timer` mientras la app está abierta (sin bandeja, no hace nada). Como solo hay plan para 7 días, si no se abre la app en una semana los avisos se acaban.
- **Datos.** Un único JSON en la columna `SettingsEntity.reminders` (`ReminderSettings`), nulo = sin configurar. Migración `17.sqm` (reconstruye la tabla para ser idempotente; la fila existente conserva `meals_per_day`).
- **Exportar e importar.** Campo opcional `ExportFile.reminders`, **sin subir `schemaVersion`**: los archivos antiguos no lo traen. Como las comidas al día, solo se toma si el dispositivo aún no los ha configurado; se sanea (días 1 a 7, horas 0 a 1439).

## 3. Componentes compartidos (en `shared/ui`)
`EasyFoodTheme` · `CalorieRing` · `MacroProgressRow` · `MacroDot` · `SourceAvatar` / `SourceChip` (enum `DataSource { OpenFoodFacts, Generic, Custom }`) · `DoubtfulChip` · `DoubtfulDataBanner` · `MealSection` · `DiaryEntryRow` · `FoodResultRow` · `QuantitySelector` · `NutritionTable` · `EmptyState` · `NumberField` (OutlinedTextField con sufijo de unidad) · `FoodSearch` (buscador de alimentos de «Añadir» y del editor de recetas) · `DailyKcalChart` (barras de kcal por día con la banda del objetivo, §2.16).

## 4. Navegación
Diario ⇄ Añadir (con `meal` como argumento) → Escáner / Alimento propio → Ficha → vuelve al Diario con la entrada añadida y el snackbar «Añadido a Merienda · Deshacer». Objetivos y Recetas se abren desde la barra inferior (Recetas → Detalle → Editar, o Recetas → Nueva receta → Buscador de ingredientes). Formato de números: locale es-ES (1.215 · 4,6 g). La pestaña «IA» (§2.16) es la sexta de la barra inferior y abre «Ajustes de IA». El Resumen (§2.19) se abre con el icono ▥ de la fila del Diario y vuelve a él.
