package com.huevoman77.cazahosts.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebClassifierTest {

    @Test
    fun comparaNombresExactos() {
        assertTrue(WebClassifier.matchesName("www.ejemplo.com", "www.ejemplo.com"))
        assertFalse(WebClassifier.matchesName("www.ejemplo.com", "www.ejemplo.org"))
    }

    @Test
    fun aceptaElComodinDeUnSoloNivel() {
        assertTrue(WebClassifier.matchesName("api.ejemplo.com", "*.ejemplo.com"))
        assertFalse(WebClassifier.matchesName("a.b.ejemplo.com", "*.ejemplo.com"))
        assertFalse(WebClassifier.matchesName("ejemplo.com", "*.ejemplo.com"))
    }

    @Test
    fun ignoraMayusculasYPuntoFinal() {
        assertTrue(WebClassifier.matchesName("WWW.Ejemplo.COM.", "www.ejemplo.com"))
    }

    @Test
    fun sacaElNombreComunDelCertificado() {
        assertEquals("www.ejemplo.com", WebClassifier.commonName("CN=www.ejemplo.com, O=Empresa, C=US"))
        assertEquals("www.ejemplo.com", WebClassifier.commonName("CN=www.ejemplo.com"))
        assertEquals("", WebClassifier.commonName("O=Empresa, C=US"))
    }

    @Test
    fun leeElTituloDeLaPagina() {
        assertEquals("Portal", WebClassifier.title("<html><head><title>Portal</title></head></html>"))
        assertEquals("", WebClassifier.title("<html><body>sin titulo</body></html>"))
    }

    @Test
    fun elEstadoHttpRepresentativoPrefiereElTls() {
        val report = WebClassifier.Report(
            directStatus = 301,
            directOk = true,
            sniStatus = 200,
            sniOk = true
        )
        assertEquals(200, report.httpStatus)
        assertTrue(report.anyOk)
    }

    @Test
    fun elEstadoHttpCaeAlPuertoOchoCuandoNoHayTls() {
        val report = WebClassifier.Report(directStatus = 403, directOk = true)
        assertEquals(403, report.httpStatus)
        assertTrue(report.directOk)
        assertFalse(report.sniOk)
    }
}
