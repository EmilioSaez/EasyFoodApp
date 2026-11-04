package app.luxion.fitarchiveapp.transfer

import java.security.SecureRandom

private val secureRandom = SecureRandom()

internal actual fun secureRandomInt(bound: Int): Int = secureRandom.nextInt(bound)
