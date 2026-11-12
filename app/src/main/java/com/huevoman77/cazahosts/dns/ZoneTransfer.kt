package com.huevoman77.cazahosts.dns

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xbill.DNS.ARecord
import org.xbill.DNS.Record
import org.xbill.DNS.ZoneTransferIn
import java.time.Duration

/**
 * Transferencia de zona completa (AXFR). Cuando el servidor de nombres del
 * dominio publica la zona, se obtienen de una sola vez todos los nombres que
 * existen, incluidos los que nunca aparecen en un diccionario.
 */
object ZoneTransfer {

    /** Tope de registros que se conservan: hay zonas publicas con miles de lineas. */
    private const val MAX_ENTRIES = 5000

    data class Entry(val name: String, val type: Int, val ttl: Int, val value: String)

    data class Zone(val server: String, val entries: List<Entry>) {
        /** Nombres de host definidos en la zona, sin duplicados. */
        val hosts: List<String>
            get() = entries.asSequence()
                .map { it.name }
                .filter { it.isNotEmpty() }
                .distinct()
                .toList()
    }

    /**
     * Intenta descargar la zona desde cada servidor de nombres. Devuelve null
     * si ninguno la publica, que es lo habitual en la mayoria de los dominios.
     */
    suspend fun transfer(domain: String, nameservers: List<String>, timeoutMs: Int): Zone? =
        withContext(Dispatchers.IO) {
            val zone = absoluteDnsName(domain) ?: return@withContext null
            for (server in nameservers) {
                val xfr = try {
                    ZoneTransferIn.newAXFR(zone, server, 53, null).apply {
                        setTimeout(Duration.ofMillis(timeoutMs.toLong()))
                    }
                } catch (e: Exception) {
                    continue
                }
                val records = try {
                    xfr.run()
                    xfr.getAXFR()
                } catch (e: Exception) {
                    null
                } ?: continue
                if (records.isEmpty()) continue
                return@withContext Zone(server, toEntries(records))
            }
            null
        }

    /** Convierte los registros de la zona en entradas legibles. */
    internal fun toEntries(records: List<Record>): List<Entry> =
        records.take(MAX_ENTRIES).map { toEntry(it) }

    private fun toEntry(record: Record): Entry {
        val value = when (record) {
            is ARecord -> record.address.hostAddress ?: ""
            else -> record.toString()
        }
        return Entry(record.name.toString(true), record.type, record.ttl.toInt(), value)
    }
}
