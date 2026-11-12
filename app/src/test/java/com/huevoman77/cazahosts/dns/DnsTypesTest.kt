package com.huevoman77.cazahosts.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsTypesTest {

    @Test
    fun formateaDireccionesIpv6() {
        val bytes = ByteArray(16)
        bytes[15] = 1
        assertEquals("::1", formatIpv6(bytes))

        val full = ByteArray(16) { 0 }
        full[0] = 0x20; full[1] = 0x01
        full[15] = 0x01
        assertEquals("2001::1", formatIpv6(full))

        val middle = ByteArray(16)
        middle[0] = 0x20; middle[1] = 0x01
        middle[10] = 0xFF.toByte(); middle[11] = 0xFE.toByte()
        middle[14] = 0x00; middle[15] = 0x02
        assertEquals("2001::fffe:0:2", formatIpv6(middle))
    }

    @Test
    fun nombraLosTiposDeRegistro() {
        assertEquals("A", DnsType.name(DnsType.A))
        assertEquals("AAAA", DnsType.name(DnsType.AAAA))
        assertEquals("CNAME", DnsType.name(DnsType.CNAME))
        assertEquals("HTTPS", DnsType.name(DnsType.HTTPS))
        assertTrue(DnsType.name(9999).startsWith("TIPO"))
    }

    @Test
    fun normalizaDominio() {
        assertEquals("ejemplo.com", SubdomainEngine.normalizeDomain("https://www.ejemplo.com/path"))
        assertEquals("ejemplo.com", SubdomainEngine.normalizeDomain("  EJEMPLO.com. "))
        assertEquals("ejemplo.com", SubdomainEngine.normalizeDomain("ejemplo.com:8080"))
    }

    /**
     * dnsjava exige nombres absolutos: sin el origen ROOT lanza
     * RelativeNameException y toda consulta DNS falla en silencio.
     */
    @Test
    fun construyeNombresAbsolutos() {
        assertEquals("ejemplo.com", absoluteDnsName("ejemplo.com")?.toString(true))
        assertEquals("EJEMPLO.com", absoluteDnsName("EJEMPLO.com.")?.toString(true))
        assertEquals("www.ejemplo.com", absoluteDnsName(" www.ejemplo.com ")?.toString(true))
        assertTrue(absoluteDnsName("ejemplo.com")!!.isAbsolute())
        assertEquals(null, absoluteDnsName(""))
        assertEquals(null, absoluteDnsName("...  "))
    }
}
