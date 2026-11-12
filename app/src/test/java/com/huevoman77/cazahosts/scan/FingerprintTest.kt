package com.huevoman77.cazahosts.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerprintTest {

    @Test
    fun reconoceServiciosPorPuerto() {
        assertEquals("http", Fingerprint.serviceName(80))
        assertEquals("https", Fingerprint.serviceName(443))
        assertEquals("ssh", Fingerprint.serviceName(22))
        assertEquals("desconocido", Fingerprint.serviceName(65533))
    }

    @Test
    fun interpretaListaDePuertos() {
        assertEquals(listOf(22, 80, 443), Fingerprint.parsePorts("22, 80,443"))
        assertEquals(listOf(8000, 8001, 8002), Fingerprint.parsePorts("8000-8002"))
    }

    @Test
    fun identificaProductoPorBanner() {
        val info = Fingerprint.identify(21, "220 ProFTPD 1.3.5 Server")
        assertEquals("ftp", info.service)
        assertTrue(info.product.contains("ProFTPD", ignoreCase = true))
    }

    @Test
    fun identificaVersionDeServidorWeb() {
        val info = Fingerprint.identify(80, "HTTP/1.1 200 OK\r\nServer: nginx/1.18.0\r\n\r\n")
        assertTrue(info.product.contains("nginx", ignoreCase = true))
        assertTrue(info.version.contains("1.18"))
    }

    @Test
    fun expandePreajustesDePuertos() {
        val top = Fingerprint.portsForPreset("top", "")
        assertTrue(top.size > 10)
        assertTrue(top.contains(80))
        val todos = Fingerprint.portsForPreset("todos", "")
        assertEquals(65535, todos.size)
        val propio = Fingerprint.portsForPreset("personalizado", "22, 8080")
        assertEquals(listOf(22, 8080), propio)
    }
}
