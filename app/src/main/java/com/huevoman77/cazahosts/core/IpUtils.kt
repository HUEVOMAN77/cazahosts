package com.huevoman77.cazahosts.core

/** Rango de direcciones IPv4 expressed como longs (0..4294967295). */
data class TargetRange(val start: Long, val end: Long, val label: String) {
    val size: Long get() = end - start + 1
    fun ipAt(offset: Long): String = IpUtils.longToIpv4(start + offset)
}

object IpUtils {

    private const val MASK32 = 0xFFFFFFFFL

    fun ipv4ToLong(text: String): Long? {
        val parts = text.trim().split('.')
        if (parts.size != 4) return null
        var value = 0L
        for (part in parts) {
            if (part.isEmpty() || part.length > 3) return null
            if (part.length > 1 && part[0] == '0') return null
            val n = part.toIntOrNull() ?: return null
            if (n !in 0..255) return null
            value = (value shl 8) or n.toLong()
        }
        return value
    }

    fun longToIpv4(value: Long): String {
        val b = ByteArray(4) { ((value shr (24 - it * 8)) and 0xFF).toByte() }
        return b.joinToString(".") { (it.toInt() and 0xFF).toString() }
    }

    fun isPrivate(value: Long): Boolean {
        val o1 = ((value shr 24) and 0xFF).toInt()
        val o2 = ((value shr 16) and 0xFF).toInt()
        return o1 == 10 ||
            (o1 == 192 && o2 == 168) ||
            (o1 == 172 && o2 in 16..31) ||
            o1 == 127 ||
            (o1 == 169 && o2 == 254)
    }

    fun isRoutablePublic(value: Long): Boolean = !isPrivate(value) && value != 0L

    /** Convierte una máscara en prefijo, o null si no es válida. */
    fun maskToPrefix(mask: Long): Int? {
        if (mask and MASK32 != mask) return null
        val prefix = Integer.bitCount(mask.toInt())
        val inverted = mask.inv() and MASK32
        val expected = if (prefix == 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and MASK32
        return if (inverted == expected.inv() and MASK32 || prefix == 0) prefix else null
    }

    fun cidrToRange(text: String): TargetRange? {
        val parts = text.trim().split('/')
        if (parts.size != 2) return null
        val base = ipv4ToLong(parts[0]) ?: return null
        val bits = parts[1].toIntOrNull() ?: return null
        if (bits !in 0..32) return null
        val mask = if (bits == 0) 0L else (0xFFFFFFFFL shl (32 - bits)) and MASK32
        val start = base and mask
        val end = start or (mask.inv() and MASK32)
        return TargetRange(start, end, "${parts[0]}/$bits")
    }

    fun rangeToRange(startText: String, endText: String): TargetRange? {
        val start = ipv4ToLong(startText) ?: return null
        val endLong = if (endText.contains('.')) {
            ipv4ToLong(endText) ?: return null
        } else {
            val n = endText.toIntOrNull() ?: return null
            if (n !in 0..255) return null
            (start and 0xFFFFFF00L) or n.toLong()
        }
        if (endLong < start) return null
        return TargetRange(start, endLong, "$startText-$endText")
    }

    /**
     * Acepta: CIDR, rango (A-B), comodín (10.0.0.*), IP suelta y listas separadas por coma.
     */
    fun parseTargets(input: String): List<TargetRange> {
        val cleaned = input.replace(Regex("\\s*-\\s*"), "-")
        val out = mutableListOf<TargetRange>()
        cleaned.split(',', ';', '\n', ' ', '\t')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { token -> parseToken(token)?.let(out::add) }
        return out
    }

    private fun parseToken(token: String): TargetRange? {
        if (token.contains('/')) return cidrToRange(token)
        if (token.contains('*')) {
            val parts = token.split('*')
            if (parts.size != 2) return null
            val head = parts[0].trimEnd('.')
            val prefix = if (head.isEmpty()) "" else "$head."
            val start = ipv4ToLong("$prefix" + "0") ?: return null
            val end = ipv4ToLong("$prefix" + "255") ?: return null
            return TargetRange(start, end, token)
        }
        if (token.contains('-')) {
            val a = token.substringBefore('-')
            val b = token.substringAfter('-')
            return rangeToRange(a, b)
        }
        val single = ipv4ToLong(token) ?: return null
        return TargetRange(single, single, token)
    }

    /** Divide un rango en trozos de [chunk] direcciones para barridos en paralelo. */
    fun chunk(range: TargetRange, chunk: Long): List<TargetRange> {
        if (range.size <= chunk) return listOf(range)
        val out = mutableListOf<TargetRange>()
        var s = range.start
        while (s <= range.end) {
            val e = minOf(s + chunk - 1, range.end)
            out += TargetRange(s, e, range.label)
            if (e == MASK32) break
            s = e + 1
        }
        return out
    }
}
