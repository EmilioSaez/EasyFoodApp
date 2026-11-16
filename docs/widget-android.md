# Widget de Android «EasyFood · Hoy»

Widget de pantalla de inicio (4×2, redimensionable) con:

- **kcal restantes** del día (objetivo − tomadas); «kcal de más» si se pasa y «kcal hoy» si no hay objetivo.
- **Macros** `P tomadas/objetivo · H … · G …` y **agua** `tomada / objetivo L`.
- **+ Vaso** → abre la app en el Diario (donde vive la tarjeta de agua con su «+ Vaso»).
- **Entrada rápida** → abre la pantalla de entrada rápida. Tocar el resto del widget abre el Diario.

## Cómo está hecho

- `AppWidgetProvider` + RemoteViews (`androidApp/.../widget/DailyWidget.kt`): sin Jetpack Glance, para no añadir
  dependencias ni acoplarse a la versión de Compose Multiplatform.
- Los números salen de `DayWidgetSnapshot` (`shared/.../domain`, con test en `commonTest`).
- `WidgetData` lee la base de la app **solo si su versión de esquema es la actual**: nunca crea ni migra la base, para
  que la migración con copia de seguridad siga haciéndola `MainActivity`. Hasta entonces el widget pide abrir la app.
- Los botones lanzan `MainActivity` con el extra `DESTINATION` (`diary`/`water`/`quick`); `App` recibe `launchRoute`
  y lo abre encima del Diario (`launchMode=singleTop` + `onNewIntent` si la app ya estaba abierta).
- Sin cambios de esquema: no hay migración.

## Cuándo se actualiza

- Al salir la app de primer plano (`MainActivity.onPause`): ya están guardadas las comidas/agua registradas.
- Al cambiar de día, hora o zona horaria (`DATE_CHANGED`, `TIME_SET`, `TIMEZONE_CHANGED`).
- Cada 30 min (`updatePeriodMillis`) como red de seguridad.

## Probarlo

`shogun emulator start`, instalar `:androidApp:assembleDebug`, mantener pulsado el inicio → Widgets → «EasyFood · Hoy».
