package com.huevoman77.cazahosts.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

data class Neighbour(val ip: String, val mac: String, val device: String)

object Net {

    /** Conexión TCP con tiempo de espera. Devuelve el socket abierto o null. */
    fun connect(ip: String, port: Int, timeoutMs: Int): Socket? = try {
        val socket = Socket()
        socket.connect(InetSocketAddress(ip, port), timeoutMs)
        socket.tcpNoDelay = true
        socket
    } catch (e: Exception) {
        null
    }

    /** Prueba de vida rápida: puerto TCP abierto. */
    fun portOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        val socket = connect(ip, port, timeoutMs) ?: return false
        return try {
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun reverseDns(ip: String): String? = try {
        val address = InetAddress.getByName(ip)
        val name = address.canonicalHostName
        if (name.isNotEmpty() && name != ip && !name.endsWith(".in-addr.arpa")) name else null
    } catch (e: Exception) {
        null
    }

    /** Tabla ARP del dispositivo: en red local revela quién está conectado. */
    fun arpNeighbours(): List<Neighbour> = try {
        val file = java.io.File("/proc/net/arp")
        if (!file.canRead()) emptyList()
        else BufferedReader(InputStreamReader(file.inputStream())).useLines { lines ->
            lines.drop(1).mapNotNull { line ->
                val p = line.trim().split(Regex("\\s+"))
                if (p.size < 4) return@mapNotNull null
                val ip = p[0]
                val mac = p[3]
                val device = if (p.size > 5) p[5] else ""
                if (ip.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+")) && mac.contains(":")) {
                    Neighbour(ip, mac.lowercase(), device)
                } else null
            }.toList()
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun localIpv4(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    } catch (e: Exception) {
        null
    }

    fun localIpv4(context: Context): String? {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val dhcp = wifi?.dhcpInfo
        if (dhcp != null && dhcp.ipAddress != 0) {
            val ip = longToIp(dhcp.ipAddress.toLong())
            if (ip != null) return ip
        }
        return localIpv4()
    }

    /** Prefijo /24 típico del Wi-Fi del dispositivo. */
    fun localSubnet(context: Context): String? {
        val ip = localIpv4(context) ?: return null
        val parts = ip.split('.')
        if (parts.size != 4) return null
        return "${parts[0]}.${parts[1]}.${parts[2]}.0/24"
    }

    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
    }

    /** Servidores DNS que entrega el DHCP de la red Wi-Fi actual. */
    fun dnsServers(context: Context): List<String> {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val dhcp = wifi?.dhcpInfo ?: return emptyList()
        return listOf(dhcp.dns1, dhcp.dns2)
            .map { longToIp(it.toLong()) }
            .filterNotNull()
            .filter { it != "0.0.0.0" }
            .distinct()
    }

    /** Puerta de enlace por defecto de la red actual. */
    fun gateway(context: Context): String? {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val dhcp = wifi?.dhcpInfo ?: return null
        val ip = longToIp(dhcp.gateway.toLong()) ?: return null
        return if (ip == "0.0.0.0") null else ip
    }

    /**
     * Envia un datagrama UDP y espera la respuesta. Si el puerto esta cerrado,
     * el sistema responde con ICMP y la espera termina en error.
     */
    fun udpProbe(ip: String, port: Int, payload: ByteArray, timeoutMs: Int): ByteArray? = try {
        java.net.DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMs
            val address = InetAddress.getByName(ip)
            socket.send(java.net.DatagramPacket(payload, payload.size, address, port))
            val buffer = ByteArray(4096)
            val response = java.net.DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            buffer.copyOf(response.length)
        }
    } catch (e: Exception) {
        null
    }

    private fun longToIp(value: Long): String? {
        val v = value and 0xFFFFFFFFL
        return "${(v shr 24) and 0xFF}.${(v shr 16) and 0xFF}.${(v shr 8) and 0xFF}.${v and 0xFF}"
    }
}
