package app.luxion.fitarchiveapp.transfer

/** Entero aleatorio criptográficamente seguro en `[0, bound)`. Solo para códigos de emparejamiento. */
internal expect fun secureRandomInt(bound: Int): Int
