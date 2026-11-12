package com.huevoman77.cazahosts.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xbill.DNS.AAAARecord
import org.xbill.DNS.ARecord
import org.xbill.DNS.CNAMERecord
import org.xbill.DNS.DClass
import org.xbill.DNS.Name
import org.xbill.DNS.Type

class ZoneTransferTest {

    @Test
    fun convierteLosRegistrosDeLaZona() {
        val zona = Name.fromString("ejemplo.com", Name.root)
        val a = ARecord(zona, DClass.IN, 300L, java.net.InetAddress.getByName("1.2.3.4"))
        val cname = CNAMERecord(
            Name.fromString("www.ejemplo.com", Name.root),
            DClass.IN,
            60L,
            Name.fromString("cdn.ejemplo.com", Name.root)
        )
        val aaaa = AAAARecord(
            Name.fromString("v6.ejemplo.com", Name.root),
            DClass.IN,
            120L,
            java.net.InetAddress.getByName("2001:db8::1")
        )

        val entries = ZoneTransfer.toEntries(listOf(a, cname, aaaa))

        assertEquals(3, entries.size)
        assertEquals("ejemplo.com", entries[0].name)
        assertEquals(DnsType.A, entries[0].type)
        assertEquals("1.2.3.4", entries[0].value)
        assertEquals(300, entries[0].ttl)

        assertEquals("www.ejemplo.com", entries[1].name)
        assertEquals(DnsType.CNAME, entries[1].type)

        assertEquals("v6.ejemplo.com", entries[2].name)
        assertEquals(DnsType.AAAA, entries[2].type)

        val zone = ZoneTransfer.Zone("ns1.ejemplo.com", entries)
        assertEquals(listOf("ejemplo.com", "www.ejemplo.com", "v6.ejemplo.com"), zone.hosts)
    }

    @Test
    fun laZonaSinDuplicados() {
        val name = Name.fromString("www.ejemplo.com", Name.root)
        val a = ARecord(name, DClass.IN, 300L, java.net.InetAddress.getByName("1.2.3.4"))
        val a2 = ARecord(name, DClass.IN, 300L, java.net.InetAddress.getByName("5.6.7.8"))
        val zone = ZoneTransfer.Zone("ns1.ejemplo.com", ZoneTransfer.toEntries(listOf(a, a2)))
        assertEquals(1, zone.hosts.size)
        assertTrue(zone.hosts.contains("www.ejemplo.com"))
    }
}
