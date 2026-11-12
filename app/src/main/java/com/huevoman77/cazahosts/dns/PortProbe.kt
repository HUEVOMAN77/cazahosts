package com.huevoman77.cazahosts.dns

import com.huevoman77.cazahosts.core.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import com.huevoman77.cazahosts.scan.Fingerprint

/**
 * Puertos abiertos de la direccion a la que apunta un subdominio. Sirve para
 * ver que servicios reales hay detras del nombre, no solo si el sitio carga.
 */
object PortProbe {

    data class OpenPort(val port: Int, val service: String, val product: String = "")

    suspend fun scan(ip: String, ports: List<Int>, timeoutMs: Int): List<OpenPort> =
        withContext(Dispatchers.IO) {
            coroutineScope {
                ports.map { port ->
                    async {
                        if (!Net.portOpen(ip, port, timeoutMs)) return@async null
                        OpenPort(port, Fingerprint.serviceName(port))
                    }
                }.awaitAll().filterNotNull()
            }
        }
}
