package app.luxion.fitarchiveapp.time

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSDate

actual fun currentHour(): Int = NSCalendar.currentCalendar.component(NSCalendarUnitHour, fromDate = NSDate()).toInt()
