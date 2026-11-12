package com.huevoman77.cazahosts.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CertificateTransparencyTest {

    private val json = """
        [
          {
            "issuer_ca_id": "1640",
            "issuer_name": "C=US, O=Let's Encrypt, CN=R3",
            "common_name": "www.ejemplo.com",
            "name_value": "www.ejemplo.com\nejemplo.com",
            "id": "111",
            "entry_timestamp": "2024-01-01T00:00:00",
            "not_before": "2024-01-01T00:00:00",
            "not_after": "2024-04-01T00:00:00",
            "serial_number": "aa"
          },
          {
            "issuer_ca_id": "1640",
            "issuer_name": "C=US, O=Let's Encrypt, CN=R3",
            "common_name": "*.ejemplo.com",
            "name_value": "*.ejemplo.com",
            "id": "222",
            "entry_timestamp": "2024-02-01T00:00:00",
            "not_before": "2024-02-01T00:00:00",
            "not_after": "2024-05-01T00:00:00",
            "serial_number": "bb"
          },
          {
            "issuer_ca_id": "9999",
            "issuer_name": "Otro emisor",
            "common_name": "www.otrodominio.com",
            "name_value": "www.otrodominio.com",
            "id": "333",
            "entry_timestamp": "2024-03-01T00:00:00",
            "not_before": "2024-03-01T00:00:00",
            "not_after": "2024-06-01T00:00:00",
            "serial_number": "cc"
          }
        ]
    """.trimIndent()

    @Test
    fun extraeLosNombresDelDominio() {
        val entries = CertificateTransparency.parse(json, "ejemplo.com")
        val names = entries.map { it.name }
        assertTrue(names.contains("www.ejemplo.com"))
        assertTrue(names.contains("ejemplo.com"))
        assertEquals(2, names.size)
    }

    @Test
    fun quitaElComodinDelNombre() {
        val entries = CertificateTransparency.parse(json, "ejemplo.com")
        assertTrue(entries.none { it.name.contains("*") })
        assertTrue(entries.any { it.name == "ejemplo.com" })
    }

    @Test
    fun guardaElEmisorDelCertificado() {
        val entries = CertificateTransparency.parse(json, "ejemplo.com")
        assertTrue(entries.all { it.issuer.contains("Let's Encrypt") })
        assertTrue(entries.first().notAfter.startsWith("2024"))
    }

    @Test
    fun aguantaUnJsonRoto() {
        assertTrue(CertificateTransparency.parse("no es json", "ejemplo.com").isEmpty())
        assertTrue(CertificateTransparency.parse("[]", "ejemplo.com").isEmpty())
    }

    private val certspotter = """
        [
          {
            "id": "1",
            "dns_names": ["www.ejemplo.com", "*.ejemplo.com", "www.otrodominio.com"],
            "not_before": "2025-01-01T00:00:00",
            "not_after": "2025-04-01T00:00:00",
            "revoked": false
          },
          {
            "id": "2",
            "dns_names": ["api.ejemplo.com"],
            "not_before": "2025-02-01T00:00:00",
            "not_after": "2025-05-01T00:00:00",
            "revoked": false
          }
        ]
    """.trimIndent()

    @Test
    fun extraeLosNombresDeCertSpotter() {
        val entries = CertificateTransparency.parseCertspotter(certspotter, "ejemplo.com")
        val names = entries.map { it.name }
        assertTrue(names.contains("www.ejemplo.com"))
        assertTrue(names.contains("api.ejemplo.com"))
        assertTrue(names.none { it.contains("otrodominio") })
        assertEquals(3, names.size)
    }

    @Test
    fun aguantaUnCertSpotterRoto() {
        assertTrue(CertificateTransparency.parseCertspotter("no es json", "ejemplo.com").isEmpty())
        assertTrue(CertificateTransparency.parseCertspotter("[]", "ejemplo.com").isEmpty())
    }
}
