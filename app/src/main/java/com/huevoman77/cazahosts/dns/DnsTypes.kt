package com.huevoman77.cazahosts.dns

import org.xbill.DNS.Name

/**
 * Convierte un nombre de texto en un nombre absoluto de DNS. dnsjava exige el
 * punto final: sin el origen ROOT lanza RelativeNameException y la consulta
 * falla en silencio.
 */
internal fun absoluteDnsName(name: String): Name? = try {
    Name.fromString(name.trim().trim('.'), Name.root)
} catch (e: Exception) {
    null
}

/** Tipos de registro DNS que entiende el motor. */
object DnsType {
    const val A = 1
    const val NS = 2
    const val CNAME = 5
    const val SOA = 6
    const val PTR = 12
    const val MX = 15
    const val TXT = 16
    const val AAAA = 28
    const val SRV = 33
    const val HTTPS = 65

    fun name(type: Int): String = when (type) {
        A -> "A"
        NS -> "NS"
        CNAME -> "CNAME"
        SOA -> "SOA"
        PTR -> "PTR"
        MX -> "MX"
        TXT -> "TXT"
        AAAA -> "AAAA"
        SRV -> "SRV"
        HTTPS -> "HTTPS"
        else -> "TIPO$type"
    }
}

/** Datos de un registro ya interpretados según su tipo. */
sealed interface RData {
    data class Address(val ip: String) : RData
    data class Name(val target: String) : RData
    data class Mail(val preference: Int, val exchange: String) : RData
    data class Text(val text: String) : RData
    data class Service(val priority: Int, val weight: Int, val port: Int, val target: String) : RData
    data class Zone(
        val primary: String,
        val mailbox: String,
        val serial: Long,
        val refresh: Long,
        val retry: Long,
        val expire: Long,
        val minimum: Long
    ) : RData
    data class Blob(val hex: String) : RData
}

data class DnsRecord(val name: String, val type: Int, val ttl: Int, val data: RData)

data class DnsQuestion(val name: String, val type: Int, val klass: Int = 1)

data class DnsResponse(
    val id: Int,
    val rcode: Int,
    val truncated: Boolean,
    val authoritative: Boolean,
    val questions: List<DnsQuestion> = emptyList(),
    val answers: List<DnsRecord> = emptyList(),
    val authorities: List<DnsRecord> = emptyList(),
    val additionals: List<DnsRecord> = emptyList()
) {
    val success: Boolean get() = rcode == 0

    fun errorText(): String = when (rcode) {
        0 -> ""
        1 -> "Error de formato"
        2 -> "Fallo del servidor"
        3 -> "Nombre no existe (NXDOMAIN)"
        4 -> "No implementado"
        5 -> "Rechazado"
        else -> "Error $rcode"
    }
}

/**
 * Escribe una IPv6 en forma corta (2001:db8::1) a partir de sus 16 bytes.
 * Java siempre devuelve la forma larga, que es mas dificil de leer.
 */
fun formatIpv6(bytes: ByteArray): String {
    if (bytes.size != 16) return bytes.joinToString("") { String.format("%02x", it) }
    val groups = IntArray(8)
    for (i in 0 until 8) {
        groups[i] = ((bytes[i * 2].toInt() and 0xFF) shl 8) or (bytes[i * 2 + 1].toInt() and 0xFF)
    }
    var bestStart = -1
    var bestLen = 0
    var curStart = -1
    var curLen = 0
    for (i in 0 until 8) {
        if (groups[i] == 0) {
            if (curStart < 0) {
                curStart = i
                curLen = 1
            } else {
                curLen++
            }
            if (curLen > bestLen) {
                bestLen = curLen
                bestStart = curStart
            }
        } else {
            curStart = -1
            curLen = 0
        }
    }
    if (bestLen < 2) {
        bestStart = -1
        bestLen = 0
    }
    if (bestStart >= 0 && bestLen == 8) return "::"
    val parts = ArrayList<String>(9)
    var i = 0
    while (i < 8) {
        if (bestStart >= 0 && i == bestStart) {
            parts.add("")
            i += bestLen
            continue
        }
        parts.add(Integer.toHexString(groups[i]))
        i++
    }
    if (bestStart == 0) parts.add(0, "")
    if (bestStart >= 0 && bestStart + bestLen == 8) parts.add("")
    return parts.joinToString(":")
}
