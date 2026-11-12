package com.huevoman77.cazahosts.vuln

import com.huevoman77.cazahosts.data.PortFinding
import com.huevoman77.cazahosts.data.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VulnDbTest {

    @Test
    fun detectaVersionVulnerable() {
        val finding = PortFinding(
            port = 21,
            service = "ftp",
            product = "vsftpd",
            version = "2.3.4",
            banner = "220 (vsFTPd 2.3.4)"
        )
        val result = VulnDb.evaluate(finding)
        assertTrue(result.isNotEmpty())
        assertTrue(result.any { it.cve == "CVE-2011-2523" })
        assertEquals(Severity.CRITICA, result.first().severityEnum)
    }

    @Test
    fun noMarcaVersionesSeguras() {
        val finding = PortFinding(
            port = 21,
            service = "ftp",
            product = "vsftpd",
            version = "3.0.3",
            banner = "220 (vsFTPd 3.0.3)"
        )
        assertTrue(VulnDb.evaluate(finding).none { it.id == "vsftpd-2.3.4" })
    }

    @Test
    fun detectaServicioSensiblePorPuerto() {
        val finding = PortFinding(port = 2375, service = "docker-api")
        val result = VulnDb.evaluate(finding)
        assertTrue(result.any { it.id.contains("docker") })
    }

    @Test
    fun identificaVersionDeOpenSsh() {
        val finding = PortFinding(
            port = 22,
            service = "ssh",
            product = "OpenSSH",
            version = "8.9p1",
            banner = "SSH-2.0-OpenSSH_8.9p1"
        )
        assertTrue(VulnDb.evaluate(finding).any { it.cve == "CVE-2024-6387" })
    }
}
