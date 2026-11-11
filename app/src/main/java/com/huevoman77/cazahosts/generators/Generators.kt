package com.huevoman77.cazahosts.generators

import java.util.Locale
import kotlin.random.Random

object Generators {

    private val lower = "abcdefghijklmnopqrstuvwxyz"
    private val upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private val digits = "0123456789"
    private val symbols = "!@#$%&*-+=?"
    private val similar = "il1Lo0O"
    private val vowels = "aeiou"
    private val consonants = "bcdfghjklmnpqrstvwxyz"

    private val words = listOf(
        "sol", "luna", "monte", "río", "viento", "bosque", "piedra", "nube", "fuego", "agua",
        "tierra", "estrella", "lobo", "águila", "serpiente", "tigre", "cuervo", "zafiro", "cobre", "perla",
        "roble", "cedro", " Sauce", "campo", "valle", "cabo", "duna", "risco", "faro", "bahía",
        "trigo", "cebada", "olivo", "laurel", "romero", "menta", "salvia", "anís", "clavo", "nuez"
    ).map { it.trim().lowercase(Locale.US) }

    private val suffixes = listOf(
        "com", "net", "org", "io", "dev", "app", "cloud", "local", "lan", "internal",
        "test", "staging", "prod", "mx", "com.mx", "com.ar", "co.uk"
    )

    private val nombres = listOf(
        "carlos", "ana", "luis", "maria", "jose", "sofia", "miguel", "lucia", "david", "valeria",
        "jorge", "camila", "andres", "paula", "ricardo", "elena", "fernando", "guadalupe", "hugo", "isabel"
    )

    private val apellidos = listOf(
        "garcia", "rodriguez", "martinez", "lopez", "hernandez", "gonzalez", "perez", "sanchez",
        "ramirez", "torres", "flores", "rivera", "gomez", "diaz", "cruz", "morales"
    )

    fun passwords(
        count: Int,
        length: Int,
        useUpper: Boolean,
        useDigits: Boolean,
        useSymbols: Boolean,
        avoidSimilar: Boolean,
        pronounceable: Boolean
    ): List<String> {
        val out = mutableListOf<String>()
        val used = mutableSetOf<String>()
        var guard = 0
        while (out.size < count && guard < count * 40) {
            guard++
            val pass = if (pronounceable) pronounceablePassword(length) else randomPassword(length, useUpper, useDigits, useSymbols, avoidSimilar)
            if (used.add(pass)) out += pass
        }
        return out
    }

    private fun randomPassword(length: Int, useUpper: Boolean, useDigits: Boolean, useSymbols: Boolean, avoidSimilar: Boolean): String {
        var alphabet = lower
        if (useUpper) alphabet += upper
        if (useDigits) alphabet += digits
        if (useSymbols) alphabet += symbols
        if (avoidSimilar) {
            alphabet = alphabet.filter { it !in similar }
            if (alphabet.length < 8) alphabet = lower + digits
        }
        val sb = StringBuilder()
        repeat(length) { sb.append(alphabet.random()) }
        if (useUpper && sb.none { it.isUpperCase() }) sb[0] = upper.random()
        if (useDigits && sb.none { it.isDigit() }) sb[1 % length] = digits.random()
        if (useSymbols && sb.none { !it.isLetterOrDigit() }) sb[2 % length] = symbols.random()
        return sb.toString()
    }

    private fun pronounceablePassword(length: Int): String {
        val sb = StringBuilder()
        while (sb.length < length) {
            sb.append(consonants.random())
            if (sb.length < length) sb.append(vowels.random())
        }
        val result = sb.substring(0, length)
        return result.replaceFirstChar { it.uppercase() } + (if (length > 2) digits.random().toString() else "")
    }

    fun subdomains(words: List<String>, suffixList: List<String>, depth: Int): List<String> {
        val base = words.ifEmpty { this.words }
        val outs = mutableListOf<String>()
        for (word in base) {
            for (suffix in suffixList.ifEmpty { this.suffixes }) {
                if (depth <= 1) {
                    outs += "$word.$suffix"
                } else {
                    for (prefix in base.take(12)) {
                        outs += "$prefix.$word.$suffix"
                    }
                }
            }
        }
        return outs.distinct()
    }

    fun ipRanges(seed: Long, count: Int, maxHosts: Int): List<String> {
        val random = Random(seed)
        val out = mutableListOf<String>()
        repeat(count) {
            val a = random.nextInt(1, 224)
            val b = random.nextInt(0, 256)
            val c = random.nextInt(0, 256)
            val size = random.nextInt(8, maxHosts + 1)
            val start = random.nextInt(0, 256 - size)
            out += "$a.$b.$c.$start-$a.$b.$c.${start + size - 1}"
        }
        return out.distinct()
    }

    /** Descompone un CIDR en bloques del prefijo indicado. */
    fun subnetBlocks(cidr: String, newPrefix: Int): List<String> {
        val base = com.huevoman77.cazahosts.core.IpUtils.cidrToRange(cidr) ?: return emptyList()
        if (newPrefix !in 0..32) return emptyList()
        val size = 1L shl (32 - newPrefix)
        val out = mutableListOf<String>()
        var s = base.start
        while (s <= base.end) {
            val e = minOf(s + size - 1, base.end)
            out += "${com.huevoman77.cazahosts.core.IpUtils.longToIpv4(s)}/${newPrefix}"
            if (e == 0xFFFFFFFFL) break
            s = e + 1
        }
        return out
    }

    fun hostnames(count: Int): List<String> {
        val out = mutableListOf<String>()
        repeat(count) {
            out += "${words.random()}-${nombres.random()}"
        }
        return out.distinct()
    }

    fun users(count: Int): List<String> {
        val out = mutableListOf<String>()
        repeat(count) {
            out += "${nombres.random()}.${apellidos.random()}"
        }
        return out.distinct()
    }

    fun passwordsForUsers(users: List<String>, base: List<String>): List<String> {
        val out = mutableListOf<String>()
        for (u in users) {
            out += u
            out += "${u}2024"
            out += "${u}2025"
            out += "${u.replace(".", "")}123"
            out += "${u.substringBefore('.')}"
            out += base.randomOrNull() ?: "1234"
        }
        return out.distinct()
    }

    fun wordlist(count: Int, minLength: Int, maxLength: Int): List<String> {
        val out = mutableListOf<String>()
        val extra = listOf("admin", "password", "clave", "usuario", "servidor", "backup", "correo", "sistema")
        out += extra
        repeat(count) {
            val len = minLength + Random.nextInt(maxLength - minLength + 1)
            out += randomPassword(len, true, true, false, false)
        }
        return out.distinct()
    }
}
