# EasyFood: especificación de diseño (Material 3 / Compose Multiplatform)

Lienzo con las pantallas: https://claude.ai/artifact/8K3RKHCUXz6peLbMoUVom5
Mockups a 390 dp de ancho (teléfono). En escritorio se usan las mismas pantallas en una columna de 600 dp como máximo, centrada.

## 1. Tokens

### 1.1 Color: `lightColorScheme` / `darkColorScheme` (semilla verde #386A20)

| Rol M3 | Claro | Oscuro |
|---|---|---|
| primary | #386A20 | #9DD67D |
| onPrimary | #FFFFFF | #0B3900 |
| primaryContainer | #B8F397 | #205107 |
| onPrimaryContainer | #042100 | #B8F397 |
| secondary | #55624C | #BDCBB0 |
| onSecondary | #FFFFFF | #283420 |
| secondaryContainer | #D9E7CB | #3E4A35 |
| onSecondaryContainer | #131F0D | #D9E7CB |
| tertiary | #386666 | #A0CFCF |
| tertiaryContainer | #BBEBEB | #1E4E4E |
| onTertiaryContainer | #002020 | #BBEBEB |
| error | #BA1A1A | #FFB4AB |
| errorContainer | #FFDAD6 | #93000A |
| onErrorContainer | #410002 | #FFDAD6 |
| surface / background | #F8FAF0 | #11140F |
| surfaceContainerLowest | #FFFFFF | #0C0F0A |
| surfaceContainerLow | #F2F5EA | #191D16 |
| surfaceContainer | #ECEFE4 | #1D211A |
| surfaceContainerHigh | #E6E9DF | #282B24 |
| surfaceContainerHighest | #E1E4D9 | #33362F |
| onSurface | #191D16 | #E1E4D9 |
| onSurfaceVariant | #43483E | #C3C8BB |
| outline | #74796D | #8D9286 |
| outlineVariant | #C3C8BB | #43483E |
| inverseSurface | #2E312B | #E1E4D9 |
| inverseOnSurface | #EFF2E7 | #2E312B |
| inversePrimary | #9DD67D | #386A20 |

### 1.2 Colores extendidos (no existen en M3)
Van en un `data class EasyFoodColors` y se exponen con `staticCompositionLocalOf` (`LocalEasyFoodColors`).

| Token | Claro | Oscuro | Uso |
|---|---|---|---|
| macroProtein | #2F5DA8 | #ADC6FF | barra, punto y segmento de proteína |
| macroCarbs | #D9822B | #FFB77C | hidratos |
| macroFat | #8E4B8F | #F0B0F0 | grasa |
| warningContainer | #FFDF9E | #5C4300 | aviso de datos dudosos, chip «Dudoso» |
| onWarningContainer | #261A00 | #FFDF9E | texto e icono sobre warningContainer |
| warning | #7A5900 | #F5BF48 | valor «Sin dato» en la tabla |

Origen del dato (avatar y chip):
- Genérico → secondaryContainer / onSecondaryContainer, inicial «G»
- Open Food Facts → tertiaryContainer / onTertiaryContainer, «OFF», icono de globo
- Propio → warningContainer / onWarningContainer, «P», icono de lápiz

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
```
Scaffold
├─ CenterAlignedTopAppBar: IconButton(‹) · título «Hoy» + subtítulo fecha (bodySmall) · IconButton(›)
├─ LazyColumn (padding 16, spacedBy 12)
│  ├─ DaySummaryCard  (surfaceContainerLow, large)
│  │  ├─ CalorieRing 120 dp: track surfaceContainerHighest, progreso primary, trazo 10 dp, extremo redondo
│  │  │   centro: restantes (headlineSmall) + «kcal restantes» (bodySmall)
│  │  │   si se pasa del objetivo: arco en error y texto «X kcal de más»
│  │  └─ Column: «1.215 / 2.100 kcal» + 3 × MacroProgressRow (label, «68 / 130 g», LinearProgressIndicator 6 dp con color de macro)
│  └─ MealSection × 4 (Desayuno, Comida, Cena, Snacks)  OutlinedCard (surfaceContainerLowest, outlineVariant)
│     ├─ cabecera: nombre (titleMedium) + «386 kcal · P 17 · H 62 · G 8» (bodySmall) + IconButton(+) → Añadir con la comida preseleccionada
│     ├─ vacía: texto bodyMedium onSurfaceVariant
│     └─ DiaryEntryRow × n (ListItem): nombre (1 línea, ellipsis) · «cantidad · origen» · SourceBadge/DoubtfulChip · kcal (labelLarge) a la derecha
│        toque → Ficha en modo edición; deslizar o pulsación larga → borrar (con snackbar «Deshacer»)
├─ ExtendedFloatingActionButton «Añadir alimento» (primaryContainer), encima de la barra
└─ NavigationBar: Diario · Alimentos · Objetivos (indicador secondaryContainer)
```
Estado: `DiaryUiState(date, goalKcal, consumedKcal, macros: List<MacroProgress>, meals: List<MealUi>)`.

### 2.2 Añadir alimento (`AddFoodScreen`)
```
TopAppBar: ← · «Añadir a» · FilterChip/menú desplegable con la comida (Comida ▾)
SearchBar (inline, extraLarge, surfaceContainerHigh): icono lupa · texto · IconButton borrar
Fila de 3 accesos rápidos (grid 3 col, gap 8, alto 72, medium):
   Escanear (primaryContainer) · Código EAN · Alimento propio (surfaceContainer)
FilterChips en LazyRow: Todos (seleccionado) · Genérico · Open Food Facts · Propios
Cabecera «Resultados · 24» (titleSmall primary)  · sin texto: «Recientes»
LazyColumn de FoodResultRow (ListItem 72): SourceAvatar 40 · nombre / «marca · tamaño · EAN» · «255» + «kcal/100 g»
```
- Búsqueda: debounce de 300 ms y FTS local; Open Food Facts solo si hay red, mezclado detrás de los resultados locales.
- En escritorio, «Escanear» se oculta y «Código EAN» pasa a primaryContainer.

### 2.3 Escáner + EAN manual (`ScannerScreen`)
```
Box fondo #11140F
├─ vista previa de cámara (expect/actual: CameraX+ML Kit / AVFoundation)
├─ barra superior transparente: IconButton cerrar · «Escanear código» · IconButton linterna
├─ marco 300×190, radio 16, borde 2 dp inversePrimary + línea guía horizontal
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
| Error de red puntual | — | Snackbar «Open Food Facts no responde.» + acción «Reintentar» | mantiene el contenido ya cargado |

Para las cargas, usa `LinearProgressIndicator` bajo la barra de búsqueda o filas placeholder (shimmer suave con surfaceContainerHigh), no spinners a pantalla completa.

## 3. Componentes compartidos (en `shared/ui`)
`EasyFoodTheme` · `CalorieRing` · `MacroProgressRow` · `MacroDot` · `SourceAvatar` / `SourceChip` (enum `DataSource { OpenFoodFacts, Generic, Custom }`) · `DoubtfulChip` · `DoubtfulDataBanner` · `MealSection` · `DiaryEntryRow` · `FoodResultRow` · `QuantitySelector` · `NutritionTable` · `EmptyState` · `NumberField` (OutlinedTextField con sufijo de unidad).

## 4. Navegación
Diario ⇄ Añadir (con `meal` como argumento) → Escáner / Alimento propio → Ficha → vuelve al Diario con la entrada añadida y el snackbar «Añadido a Snacks · Deshacer». Objetivos se abre desde la barra inferior. Formato de números: locale es-ES (1.215 · 4,6 g).
