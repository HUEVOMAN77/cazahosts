package com.huevoman77.cazahosts.generators

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratorsTest {

    @Test
    fun generaCantidadPedidaDeClaves() {
        val out = Generators.passwords(
            count = 10,
            length = 12,
            useUpper = true,
            useDigits = true,
            useSymbols = true,
            avoidSimilar = false,
            pronounceable = false
        )
        assertEquals(10, out.size)
        assertTrue(out.all { it.length == 12 })
        assertEquals(out.size, out.distinct().size)
    }

    @Test
    fun respetaLongitudMinima() {
        val out = Generators.passwords(5, 6, true, true, false, false, false)
        assertTrue(out.all { it.length >= 6 })
    }

    @Test
    fun generaSubdominiosConNiveles() {
        val out = Generators.subdomains(
            words = listOf("dev", "api"),
            suffixList = listOf("com"),
            depth = 1
        )
        assertTrue(out.contains("dev.com"))
        assertTrue(out.contains("api.com"))
    }

    @Test
    fun generaSubdominiosConDosNiveles() {
        val out = Generators.subdomains(
            words = listOf("dev", "api"),
            suffixList = listOf("com"),
            depth = 2
        )
        assertTrue(out.any { it.count { c -> c == '.' } == 2 })
    }

    @Test
    fun generaRangosDeIp() {
        val out = Generators.ipRanges(seed = 42L, count = 3, maxHosts = 254)
        assertEquals(3, out.size)
        assertTrue(out.all { it.contains('-') })
    }

    @Test
    fun generaBloquesDeSubred() {
        val out = Generators.subnetBlocks("192.168.1.0/24", 26)
        assertEquals(4, out.size)
        assertTrue(out.contains("192.168.1.0/26"))
        assertTrue(out.contains("192.168.1.192/26"))
    }

    @Test
    fun generaListaDePalabras() {
        val out = Generators.wordlist(count = 20, minLength = 6, maxLength = 10)
        assertTrue(out.size >= 20)
        assertTrue(out.all { it.length in 6..10 || it in listOf("admin", "password", "clave", "usuario", "servidor", "backup", "correo", "sistema") })
    }
}
