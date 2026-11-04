package app.luxion.fitarchiveapp.transfer

/** Direcciones IPv4 de las interfaces de red activas (las de red local primero). Vacío si no se pueden obtener. */
expect fun localIpAddresses(): List<String>
