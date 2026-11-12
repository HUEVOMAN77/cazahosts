package com.huevoman77.cazahosts.dns

import java.io.ByteArrayOutputStream

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

class DnsFormatException(message: String) : Exception(message)

/**
 * Codec DNS sobre UDP: arma consultas y lee respuestas crudas.
 * La lectura de nombres soporta punteros de compresion, que es como los
 * resolvers reales compactan los nombres dentro del paquete.
 */
object DnsCodec {

    fun buildQuery(id: Int, name: String, type: Int, recursionDesired: Boolean = true): ByteArray {
        val out = ByteArrayOutputStream(64)
        out.write((id shr 8) and 0xFF)
        out.write(id and 0xFF)
        val flags = if (recursionDesired) 0x0100 else 0x0000
        out.write((flags shr 8) and 0xFF)
        out.write(flags and 0xFF)
        out.write(0); out.write(1) // QDCOUNT
        out.write(0); out.write(0) // ANCOUNT
        out.write(0); out.write(0) // NSCOUNT
        out.write(0); out.write(0) // ARCOUNT
        writeName(out, name)
        out.write((type shr 8) and 0xFF)
        out.write(type and 0xFF)
        out.write(0); out.write(1) // clase IN
        return out.toByteArray()
    }

    private fun writeName(out: ByteArrayOutputStream, name: String) {
        val clean = name.trim().trim('.').ifEmpty { "-" }
        for (label in clean.split('.')) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            val slice = if (bytes.size > 63) bytes.copyOf(63) else bytes
            out.write(slice.size)
            out.write(slice)
        }
        out.write(0)
    }

    fun parseResponse(data: ByteArray): DnsResponse {
        if (data.size < 12) throw DnsFormatException("Respuesta muy corta")
        val c = Cursor(data)
        val id = c.u16()
        val flags = c.u16()
        val qd = c.u16()
        val an = c.u16()
        val ns = c.u16()
        val ar = c.u16()
        val questions = ArrayList<DnsQuestion>(qd)
        repeat(qd) {
            val name = c.name()
            val type = c.u16()
            val klass = c.u16()
            questions.add(DnsQuestion(name, type, klass))
        }
        return DnsResponse(
            id = id,
            rcode = flags and 0x0F,
            truncated = (flags and 0x0200) != 0,
            authoritative = (flags and 0x0400) != 0,
            questions = questions,
            answers = readRecords(c, an),
            authorities = readRecords(c, ns),
            additionals = readRecords(c, ar)
        )
    }

    private fun readRecords(c: Cursor, count: Int): List<DnsRecord> {
        val out = ArrayList<DnsRecord>(count)
        repeat(count) {
            val name = c.name()
            val type = c.u16()
            val klass = c.u16()
            val ttl = c.u32().toInt()
            val length = c.u16()
            val start = c.pos
            c.limit = start + length
            val data = decodeData(c, type)
            c.limit = -1
            val consumed = c.pos - start
            if (consumed < length) c.skip(length - consumed)
            out.add(DnsRecord(name, type, ttl, data))
        }
        return out
    }

    /**
     * Interpreta los datos del registro leyendo directo del cursor, para que los
     * punteros de compresion puedan apuntar a cualquier parte del paquete.
     */
    private fun decodeData(c: Cursor, type: Int): RData = when (type) {
        DnsType.A -> {
            val data = c.read(4)
            RData.Address(
                "${data[0].toInt() and 0xFF}.${data[1].toInt() and 0xFF}." +
                    "${data[2].toInt() and 0xFF}.${data[3].toInt() and 0xFF}"
            )
        }

        DnsType.AAAA -> {
            val data = c.read(16)
            RData.Address(ipv6(data))
        }

        DnsType.NS, DnsType.CNAME, DnsType.PTR -> RData.Name(c.name())

        DnsType.MX -> {
            val pref = c.u16()
            RData.Mail(pref, c.name())
        }

        DnsType.TXT -> {
            val sb = StringBuilder()
            while (c.remaining() > 0) {
                val len = c.u8()
                if (len > c.remaining()) break
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(String(c.read(len), Charsets.UTF_8))
            }
            RData.Text(sb.toString())
        }

        DnsType.SOA -> {
            val primary = c.name()
            val mailbox = c.name()
            RData.Zone(
                primary = primary,
                mailbox = mailbox,
                serial = c.u32(),
                refresh = c.u32(),
                retry = c.u32(),
                expire = c.u32(),
                minimum = c.u32()
            )
        }

        DnsType.SRV -> {
            val priority = c.u16()
            val weight = c.u16()
            val port = c.u16()
            RData.Service(priority, weight, port, c.name())
        }

        else -> RData.Blob(hex(c.read(c.remaining())))
    }

    private fun hex(data: ByteArray): String =
        data.joinToString("") { String.format("%02x", it) }

    fun ipv6(bytes: ByteArray): String {
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
                if (curStart < 0) { curStart = i; curLen = 1 } else curLen++
                if (curLen > bestLen) { bestLen = curLen; bestStart = curStart }
            } else {
                curStart = -1
                curLen = 0
            }
        }
        if (bestLen < 2) { bestStart = -1; bestLen = 0 }
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

    private class Cursor(val data: ByteArray) {
        var pos = 0

        /** Limite de lectura para los datos de un registro; -1 = sin limite. */
        var limit = -1

        private val bound: Int get() = if (limit >= 0) limit else data.size

        fun remaining(): Int = (bound - pos).coerceAtLeast(0)

        fun skip(n: Int) {
            if (n <= 0) return
            if (pos + n > data.size) throw DnsFormatException("Datos truncados")
            pos += n
        }

        fun u8(): Int {
            if (pos >= bound) throw DnsFormatException("Datos truncados")
            return data[pos++].toInt() and 0xFF
        }

        fun u16(): Int = (u8() shl 8) or u8()

        fun u32(): Long = (u16().toLong() shl 16) or u16().toLong()

        fun read(n: Int): ByteArray {
            if (n < 0 || pos + n > bound) throw DnsFormatException("Datos truncados")
            val out = data.copyOfRange(pos, pos + n)
            pos += n
            return out
        }

        /**
         * Nombre con soporte de punteros de compresion: si aparece un puntero
         * (dos bytes con los bits altos en 1), salta a la posicion indicada y
         * sigue leyendo desde ahi sin mover el cursor principal.
         */
        fun name(): String {
            val sb = StringBuilder()
            var offset = pos
            var resume = -1
            var guard = 0
            while (guard++ < 128) {
                val cap = if (resume < 0) bound else data.size
                if (offset >= cap) break
                val len = data[offset].toInt() and 0xFF
                if (len == 0) { offset++; break }
                if ((len and 0xC0) == 0xC0) {
                    if (offset + 1 >= data.size) break
                    val ptr = ((len and 0x3F) shl 8) or (data[offset + 1].toInt() and 0xFF)
                    if (resume < 0) resume = offset + 2
                    offset = ptr
                    continue
                }
                if ((len and 0xC0) != 0) break
                val start = offset + 1
                if (start + len > cap) break
                if (sb.isNotEmpty()) sb.append('.')
                sb.append(String(data, start, len, Charsets.US_ASCII))
                offset = start + len
            }
            pos = if (resume >= 0) resume else offset
            return sb.toString()
        }
    }
}
