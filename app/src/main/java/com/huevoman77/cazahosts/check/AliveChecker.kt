package com.huevoman77.cazahosts.check

import com.huevoman77.cazahosts.core.Net
import com.huevoman77.cazahosts.data.WatchedHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Verificador de disponibilidad: vuelve a comprobar qué hosts siguen vivos
 * y avisa cuando cambia el estado.
 */
class AliveChecker(
    private val ports: List<Int> = listOf(80, 443, 22, 445, 8080, 3389, 23, 21)
) {

    suspend fun check(
        watched: List<WatchedHost>,
        timeoutMs: Int = 900,
        onUpdate: (WatchedHost) -> Unit = {}
    ): List<WatchedHost> = withContext(Dispatchers.IO) {
        val arp = Net.arpNeighbours().associate { it.ip to it }
        watched.map { host ->
            val wasAlive = host.alive
            var alive = false
            var openCount = 0
            for (port in ports) {
                if (Net.portOpen(host.ip, port, timeoutMs)) {
                    alive = true
                    openCount++
                }
            }
            if (!alive && arp.containsKey(host.ip)) alive = true
            val history = (host.history + System.currentTimeMillis()).takeLast(120)
            val updated = host.copy(
                alive = alive,
                lastStateChange = if (wasAlive != alive) System.currentTimeMillis() else host.lastStateChange,
                history = history,
                mac = arp[host.ip]?.mac ?: host.mac,
                portsOpen = openCount
            )
            onUpdate(updated)
            updated
        }
    }
}
