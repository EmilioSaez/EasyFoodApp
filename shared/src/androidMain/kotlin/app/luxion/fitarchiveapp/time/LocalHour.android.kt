package app.luxion.fitarchiveapp.time

import java.time.LocalTime

actual fun currentHour(): Int = LocalTime.now().hour
