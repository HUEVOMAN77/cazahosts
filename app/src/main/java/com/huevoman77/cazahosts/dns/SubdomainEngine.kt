package com.huevoman77.cazahosts.dns

import com.huevoman77.cazahosts.data.DnsRecordInfo
import com.huevoman77.cazahosts.data.SubdomainResult
import com.huevoman77.cazahosts.vuln.Checks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SubdomainConfig(
    val domain: String,
    val words: List<String>,
    val recordTypes: Set<Int> = setOf(DnsType.A),
    val concurrency: Int = 40,
    val resolvers: List<String> = listOf("1.1.1.1", "8.8.8.8"),
    val timeoutMs: Int = 2500,
    val retries: Int = 1,
    val detectWildcard: Boolean = true,
    val chaseCname: Boolean = true,
    val probeHttp: Boolean = true,
    val reverseDns: Boolean = true,
    val maxCandidates: Int = 30000
)

data class SubdomainRun(
    val results: List<SubdomainResult>,
    val wildcardIps: Set<String>,
    val discardedWildcard: Int,
    val tested: Int,
    val candidates: Int,
    val elapsedMs: Long
)

/**
 * Motor de fuerza bruta de subdominios: prueba una lista de nombres contra el
 * dominio objetivo, detecta el DNS comodin para descartar falsos positivos y
 * persigue los CNAME para no perder hosts que solo existen como alias.
 */
class SubdomainEngine(private val config: SubdomainConfig) {

    @Volatile
    var cancelled = false

    private val client = DnsClient(config.resolvers, config.timeoutMs, config.retries)

    suspend fun run(
        onResult: (SubdomainResult) -> Unit,
        onProgress: (tested: Int, found: Int) -> Unit
    ): SubdomainRun = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val domain = normalizeDomain(config.domain)
        val candidates = buildCandidates(domain)
        val wildcardIps = if (config.detectWildcard) detectWildcard(domain) else emptySet()

        val found = LinkedHashMap<String, SubdomainResult>()
        var tested = 0
        var discarded = 0
        val lock = Any()

        val queue = Channel<String>(capacity = 512)
        coroutineScope {
            val producer = launch(Dispatchers.IO) {
                for (candidate in candidates) {
                    queue.send(candidate)
                }
                queue.close()
            }
            val workers = (1..config.concurrency.coerceIn(1, 120)).map {
                launch(Dispatchers.IO) {
                    for (host in queue) {
                        if (cancelled) break
                        val result = resolve(host)
                        synchronized(lock) {
                            tested++
                            if (result != null) {
                                val isWildcard = wildcardIps.isNotEmpty() &&
                                    result.addresses.isNotEmpty() &&
                                    result.addresses.all { it in wildcardIps }
                                if (isWildcard) {
                                    discarded++
                                } else {
                                    found[host] = result
                                    onResult(result)
                                }
                            }
                            onProgress(tested, found.size)
                        }
                    }
                }
            }
            workers.forEach { it.join() }
            producer.join()
        }

        val results = if (config.probeHttp) {
            probeHttp(found.values.toList(), onResult = { updated ->
                synchronized(lock) { found[updated.host] = updated }
            })
        } else {
            found.values.toList()
        }

        SubdomainRun(
            results = results.sortedBy { it.host },
            wildcardIps = wildcardIps,
            discardedWildcard = discarded,
            tested = tested,
            candidates = candidates.size,
            elapsedMs = System.currentTimeMillis() - start
        )
    }

    private fun buildCandidates(domain: String): List<String> {
        val seen = LinkedHashSet<String>()
        for (word in config.words) {
            val clean = word.trim().lowercase().trim('.')
            if (clean.isEmpty()) continue
            seen.add("$clean.$domain")
            if (seen.size >= config.maxCandidates) break
        }
        return seen.toList()
    }

    /**
     * Muchos dominios responden a cualquier nombre. Se prueban tres etiquetas
     * aleatorias: si todas resuelven, el dominio usa comodin y sus direcciones
     * se pueden descartar de los resultados.
     */
    private suspend fun detectWildcard(domain: String): Set<String> {
        val ips = LinkedHashSet<String>()
        repeat(3) {
            val label = randomLabel()
            val res = client.query("$label.$domain", DnsType.A) ?: return@repeat
            if (!res.success) return@repeat
            for (rec in res.answers) {
                val data = rec.data
                if (data is RData.Address) ips.add(data.ip)
            }
        }
        return ips
    }

    private suspend fun resolve(host: String): SubdomainResult? {
        val addresses = LinkedHashSet<String>()
        val ipv6 = LinkedHashSet<String>()
        val records = mutableListOf<DnsRecordInfo>()
        var cname = ""

        for (type in config.recordTypes) {
            if (cancelled) return null
            val response = client.query(host, type) ?: continue
            if (!response.success) continue
            for (rec in response.answers) {
                when (val data = rec.data) {
                    is RData.Address -> if (type == DnsType.AAAA) ipv6.add(data.ip) else addresses.add(data.ip)
                    is RData.Name -> {
                        if (type == DnsType.CNAME && cname.isEmpty()) cname = data.target
                        if (type != DnsType.CNAME) {
                            records += DnsRecordInfo(DnsType.name(type), data.target, rec.ttl)
                        }
                    }
                    is RData.Mail -> records += DnsRecordInfo(DnsType.name(type), "${data.preference} ${data.exchange}", rec.ttl)
                    is RData.Text -> records += DnsRecordInfo(DnsType.name(type), data.text, rec.ttl)
                    is RData.Service -> records += DnsRecordInfo(DnsType.name(type), "${data.port} ${data.target}", rec.ttl)
                    is RData.Zone -> records += DnsRecordInfo(DnsType.name(type), data.primary, rec.ttl)
                    is RData.Blob -> records += DnsRecordInfo(DnsType.name(type), data.hex, rec.ttl)
                }
            }
        }

        if (addresses.isEmpty() && ipv6.isEmpty() && cname.isEmpty() && records.isEmpty()) return null

        if (config.chaseCname && cname.isNotEmpty() && addresses.isEmpty() && ipv6.isEmpty()) {
            val target = client.query(cname, DnsType.A)
            if (target != null && target.success) {
                for (rec in target.answers) {
                    val data = rec.data
                    if (data is RData.Address) addresses.add(data.ip)
                }
            }
            val target6 = client.query(cname, DnsType.AAAA)
            if (target6 != null && target6.success) {
                for (rec in target6.answers) {
                    val data = rec.data
                    if (data is RData.Address) ipv6.add(data.ip)
                }
            }
        }

        return SubdomainResult(
            host = host,
            domain = config.domain,
            addresses = addresses.toList(),
            ipv6 = ipv6.toList(),
            cname = cname,
            records = records,
            firstSeen = System.currentTimeMillis()
        )
    }

    /** Segunda pasada: consulta el sitio web de cada subdominio para ver que expone. */
    private suspend fun probeHttp(
        results: List<SubdomainResult>,
        onResult: (SubdomainResult) -> Unit
    ): List<SubdomainResult> {
        val map = LinkedHashMap<String, SubdomainResult>()
        results.forEach { map[it.host] = it }
        val limit = results.take(250)
        val lock = Any()
        val queue = Channel<SubdomainResult>(capacity = 64)
        coroutineScope {
            val producer = launch(Dispatchers.IO) {
                limit.forEach { queue.send(it) }
                queue.close()
            }
            val workers = (1..12).map {
                launch(Dispatchers.IO) {
                    for (item in queue) {
                        if (cancelled) break
                        val updated = probeOne(item)
                        synchronized(lock) {
                            map[updated.host] = updated
                            onResult(updated)
                        }
                    }
                }
            }
            workers.forEach { it.join() }
            producer.join()
        }
        return map.values.toList()
    }

    private suspend fun probeOne(item: SubdomainResult): SubdomainResult {
        var status = 0
        var title = ""
        var reverse = item.reverseName
        if (reverse.isEmpty() && config.reverseDns) {
            reverse = item.addresses.firstOrNull()?.let { reverseName(it) } ?: ""
        }
        val https = Checks.httpGet(item.host, 443, "/", 2500, true)
        if (https != null && https.status > 0) {
            status = https.status
            title = https.title
        } else {
            val http = Checks.httpGet(item.host, 80, "/", 2500, false)
            if (http != null && http.status > 0) {
                status = http.status
                title = http.title
            }
        }
        if (status == 0 && reverse == item.reverseName) return item
        return item.copy(httpStatus = status, httpTitle = title, reverseName = reverse)
    }

    private fun reverseName(ip: String): String = try {
        val name = java.net.InetAddress.getByName(ip).canonicalHostName
        if (name.isNotEmpty() && name != ip && !name.endsWith(".in-addr.arpa")) name else ""
    } catch (e: Exception) {
        ""
    }

    private fun randomLabel(): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
        val sb = StringBuilder(12)
        repeat(12) { sb.append(alphabet[(Math.random() * alphabet.length).toInt()]) }
        return sb.toString()
    }

    companion object {
        /** Limpia la entrada: quita protocolo, ruta, espacios y www inicial. */
        fun normalizeDomain(input: String): String {
            var text = input.trim().lowercase()
            text = text.removePrefix("https://").removePrefix("http://")
            text = text.substringBefore('/')
            text = text.substringBefore(':')
            text = text.trim().trim('.')
            if (text.startsWith("www.")) text = text.substring(4)
            return text
        }
    }
}
