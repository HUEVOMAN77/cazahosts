package com.huevoman77.cazahosts.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IpUtilsTest {

    @Test
    fun convierteTextoALargo() {
        assertEquals(0L, IpUtils.ipv4ToLong("0.0.0.0"))
        assertEquals(167772161L, IpUtils.ipv4ToLong("10.0.0.1"))
        assertEquals(4294967295L, IpUtils.ipv4ToLong("255.255.255.255"))
    }

    @Test
    fun rechazaTextosInvalidos() {
        assertNull(IpUtils.ipv4ToLong("192.168.1"))
        assertNull(IpUtils.ipv4ToLong("192.168.1.256"))
        assertNull(IpUtils.ipv4ToLong("192.168.01.1"))
        assertNull(IpUtils.ipv4ToLong(""))
    }

    @Test
    fun convierteLargoATexto() {
        assertEquals("10.0.0.1", IpUtils.longToIpv4(167772161L))
        assertEquals("255.255.255.255", IpUtils.longToIpv4(4294967295L))
        assertEquals("0.0.0.0", IpUtils.longToIpv4(0L))
    }

    @Test
    fun detectaRedesPrivadas() {
        assertTrue(IpUtils.isPrivate(IpUtils.ipv4ToLong("192.168.1.10")!!))
        assertTrue(IpUtils.isPrivate(IpUtils.ipv4ToLong("10.1.2.3")!!))
        assertTrue(IpUtils.isPrivate(IpUtils.ipv4ToLong("172.16.0.1")!!))
        assertTrue(!IpUtils.isPrivate(IpUtils.ipv4ToLong("8.8.8.8")!!))
    }

    @Test
    fun convierteCidrEnRango() {
        val range = IpUtils.cidrToRange("192.168.1.0/24")
        assertEquals(256L, range?.size)
        assertEquals("192.168.1.0", IpUtils.longToIpv4(range!!.start))
        assertEquals("192.168.1.255", IpUtils.longToIpv4(range.end))
    }

    @Test
    fun rechazaCidrInvalido() {
        assertNull(IpUtils.cidrToRange("192.168.1.0/33"))
        assertNull(IpUtils.cidrToRange("192.168.1.0/abc"))
        assertNull(IpUtils.cidrToRange("192.168.1.0"))
    }

    @Test
    fun interpretaRangoDePuertos() {
        val range = IpUtils.rangeToRange("10.0.0.1", "254")
        assertEquals(254L, range?.size)
        assertEquals("10.0.0.254", IpUtils.longToIpv4(range!!.end))
        assertNull(IpUtils.rangeToRange("10.0.0.5", "10.0.0.1"))
    }

    @Test
    fun parseaListaDeObjetivos() {
        val parsed = IpUtils.parseTargets("192.168.1.0/24, 10.0.0.1-254, 10.0.0.*, 8.8.8.8")
        assertEquals(4, parsed.size)
        assertEquals(256L, parsed[0].size)
        assertEquals(254L, parsed[1].size)
        assertEquals(256L, parsed[2].size)
        assertEquals(1L, parsed[3].size)
    }

    @Test
    fun divideRangoEnTrozos() {
        val range = IpUtils.cidrToRange("192.168.1.0/24")!!
        val trozos = IpUtils.chunk(range, 64)
        assertEquals(4, trozos.size)
        assertEquals(64L, trozos[0].size)
        assertEquals("192.168.1.192", IpUtils.longToIpv4(trozos[3].start))
    }
}
