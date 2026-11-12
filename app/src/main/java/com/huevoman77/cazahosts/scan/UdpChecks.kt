package com.huevoman77.cazahosts.scan

import com.huevoman77.cazahosts.core.Net
import com.huevoman77.cazahosts.data.PortFinding
import com.huevoman77.cazahosts.data.Severity
import com.huevoman77.cazahosts.data.VulnFinding
import com.huevoman77.cazahosts.dns.absoluteDnsName
import org.xbill.DNS.ARecord
import org.xbill.DNS.DClass
import org.xbill.DNS.Message
import org.xbill.DNS.Record
import org.xbill.DNS.Section
import org.xbill.DNS.Type

/**
 * Sondeos UDP: algunos servicios relevantes solo escuchan por UDP y el
 * barrido TCP no los ve. Se usan consultas minimas y legitimas, nunca
 * destructivas, y la respuesta confirma que el puerto esta abierto.
 */
object UdpChecks {

    /** Puertos UDP que interesan en una red domestica o de oficina. */
    val ports = listOf(161, 53, 123, 1900)

    /** GetRequest SNMPv1 con comunidad "public" para leer sysDescr. */
    private val SNMP_PUBLIC_GET = byteArrayOf(
        0x30, 0x29, 0x02, 0x01, 0x00, 0x04, 0x06, 0x70, 0x75, 0x62, 0x6C, 0x69, 0x63,
        0xA0.toByte(), 0x1C, 0x02, 0x04, 0x00, 0x00, 0x00, 0x01, 0x02, 0x01, 0x00, 0x02, 0x01, 0x00,
        0x30, 0x0E, 0x30, 0x0C, 0x06, 0x08, 0x2B, 0x06, 0x01, 0x02, 0x01, 0x01, 0x01, 0x00,
        0x05, 0x00
    )

    private val NTP_REQUEST = ByteArray(48).also { it[0] = 0x1B }

    private val SSDP_SEARCH = (
        "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: 239.255.255.250:1900\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 1\r\n" +
            "ST: ssdp:all\r\n\r\n"
        ).toByteArray(Charsets.US_ASCII)

    data class Result(val port: PortFinding, val findings: List<VulnFinding>)

    fun probe(ip: String, port: Int, timeoutMs: Int): Result? = when (port) {
        161 -> snmp(ip, timeoutMs)
        53 -> dns(ip, timeoutMs)
        123 -> ntp(ip, timeoutMs)
        1900 -> ssdp(ip, timeoutMs)
        else -> null
    }

    private fun snmp(ip: String, timeoutMs: Int): Result? {
        val response = Net.udpProbe(ip, 161, SNMP_PUBLIC_GET, timeoutMs) ?: return null
        val errorStatus = snmpErrorStatus(response)
        val accepted = errorStatus == 0
        val description = snmpText(response)
        val findings = mutableListOf<VulnFinding>()
        if (accepted) {
            findings += VulnFinding(
                id = "snmp-public-community",
                severity = Severity.ALTA.name,
                title = "SNMP acepta la comunidad publica",
                detail = "El agente SNMP respondio con la comunidad por defecto 'public'. " +
                    "Con ella se pueden leer las tablas del equipo y, si tiene permisos, cambiar su configuracion.",
                fix = "Cambiar la comunidad por una privada, restringir el SNMP a la red de administracion y usar SNMPv3.",
                port = 161
            )
        }
        return Result(
            port = PortFinding(
                port = 161,
                service = "snmp",
                product = if (accepted) "SNMP agente con comunidad publica" else "SNMP agente",
                version = "SNMPv1",
                banner = description.take(200),
                state = "udp"
            ),
            findings = findings
        )
    }

    /** Lee el error-status del GetResponse recorriendo los TLV del PDU. */
    internal fun snmpErrorStatus(response: ByteArray): Int {
        for (i in response.indices) {
            if (response[i] != 0xA2.toByte()) continue
            var p = i + 2
            repeat(3) {
                if (p >= response.size || response[p] != 0x02.toByte()) return -1
                val len = response[p + 1].toInt() and 0xFF
                if (len > 4 || p + 2 + len > response.size) return -1
                if (it == 2) return response[p + 2].toInt() and 0xFF
                p += 2 + len
            }
            return -1
        }
        return -1
    }

    /** Extrae el texto de sysDescr de la respuesta SNMP. */
    internal fun snmpText(response: ByteArray): String {
        for (i in response.indices) {
            if (response[i] != 0x04.toByte()) continue
            if (i + 1 >= response.size) continue
            val len = response[i + 1].toInt() and 0xFF
            if (len in 3..200 && i + 2 + len <= response.size) {
                val value = String(response, i + 2, len, Charsets.UTF_8)
                if (value.none { it.isISOControl() }) return value.trim()
            }
        }
        return ""
    }

    private fun dns(ip: String, timeoutMs: Int): Result? {
        val name = "consulta${(Math.random() * 100000).toInt()}.com"
        val packet = try {
            Message.newQuery(
                Record.newRecord(
                    absoluteDnsName(name) ?: return null,
                    Type.A,
                    DClass.IN
                )
            ).toWire()
        } catch (e: Exception) {
            return null
        }
        val response = Net.udpProbe(ip, 53, packet, timeoutMs) ?: return null
        val parsed = try {
            Message(response)
        } catch (e: Exception) {
            null
        } ?: return null
        val answers = parsed.getSection(Section.ANSWER)
        val resolved = answers.any { it is ARecord }
        val findings = mutableListOf<VulnFinding>()
        if (resolved) {
            findings += VulnFinding(
                id = "dns-recursive-open",
                severity = Severity.INFO.name,
                title = "El puerto 53 contesta consultas de nombres externos",
                detail = "Este equipo resuelve nombres de Internet para quien pregunte. Si es el router de la red, " +
                    "es el comportamiento normal de su reenviador; si es otro equipo, conviene revisar quien puede usarlo.",
                fix = "Si no debe resolver nombres externos, restringir las consultas a las IP autorizadas o cerrar el puerto 53.",
                port = 53
            )
        }
        return Result(
            port = PortFinding(
                port = 53,
                service = "dns",
                product = "Servidor DNS",
                version = "UDP",
                banner = "${parsed.question?.name?.toString(true) ?: name} -> ${answers.size} respuestas",
                state = "udp"
            ),
            findings = findings
        )
    }

    private fun ntp(ip: String, timeoutMs: Int): Result? {
        val response = Net.udpProbe(ip, 123, NTP_REQUEST, timeoutMs) ?: return null
        if (response.size < 48) return null
        val mode = response[0].toInt() and 0x07
        if (mode != 4) return null
        return Result(
            port = PortFinding(
                port = 123,
                service = "ntp",
                product = "Servidor NTP",
                version = "NTPv${(response[0].toInt() shr 3) and 0x07}",
                banner = "Modo 4 (servidor) confirmado",
                state = "udp"
            ),
            findings = listOf(
                VulnFinding(
                    id = "ntp-open",
                    severity = Severity.INFO.name,
                    title = "Servidor NTP abierto",
                    detail = "El servicio responde consultas de hora. Segun la configuracion, puede permitir consultas de reflection.",
                    fix = "Restringir el NTP a los servidores de tiempo autorizados.",
                    port = 123
                )
            )
        )
    }

    private fun ssdp(ip: String, timeoutMs: Int): Result? {
        val response = Net.udpProbe(ip, 1900, SSDP_SEARCH, timeoutMs) ?: return null
        val text = String(response, Charsets.ISO_8859_1)
        if (!text.contains("HTTP/1.1 200")) return null
        val server = Regex("(?im)^server:\\s*(.+)$").find(text)?.groupValues?.get(1)?.trim() ?: ""
        return Result(
            port = PortFinding(
                port = 1900,
                service = "ssdp",
                product = "Cliente UPnP",
                version = server,
                banner = server.take(120),
                state = "udp"
            ),
            findings = listOf(
                VulnFinding(
                    id = "upnp-discovery-open",
                    severity = Severity.INFO.name,
                    title = "Descubrimiento UPnP activo",
                    detail = "El equipo anuncia sus servicios por SSDP en la red local.",
                    fix = "Desactivar UPnP si no se usa y filtrar los anuncios en el router.",
                    port = 1900
                )
            )
        )
    }
}
