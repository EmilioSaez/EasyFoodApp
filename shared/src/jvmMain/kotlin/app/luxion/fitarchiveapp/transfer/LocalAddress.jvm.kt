package app.luxion.fitarchiveapp.transfer

import java.net.Inet4Address
import java.net.NetworkInterface

actual fun localIpAddresses(): List<String> = try {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback && !it.isVirtual }
        .flatMap { nic -> nic.inetAddresses.toList().filterIsInstance<Inet4Address>() }
        .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
        .sortedByDescending { it.isSiteLocalAddress }
        .mapNotNull { it.hostAddress }
} catch (e: Exception) {
    emptyList()
}
