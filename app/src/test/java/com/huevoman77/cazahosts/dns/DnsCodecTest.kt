package com.huevoman77.cazahosts.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class DnsCodecTest {

    private fun header(id: Int, flags: Int, qd: Int, an: Int, ns: Int, ar: Int): ByteArrayOutputStream {
        val out = ByteArrayOutputStream()
        out.write((id shr 8) and 0xFF); out.write(id and 0xFF)
        out.write((flags shr 8) and 0xFF); out.write(flags and 0xFF)
        out.write((qd shr 8) and 0xFF); out.write(qd and 0xFF)
        out.write((an shr 8) and 0xFF); out.write(an and 0xFF)
        out.write((ns shr 8) and 0xFF); out.write(ns and 0xFF)
        out.write((ar shr 8) and 0xFF); out.write(ar and 0xFF)
        return out
    }

    private fun label(out: ByteArrayOutputStream, text: String) {
        out.write(text.length)
        out.write(text.toByteArray(Charsets.US_ASCII))
    }

    private fun question(out: ByteArrayOutputStream, name: String, type: Int) {
        for (part in name.split('.')) label(out, part)
        out.write(0)
        out.write((type shr 8) and 0xFF); out.write(type and 0xFF)
        out.write(0); out.write(1)
    }

    @Test
    fun armaConsultaValida() {
        val query = DnsCodec.buildQuery(0x1234, "www.ejemplo.com", DnsType.A)
        assertEquals(0x1234, ((query[0].toInt() and 0xFF) shl 8) or (query[1].toInt() and 0xFF))
        assertEquals(1, ((query[4].toInt() and 0xFF) shl 8) or (query[5].toInt() and 0xFF))
        assertEquals(0x01, query[2].toInt() and 0xFF)
        assertTrue(query.size > 20)
    }

    @Test
    fun leeRespuestaConPunteroDeCompresion() {
        // el nombre de la respuesta es un puntero al inicio de la pregunta
        val out = header(0x1234, 0x8180, 1, 1, 0, 0)
        question(out, "www.ejemplo.com", DnsType.A)
        out.write(0xC0); out.write(0x0C)
        out.write(0x00); out.write(DnsType.A)
        out.write(0x00); out.write(0x01)
        out.write(0x00); out.write(0x00); out.write(0x00); out.write(0x3C)
        out.write(0x00); out.write(0x04)
        out.write(93); out.write(184); out.write(216); out.write(34)

        val response = DnsCodec.parseResponse(out.toByteArray())
        assertEquals(0x1234, response.id)
        assertTrue(response.success)
        assertEquals(1, response.answers.size)
        assertEquals("www.ejemplo.com", response.answers[0].name)
        val data = response.answers[0].data
        assertTrue(data is RData.Address)
        assertEquals("93.184.216.34", (data as RData.Address).ip)
    }

    @Test
    fun leeAliasCname() {
        val out = header(1, 0x8180, 1, 1, 0, 0)
        question(out, "web.ejemplo.com", DnsType.CNAME)
        // puntero al nombre de la pregunta mas el destino en texto plano
        out.write(0xC0); out.write(0x0C)
        out.write(0x00); out.write(DnsType.CNAME)
        out.write(0x00); out.write(0x01)
        out.write(0x00); out.write(0x00); out.write(0x00); out.write(0x3C)
        out.write(0x00); out.write(0x11)
        label(out, "cdn")
        label(out, "ejemplo")
        label(out, "com")
        out.write(0)

        val response = DnsCodec.parseResponse(out.toByteArray())
        assertEquals(1, response.answers.size)
        val data = response.answers[0].data
        assertTrue(data is RData.Name)
        assertEquals("cdn.ejemplo.com", (data as RData.Name).target)
    }

    @Test
    fun detectaCodigoDeError() {
        val out = header(1, 0x8183, 1, 0, 0, 0)
        question(out, "noexiste.ejemplo.com", DnsType.A)
        val response = DnsCodec.parseResponse(out.toByteArray())
        assertEquals(3, response.rcode)
        assertTrue(!response.success)
    }

    @Test
    fun formateaDireccionesIpv6() {
        val bytes = ByteArray(16)
        bytes[15] = 1
        assertEquals("::1", DnsCodec.ipv6(bytes))

        val full = ByteArray(16) { 0 }
        full[0] = 0x20; full[1] = 0x01
        full[15] = 0x01
        assertEquals("2001::1", DnsCodec.ipv6(full))

        val middle = ByteArray(16)
        middle[0] = 0x20; middle[1] = 0x01
        middle[10] = 0xFF.toByte(); middle[11] = 0xFE.toByte()
        middle[14] = 0x00; middle[15] = 0x02
        assertEquals("2001::fffe:0:2", DnsCodec.ipv6(middle))
    }

    @Test
    fun normalizaDominio() {
        assertEquals("ejemplo.com", SubdomainEngine.normalizeDomain("https://www.ejemplo.com/path"))
        assertEquals("ejemplo.com", SubdomainEngine.normalizeDomain("  EJEMPLO.com. "))
        assertEquals("ejemplo.com", SubdomainEngine.normalizeDomain("ejemplo.com:8080"))
    }
}
