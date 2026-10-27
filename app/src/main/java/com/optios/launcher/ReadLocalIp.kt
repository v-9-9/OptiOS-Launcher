package com.optios.launcher

import java.net.InetAddress
import java.net.NetworkInterface

/** Enumerates the Wi-Fi IPv4 so we can display "THIS-IP:PORT" on screen. */
object ReadLocalIp {
    val ipv4: String?
        get() = try {
            val candidates = java.util.Collections.list(NetworkInterface.getNetworkInterfaces())
                .flatMap { nif -> java.util.Collections.list(nif.inetAddresses) }
                .filter { ip -> ip is InetAddress && !ip.isLoopbackAddress && (ip as InetAddress).hostAddress?.contains(':') == false }
            candidates.firstOrNull()?.hostAddress
        } catch (_: Exception) { null }
}
