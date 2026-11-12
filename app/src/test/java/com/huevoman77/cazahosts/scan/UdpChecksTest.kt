package com.huevoman77.cazahosts.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UdpChecksTest {

    /** GetResponse SNMP con error-status y sysDescr legibles. */
    private fun getResponse(errorStatus: Int, description: String): ByteArray {
        val out = mutableListOf<Byte>()
        fun byte(value: Int) {
            out.add(value.toByte())
        }
        fun int(value: Int) {
            byte(0x02); byte(0x01); byte(value)
        }
        byte(0xA2)
        byte(0x00) // longitud del PDU, se ajusta al final
        int(0)     // version
        byte(0x02); byte(0x04); byte(0x00); byte(0x00); byte(0x00); byte(0x01) // request-id
        int(errorStatus) // error-status
        int(0)     // error-index
        byte(0x30); byte(0x00) // varbinds
        byte(0x30); byte(0x00) // varbind
        byte(0x06); byte(0x08) // OID sysDescr.0
        byte(0x2B); byte(0x06); byte(0x01); byte(0x02)
        byte(0x01); byte(0x01); byte(0x01); byte(0x00)
        val desc = description.toByteArray(Charsets.UTF_8)
        byte(0x04); byte(desc.size)
        out.addAll(desc.asList())
        out[1] = (out.size - 2).toByte()
        return out.toByteArray()
    }

    @Test
    fun leeElErrorStatusDelGetResponse() {
        assertEquals(0, UdpChecks.snmpErrorStatus(getResponse(0, "Router Linux")))
        assertEquals(2, UdpChecks.snmpErrorStatus(getResponse(2, "Router Linux")))
    }

    @Test
    fun noConfundeElRequestIdConElErrorStatus() {
        // El request-id vale 1 y el error-status 0: un offset corrido daria 1.
        assertEquals(0, UdpChecks.snmpErrorStatus(getResponse(0, "Router Linux")))
    }

    @Test
    fun extraeLaDescripcionDelEquipo() {
        assertEquals("Router Linux", UdpChecks.snmpText(getResponse(0, "Router Linux")))
    }

    @Test
    fun devuelveCadenaVaciaSiNoHayRespuesta() {
        assertEquals(-1, UdpChecks.snmpErrorStatus(ByteArray(0)))
        assertEquals("", UdpChecks.snmpText(ByteArray(0)))
    }

    @Test
    fun incluyeLosPuertosUdpImportantes() {
        assertTrue(UdpChecks.ports.containsAll(listOf(53, 123, 161, 1900)))
    }
}
