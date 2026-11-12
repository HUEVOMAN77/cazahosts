package com.huevoman77.cazahosts.dns

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

/**
 * Cliente DNS sobre UDP crudo. A diferencia de InetAddress, este cliente permite
 * pedir cualquier tipo de registro, leer la respuesta completa (CNAME, NS, MX,
 * TXT, SOA, SRV) y rotar entre varios resolvers para no depender de uno solo.
 */
class DnsClient(
    private val resolvers: List<String>,
    private val timeoutMs: Int = 2000,
    private val retries: Int = 1
) {

    private val cursor = AtomicInteger(0)

    private val pool: List<String> = resolvers.filter { it.isNotBlank() }.ifEmpty { listOf("1.1.1.1") }

    suspend fun query(name: String, type: Int): DnsResponse? = withContext(Dispatchers.IO) {
        val id = (Math.random() * 0xFFFF).toInt() and 0xFFFF
        val packet = DnsCodec.buildQuery(id, name, type)
        val resolver = pool[cursor.getAndIncrement() % pool.size]
        val raw = exchange(resolver, packet)
        if (raw == null) return@withContext null
        val response = try {
            DnsCodec.parseResponse(raw)
        } catch (e: Exception) {
            null
        } ?: return@withContext null
        if (response.id != id) return@withContext null
        if (response.truncated) {
            val tcp = exchangeTcp(resolver, packet)
            if (tcp == null) return@withContext response
            try {
                val full = DnsCodec.parseResponse(tcp)
                if (full.id == id) full else response
            } catch (e: Exception) {
                response
            }
        } else {
            response
        }
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

    private fun exchange(resolver: String, packet: ByteArray): ByteArray? {
        var attempt = 0
        while (attempt <= retries) {
            attempt++
            try {
                DatagramSocket().use { socket ->
                    socket.soTimeout = timeoutMs
                    val address = InetAddress.getByName(resolver)
                    socket.send(DatagramPacket(packet, packet.size, address, 53))
                    val buffer = ByteArray(4096)
                    val response = DatagramPacket(buffer, buffer.size)
                    socket.receive(response)
                    return buffer.copyOf(response.length)
                }
            } catch (e: Exception) {
                if (attempt > retries) return null
            }
        }
        return null
    }

    /** some resolvers recortan la respuesta en UDP: ahi se repite por TCP. */
    private fun exchangeTcp(resolver: String, packet: ByteArray): ByteArray? = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(resolver, 53), timeoutMs)
            socket.soTimeout = timeoutMs
            val out = socket.getOutputStream()
            out.write((packet.size shr 8) and 0xFF)
            out.write(packet.size and 0xFF)
            out.write(packet)
            out.flush()
            val input = socket.getInputStream()
            val head = readFully(input, 2) ?: return@use null
            val length = ((head[0].toInt() and 0xFF) shl 8) or (head[1].toInt() and 0xFF)
            readFully(input, length)
        }
    } catch (e: Exception) {
        null
    }

    private fun readFully(stream: InputStream, count: Int): ByteArray? {
        if (count <= 0 || count > 65535) return null
        val buffer = ByteArray(count)
        var read = 0
        while (read < count) {
            val n = stream.read(buffer, read, count - read)
            if (n < 0) return null
            read += n
        }
        return buffer
    }
}
