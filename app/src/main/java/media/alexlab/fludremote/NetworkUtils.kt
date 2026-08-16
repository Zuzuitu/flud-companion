package media.alexlab.fludremote

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {
    fun localIpv4(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        } catch (_: Exception) {
            null
        }
    }

    private fun <T> java.util.Enumeration<T>.toList(): List<T> {
        val result = mutableListOf<T>()
        while (hasMoreElements()) result += nextElement()
        return result
    }
}
