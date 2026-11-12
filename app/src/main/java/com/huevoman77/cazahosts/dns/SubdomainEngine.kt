package com.huevoman77.cazahosts.dns

import com.huevoman77.cazahosts.data.DnsRecordInfo
import com.huevoman77.cazahosts.data.OpenPortInfo
import com.huevoman77.cazahosts.data.SubdomainOrigin
import com.huevoman77.cazahosts.data.SubdomainResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

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
    val useZoneTransfer: Boolean = true,
    val useCertificateTransparency: Boolean = true,
    val scanPorts: Boolean = false,
    val maxCandidates: Int = 30000
)

/** Tope de nombres candidatos por corrida para no ahogar la red. */
const val MAX_SUBDOMAIN_CANDIDATES = 30000

/** Tope de nombres que se resuelven desde fuentes externas (zona y certificados). */
const val MAX_EXTERNAL_NAMES = 600

/** Margen para consultar crt.sh: el servidor responde lento. */
const val CT_TIMEOUT_MS = 20000

/** Puertos que se revisan en la direccion de cada subdominio. */
val SUBDOMAIN_PORTS = listOf(
    21, 22, 25, 53, 80, 110, 143, 443, 445, 3306, 3389, 5432, 6379, 8080, 8443, 9200, 27017
)

data class SubdomainRun(
    val results: List<SubdomainResult>,
    val wildcardIps: Set<String>,
    val discardedWildcard: Int,
    val tested: Int,
    val candidates: Int,
    val elapsedMs: Long,
    val zoneServer: String = "",
    val fromBruteForce: Int = 0,
    val fromZone: Int = 0,
    val fromCertificate: Int = 0
)

/**
 * Motor de subdominios: combina la fuerza bruta con diccionario, la
 * transferencia de zona y los registros publicos de certificados. Detecta el
 * DNS comodin para descartar falsos positivos, persigue los CNAME y clasifica
 * de que forma responde cada host.
 */
class SubdomainEngine(private val config: SubdomainConfig) {

    @Volatile
    var cancelled = false
        set(value) {
            field = value
            client.cancelled = value
        }

    private val client = DnsClient(config.resolvers, config.timeoutMs, config.retries)

    suspend fun run(
        onResult: (SubdomainResult) -> Unit,
        onProgress: (tested: Int, found: Int) -> Unit,
        onPhase: (String) -> Unit = {}
    ): SubdomainRun = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val domain = normalizeDomain(config.domain)
        val wildcardIps = if (config.detectWildcard) detectWildcard(domain) else emptySet()

        val found = LinkedHashMap<String, SubdomainResult>()
        val lock = Any()
        val tested = AtomicInteger(0)
        val discarded = AtomicInteger(0)
        val bruteForceCount = AtomicInteger(0)
        val zoneCount = AtomicInteger(0)
        val certCount = AtomicInteger(0)

        // ------------------------------------------------- fase 1: fuerza bruta
        val candidates = buildCandidates(domain)
        onPhase("Probando nombres")
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
                        val result = resolve(host, SubdomainOrigin.BRUTE_FORCE, config.recordTypes)
                        synchronized(lock) {
                            tested.incrementAndGet()
                            if (result != null) {
                                val isWildcard = wildcardIps.isNotEmpty() &&
                                    result.addresses.isNotEmpty() &&
                                    result.addresses.all { it in wildcardIps }
                                if (isWildcard) {
                                    discarded.incrementAndGet()
                                } else {
                                    found[host] = result
                                    bruteForceCount.incrementAndGet()
                                    onResult(result)
                                }
                            }
                            onProgress(tested.get(), found.size)
                        }
                    }
                }
            }
            workers.forEach { it.join() }
            producer.join()
        }

        // ------------------------------------------- fase 2: transferencia de zona
        var zoneServer = ""
        if (config.useZoneTransfer && !cancelled) {
            onPhase("Transferencia de zona")
            val servers = nameservers(domain)
            val zone = ZoneTransfer.transfer(domain, servers, config.timeoutMs)
            if (zone != null) {
                zoneServer = zone.server
                val names = zone.hosts.filter { it.endsWith(".$domain") }.take(MAX_EXTERNAL_NAMES)
                zoneCount.set(
                    collect(names, SubdomainOrigin.ZONE_TRANSFER, found, lock, tested, onResult, onProgress)
                )
            }
        }

        // --------------------------------------- fase 3: registros de certificados
        if (config.useCertificateTransparency && !cancelled) {
            onPhase("Buscando certificados")
            // crt.sh tarda bastante en responder: necesita mas margen que el DNS.
            val entries = CertificateTransparency.search(domain, maxOf(config.timeoutMs, CT_TIMEOUT_MS))
            val names = entries.map { it.name }
                .filter { it != domain }
                .distinct()
                .take(MAX_EXTERNAL_NAMES)
            certCount.set(
                collect(names, SubdomainOrigin.CERTIFICATE, found, lock, tested, onResult, onProgress)
            )
        }

        // ------------------------------------------ fase 4: clasificacion web
        val classified = if (config.probeHttp) {
            onPhase("Revisando sitios web")
            classifyWeb(found.values.toList(), onResult)
        } else {
            found.values.toList()
        }

        // ---------------------------------------------- fase 5: puertos abiertos
        val final = if (config.scanPorts && !cancelled) {
            onPhase("Buscando puertos")
            scanPorts(classified, onResult)
        } else {
            classified
        }

        SubdomainRun(
            results = final.sortedBy { it.host },
            wildcardIps = wildcardIps,
            discardedWildcard = discarded.get(),
            tested = tested.get(),
            candidates = candidates.size,
            elapsedMs = System.currentTimeMillis() - start,
            zoneServer = zoneServer,
            fromBruteForce = bruteForceCount.get(),
            fromZone = zoneCount.get(),
            fromCertificate = certCount.get()
        )
    }

    /**
     * Resuelve una lista de nombres que vienen de fuera (zona o
     * certificados) y agrega los que existen.
     */
    private suspend fun collect(
        names: List<String>,
        origin: String,
        found: LinkedHashMap<String, SubdomainResult>,
        lock: Any,
        tested: AtomicInteger,
        onResult: (SubdomainResult) -> Unit,
        onProgress: (tested: Int, found: Int) -> Unit
    ): Int = coroutineScope {
        val added = AtomicInteger(0)
        val queue = Channel<String>(capacity = 256)
        launch(Dispatchers.IO) {
            for (name in names) {
                queue.send(name)
            }
            queue.close()
        }
        val workers = (1..10).map {
            launch(Dispatchers.IO) {
                for (host in queue) {
                    if (cancelled) break
                    val result = resolve(host, origin, setOf(DnsType.A, DnsType.AAAA, DnsType.CNAME))
                    synchronized(lock) {
                        tested.incrementAndGet()
                        if (result != null && !found.containsKey(host)) {
                            found[host] = result
                            added.incrementAndGet()
                            onResult(result)
                        }
                        onProgress(tested.get(), found.size)
                    }
                }
            }
        }
        workers.forEach { it.join() }
        added.get()
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
     * aleatorias: si las tres resuelven al mismo conjunto de direcciones, el
     * dominio usa comodin y esas direcciones se pueden descartar de los
     * resultados. Si alguna de las tres no resuelve, no se descarta nada.
     */
    private suspend fun detectWildcard(domain: String): Set<String> {
        val samples = mutableListOf<Set<String>>()
        repeat(3) {
            val label = randomLabel()
            val res = client.query("$label.$domain", DnsType.A) ?: return emptySet()
            if (!res.success) return emptySet()
            val ips = LinkedHashSet<String>()
            for (rec in res.answers) {
                val data = rec.data
                if (data is RData.Address) ips.add(data.ip)
            }
            if (ips.isEmpty()) return emptySet()
            samples.add(ips)
        }
        val first = samples.first()
        if (samples.all { it == first }) return first
        return emptySet()
    }

    /** Servidores de nombres del dominio, para intentar la transferencia de zona. */
    private suspend fun nameservers(domain: String): List<String> {
        val response = client.query(domain, DnsType.NS) ?: return emptyList()
        if (!response.success) return emptyList()
        return response.answers.mapNotNull { rec ->
            val data = rec.data
            if (data is RData.Name && rec.type == DnsType.NS) data.target else null
        }.distinct()
    }

    private suspend fun resolve(host: String, origin: String, types: Set<Int>): SubdomainResult? {
        val addresses = LinkedHashSet<String>()
        val ipv6 = LinkedHashSet<String>()
        val records = mutableListOf<DnsRecordInfo>()
        var cname = ""

        for (type in types) {
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
            origin = origin,
            addresses = addresses.toList(),
            ipv6 = ipv6.toList(),
            cname = cname,
            records = records,
            firstSeen = System.currentTimeMillis()
        )
    }

    /**
     * Segunda pasada: revisa que sitio web expone cada subdominio y de que
     * forma responde (HTTP directo, TLS con SNI o con un payload).
     */
    private suspend fun classifyWeb(
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
                        val updated = classifyOne(item)
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

    private suspend fun classifyOne(item: SubdomainResult): SubdomainResult {
        var reverse = item.reverseName
        if (reverse.isEmpty() && config.reverseDns) {
            reverse = item.addresses.firstOrNull()?.let { reverseName(it) } ?: ""
        }
        val ip = item.primaryAddress
        if (ip.isEmpty()) return item.copy(reverseName = reverse)
        val report = WebClassifier.classify(item.host, ip, config.timeoutMs)
        return item.copy(
            httpStatus = report.httpStatus,
            httpTitle = report.httpTitle,
            reverseName = reverse,
            directStatus = report.directStatus,
            sniStatus = report.sniStatus,
            sniOk = report.sniOk,
            sniRequired = report.sniRequired,
            payloadStatus = report.payloadStatus,
            payloadOk = report.payloadOk,
            certSubject = report.cert?.subject ?: "",
            certIssuer = report.cert?.issuer ?: "",
            certDaysLeft = report.cert?.daysLeft ?: -1
        )
    }

    /** Tercera pasada: que puertos estan abiertos en la direccion del host. */
    private suspend fun scanPorts(
        results: List<SubdomainResult>,
        onResult: (SubdomainResult) -> Unit
    ): List<SubdomainResult> {
        val map = LinkedHashMap<String, SubdomainResult>()
        results.forEach { map[it.host] = it }
        val limit = results.take(150)
        val lock = Any()
        val queue = Channel<SubdomainResult>(capacity = 32)
        coroutineScope {
            val producer = launch(Dispatchers.IO) {
                limit.forEach { queue.send(it) }
                queue.close()
            }
            val workers = (1..8).map {
                launch(Dispatchers.IO) {
                    for (item in queue) {
                        if (cancelled) break
                        val ip = item.primaryAddress
                        if (ip.isEmpty()) continue
                        val open = PortProbe.scan(ip, SUBDOMAIN_PORTS, 1200)
                        if (open.isEmpty()) continue
                        val updated = item.copy(
                            openPorts = open.map { OpenPortInfo(it.port, it.service) }
                        )
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
