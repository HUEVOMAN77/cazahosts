package com.huevoman77.cazahosts.dns

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xbill.DNS.AAAARecord
import org.xbill.DNS.ARecord
import org.xbill.DNS.CNAMERecord
import org.xbill.DNS.DClass
import org.xbill.DNS.Flags
import org.xbill.DNS.Message
import org.xbill.DNS.MXRecord
import org.xbill.DNS.NSRecord
import org.xbill.DNS.Name
import org.xbill.DNS.PTRRecord
import org.xbill.DNS.Record
import org.xbill.DNS.Section
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.SOARecord
import org.xbill.DNS.SRVRecord
import org.xbill.DNS.TXTRecord
import org.xbill.DNS.Type
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/**
 * Cliente DNS sobre dnsjava. A diferencia de InetAddress, este cliente permite
 * pedir cualquier tipo de registro, leer la respuesta completa (CNAME, NS, MX,
 * TXT, SOA, SRV) y rotar entre varios resolvers para no depender de uno solo.
 * La libreria se encarga del detalle de bajo nivel: EDNS, reintento por TCP
 * cuando la respuesta viene truncada y validacion del mensaje.
 */
class DnsClient(
    resolvers: List<String>,
    private val timeoutMs: Int = 2000,
    private val retries: Int = 1
) {

    private val cursor = AtomicInteger(0)

    @Volatile
    var cancelled = false

    private val pool: List<SimpleResolver> = buildPool(resolvers)

    suspend fun query(name: String, type: Int): DnsResponse? = withContext(Dispatchers.IO) {
        if (cancelled) return@withContext null
        val target = absoluteDnsName(name) ?: return@withContext null
        val question = try {
            Record.newRecord(target, type, DClass.IN)
        } catch (e: Exception) {
            return@withContext null
        }
        val query = Message.newQuery(question)

        val start = cursor.getAndIncrement()
        for (i in pool.indices) {
            if (cancelled) return@withContext null
            val resolver = pool[(start + i) % pool.size]
            var attempt = 0
            while (attempt <= retries) {
                attempt++
                val message = try {
                    resolver.send(query)
                } catch (e: Exception) {
                    null
                }
                if (message != null) return@withContext toResponse(message, target)
                if (cancelled) return@withContext null
            }
        }
        null
    }

    /** Consulta en paralelo varios tipos sobre el mismo nombre. */
    suspend fun queryMany(name: String, types: List<Int>): Map<Int, DnsResponse> {
        val out = HashMap<Int, DnsResponse>()
        for (type in types) {
            val res = query(name, type)
            if (res != null) out[type] = res
        }
        return out
    }

    private fun buildPool(resolvers: List<String>): List<SimpleResolver> {
        val addresses = resolvers.filter { it.isNotBlank() }.ifEmpty { listOf("1.1.1.1") }
        val out = addresses.mapNotNull { address ->
            try {
                SimpleResolver(address).apply {
                    setTimeout(Duration.ofMillis(timeoutMs.toLong()))
                }
            } catch (e: Exception) {
                null
            }
        }
        if (out.isNotEmpty()) return out
        return try {
            listOf(SimpleResolver().apply { setTimeout(Duration.ofMillis(timeoutMs.toLong())) })
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun toResponse(message: Message, question: Name): DnsResponse {
        val header = message.header
        val type = message.question?.type ?: Type.A
        return DnsResponse(
            id = header.getID(),
            rcode = header.getRcode(),
            truncated = header.getFlag(Flags.TC.toInt()),
            authoritative = header.getFlag(Flags.AA.toInt()),
            questions = listOf(DnsQuestion(question.toString(true), type)),
            answers = message.getSection(Section.ANSWER).map { toRecord(it) },
            authorities = message.getSection(Section.AUTHORITY).map { toRecord(it) },
            additionals = message.getSection(Section.ADDITIONAL)
                .filter { it.type != Type.OPT }
                .map { toRecord(it) }
        )
    }

    private fun toRecord(record: Record): DnsRecord {
        val name = record.name.toString(true)
        val ttl = record.ttl.toInt()
        val data: RData = when (record) {
            is ARecord -> RData.Address(record.address.hostAddress ?: "")
            is AAAARecord -> RData.Address(formatIpv6(record.address.address))
            is CNAMERecord -> RData.Name(record.target.toString(true))
            is NSRecord -> RData.Name(record.target.toString(true))
            is PTRRecord -> RData.Name(record.target.toString(true))
            is MXRecord -> RData.Mail(record.priority, record.target.toString(true))
            is TXTRecord -> RData.Text(record.strings.joinToString(" "))
            is SOARecord -> RData.Zone(
                primary = record.host.toString(true),
                mailbox = record.admin.toString(true),
                serial = record.serial,
                refresh = record.refresh,
                retry = record.retry,
                expire = record.expire,
                minimum = record.minimum
            )
            is SRVRecord -> RData.Service(
                record.priority,
                record.weight,
                record.port,
                record.target.toString(true)
            )
            else -> RData.Blob(record.toString())
        }
        return DnsRecord(name, record.type, ttl, data)
    }
}
