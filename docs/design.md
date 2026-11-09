# EasyFood: especificación de diseño (Material 3 / Compose Multiplatform)

Lienzo con las pantallas: https://claude.ai/artifact/8K3RKHCUXz6peLbMoUVom5
Mockups a 390 dp de ancho (teléfono). En escritorio se usan las mismas pantallas en una columna de 600 dp como máximo, centrada.

## 1. Tokens

### 1.1 Color: tema rojo y negro (oscuro por defecto)
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

## 2. Pantallas

### 2.1 Diario del día (`DiaryScreen`)
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
```
TopAppBar: ← · «Alimentos»   (sin selector de comida)
SearchBar (inline, extraLarge, surfaceContainerHigh): icono lupa · texto · IconButton borrar
Fila de 3 accesos rápidos (grid 3 col, gap 8, alto 72, medium):
   Recientes (primaryContainer) · Escáner · Alimento propio (surfaceContainer)
Sin texto en la búsqueda: lista «Recientes» (los últimos alimentos distintos del diario, del más reciente al más antiguo)
Con texto: cabecera «Resultados · 24» (titleSmall primary) y LazyColumn de FoodResultRow (ListItem 72):
   SourceAvatar 40 · nombre / «marca · tamaño · EAN» · «255» + «kcal/100 g»
```
- **Sin filtros:** se eliminan las fichas «Todos · Genérico · Open Food Facts · Propios» y el filtro por origen. Los resultados mezclan todos los orígenes (genérico, Open Food Facts y propios); el origen sigue visible en cada fila con `SourceAvatar`/`SourceChip`.
- **Sin comida preseleccionada:** la pestaña Alimentos ya no pide la comida arriba. Al abrir la ficha de un alimento desde la pestaña, la comida se elige en la propia ficha (por defecto la comida que toca por la hora, §2.9). Si se llega desde el «+» de una sección del Diario, la comida viene preseleccionada y no se pregunta de nuevo.
- **«Recientes»:** la lista de recientes se ve por defecto cuando no hay texto; el botón sirve para volver a ella tras buscar (borra el texto). No necesita tabla nueva: se deriva de `DiaryEntry`.
- **«Escáner»:** sustituye a «Código EAN». Abre `ScannerScreen`, que ya incluye la entrada manual de EAN (§2.3). En escritorio no hay cámara y el botón abre directamente la entrada manual.
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
TopAppBar: ← · «Producto» · IconButton editar (copiar como propio)
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
└─ «Fuente: Open Food Facts · actualizado hace 3 días» (bodySmall)
BottomBar (surfaceContainer, padding 16): Button «Añadir a Snacks» (48 dp, icono +). En modo edición: «Guardar cambios» + IconButton borrar.
```

### 2.5 Alimento propio (`CustomFoodScreen`)
TopAppBar con ✕, título y TextButton «Guardar». OutlinedTextField «Nombre»; fila con «Porción» (texto) y «Peso porción» (g). «Valores por 100 g»: grid de 2 columnas con 8 campos numéricos y el sufijo de unidad. La validación aparece en línea con `isError` y supportingText (p. ej. «Mayor que las grasas»). Si se abre desde un EAN no encontrado, el código va prerrelleno y no se puede editar. Se guarda con origen = Propio.

### 2.6 Objetivos y TDEE (`GoalsScreen`)
```
TopAppBar «Objetivos»
├─ ResultCard (primaryContainer, large): «Objetivo diario» · 2.100 kcal (displayMedium) · grid 2 col: Metabolismo basal / Gasto total (TDEE) · fórmula (bodySmall)
├─ «Tus datos»: SegmentedButton Hombre | Mujer · grid 3 col de OutlinedTextField: Edad (años), Peso (kg), Altura (cm)
├─ «Actividad física»: 5 filas RadioButton con nombre, descripción y factor
│    Sedentaria ×1,2 · Ligera ×1,375 · Moderada ×1,55 · Alta ×1,725 · Muy alta ×1,9
├─ «Meta»: SegmentedButton Perder | Mantener | Ganar · Slider con ritmo (0 / 0,25 / 0,5 / 0,75 / 1 kg/sem; 1 kg/sem ≈ 1.100 kcal/día)
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
| Diario sin entradas | neutro (secondaryContainer) | Nada apuntado hoy | Añadir alimento · Fijar objetivo |
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

### 2.10 Cabecera fija del Diario (Fase 10)
- **Siempre visible:** bajo la barra de fecha, una cabecera que NO se desplaza con la lista (queda anclada arriba) con el objetivo y cómo se va: `CalorieRing` compacto o barra, «consumidas / objetivo kcal», «X kcal restantes» (o «X kcal de más» en error) y los tres macros (P/H/G) con su progreso.
- **Sin objetivo:** en lugar del progreso, la misma cabecera muestra «Sin objetivo diario» y el botón «Fijar objetivo» que abre Objetivos.
- **Sustituye** a `DaySummaryCard`, que deja de formar parte de la lista. La lista (comidas) se desplaza por debajo.
- El aviso «Toca pesarse» sigue siendo una tarjeta de la lista (no de la cabecera).
- Altura contenida (unos 120 dp) para no quitar espacio en móviles pequeños; en pantallas con poca altura, los macros pueden ir en una sola línea.

### 2.11 Gráfica de peso: un punto por registro (Fase 10)
En `WeightChart` (Mi peso) se dibuja un punto por cada registro, no solo el primero y el último. El último registro se destaca (punto relleno, mayor); los demás son puntos más pequeños. La línea los une. Accesibilidad: la descripción de la gráfica sigue resumiendo «N registros, de X a Y, último Z kg».

## 3. Componentes compartidos (en `shared/ui`)
`EasyFoodTheme` · `CalorieRing` · `MacroProgressRow` · `MacroDot` · `SourceAvatar` / `SourceChip` (enum `DataSource { OpenFoodFacts, Generic, Custom }`) · `DoubtfulChip` · `DoubtfulDataBanner` · `MealSection` · `DiaryEntryRow` · `FoodResultRow` · `QuantitySelector` · `NutritionTable` · `EmptyState` · `NumberField` (OutlinedTextField con sufijo de unidad) · `FoodSearch` (buscador de alimentos de «Añadir» y del editor de recetas).

## 4. Navegación
Diario ⇄ Añadir (con `meal` como argumento) → Escáner / Alimento propio → Ficha → vuelve al Diario con la entrada añadida y el snackbar «Añadido a Snacks · Deshacer». Objetivos y Recetas se abren desde la barra inferior (Recetas → Detalle → Editar, o Recetas → Nueva receta → Buscador de ingredientes). Formato de números: locale es-ES (1.215 · 4,6 g).
