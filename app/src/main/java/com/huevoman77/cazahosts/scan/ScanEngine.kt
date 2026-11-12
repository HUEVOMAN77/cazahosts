package com.huevoman77.cazahosts.scan

import com.huevoman77.cazahosts.core.IpUtils
import com.huevoman77.cazahosts.core.Net
import com.huevoman77.cazahosts.core.TargetRange
import com.huevoman77.cazahosts.core.Vendors
import com.huevoman77.cazahosts.data.HostResult
import com.huevoman77.cazahosts.data.PortFinding
import com.huevoman77.cazahosts.data.VulnFinding
import com.huevoman77.cazahosts.vuln.Checks
import com.huevoman77.cazahosts.vuln.VulnDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class ScanMode(val label: String, val description: String) {
    HOSTS("Solo hosts", "Detecta qué direcciones de la red responden"),
    HOSTS_PORTS("Hosts y puertos", "Además identifica los puertos abiertos"),
    FULL("Auditoría completa", "Puertos, banners, versiones, CVEs y chequeos activos")
}

data class ScanConfig(
    val targets: String = "",
    val mode: ScanMode = ScanMode.HOSTS_PORTS,
    val probePorts: List<Int> = listOf(80, 443, 22, 21, 445, 8080, 8443, 3389, 23, 3306, 139, 53, 5555, 9000),
    val portPreset: String = "top",
    val customPorts: String = "",
    val concurrency: Int = 96,
    val timeoutMs: Int = 700,
    val grabBanner: Boolean = true,
    val activeChecks: Boolean = true,
    val useArp: Boolean = true,
    val reverseDns: Boolean = true,
    val udpChecks: Boolean = true,
    val maxHosts: Long = 65536
)

data class ScanProgress(
    val scanned: Long = 0,
    val total: Long = 0,
    val alive: Int = 0,
    val portsFound: Int = 0,
    val findings: Int = 0,
    val currentIp: String = "",
    val phase: String = "Preparando",
    val running: Boolean = false
)

data class ScanSummary(
    val hostsScanned: Long = 0,
    val hostsAlive: Int = 0,
    val portsFound: Int = 0,
    val findings: Int = 0,
    val durationMs: Long = 0,
    val hosts: List<HostResult> = emptyList(),
    val error: String = ""
)

class ScanEngine(private val config: ScanConfig) {

    private val cancelled = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private val portsByHost = ConcurrentHashMap<String, MutableList<PortFinding>>()
    private val findingsByHost = ConcurrentHashMap<String, MutableList<VulnFinding>>()
    private val aliveHosts = Collections.synchronizedList(mutableListOf<HostResult>())

    companion object {
        private const val DNS_TIMEOUT_MS = 5000L
    }

    fun cancel() {
        cancelled.set(true)
    }

    fun isCancelled(): Boolean = cancelled.get()

    fun pause() {
        paused.set(true)
    }

    fun resume() {
        paused.set(false)
    }

    fun isPaused(): Boolean = paused.get()

    /** Espera a que se reanude el escaneo, salvo que se cancele. */
    private suspend fun awaitResume() {
        while (paused.get() && !cancelled.get()) {
            delay(200)
        }
    }

    /**
     * Acepta rangos de IP y tambien nombres de host: "ejemplo.com" se resuelve
     * por DNS y se escanea la IP que devuelva.
     */
    private suspend fun resolveTargets(input: String): List<TargetRange> {
        val tokens = input.replace(Regex("\\s*-\\s*"), "-")
            .split(',', ';', '\n', ' ', '\t')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val out = mutableListOf<TargetRange>()
        for (token in tokens) {
            val parsed = IpUtils.parseTargets(token)
            if (parsed.isNotEmpty()) {
                out += parsed
                continue
            }
            val address = resolveHost(token) ?: continue
            val value = IpUtils.ipv4ToLong(address) ?: continue
            out += TargetRange(value, value, token)
        }
        return out
    }

    private suspend fun resolveHost(name: String): String? {
        val address = withTimeoutOrNull(DNS_TIMEOUT_MS) {
            withContext(Dispatchers.IO) { InetAddress.getByName(name) }
        }
        return address?.hostAddress
    }

    suspend fun run(
        onHost: (HostResult) -> Unit,
        onProgress: (ScanProgress) -> Unit
    ): ScanSummary = coroutineScope {
        val started = System.currentTimeMillis()
        val ranges = resolveTargets(config.targets)
        if (ranges.isEmpty()) {
            return@coroutineScope ScanSummary(error = "No se entendió el objetivo. Usa 192.168.1.0/24, 10.0.0.1-254, una IP sola o un nombre de host como ejemplo.com.")
        }

        val totalHosts = ranges.sumOf { it.size }.coerceAtMost(config.maxHosts)
        val scanned = AtomicLong(0L)
        val alive = AtomicInteger(0)
        val portsFound = AtomicInteger(0)
        val findingCount = AtomicInteger(0)
        val currentIp = java.util.concurrent.atomic.AtomicReference("")

        onProgress(ScanProgress(total = totalHosts, phase = "Buscando hosts", running = true))

        // ---------------------------------------------------------- fase 1: hosts
        val ipChannel = Channel<String>(capacity = 4096)
        val producer = launch(Dispatchers.IO) {
            var done = 0L
            for (range in ranges) {
                var value = range.start
                while (value <= range.end) {
                    if (cancelled.get()) break
                    if (done >= config.maxHosts) break
                    ipChannel.send(IpUtils.longToIpv4(value))
                    done++
                    if (value == 0xFFFFFFFFL) break
                    value++
                }
                if (done >= config.maxHosts) break
            }
            ipChannel.close()
        }

        val arpTable = if (config.useArp) Net.arpNeighbours().associateBy { it.ip } else emptyMap()

        val workers = (1..config.concurrency.coerceIn(1, 256)).map {
            launch(Dispatchers.IO) {
                for (ip in ipChannel) {
                    if (cancelled.get()) break
                    awaitResume()
                    if (cancelled.get()) break
                    currentIp.set(ip)
                    val host = probeHost(ip, arpTable)
                    scanned.incrementAndGet()
                    if (host != null) {
                        aliveHosts += host
                        alive.incrementAndGet()
                        onHost(host)
                    }
                    if (scanned.get() % 25 == 0L) {
                        onProgress(
                            ScanProgress(
                                scanned = scanned.get(),
                                total = totalHosts,
                                alive = alive.get(),
                                portsFound = portsFound.get(),
                                findings = findingCount.get(),
                                currentIp = currentIp.get(),
                                phase = "Buscando hosts",
                                running = true
                            )
                        )
                    }
                }
            }
        }
        workers.forEach { it.join() }
        producer.join()

        // ------------------------------------------------------- fase 2: puertos
        val aliveList = aliveHosts.toList()
        if (config.mode != ScanMode.HOSTS && aliveList.isNotEmpty() && !cancelled.get()) {
            onProgress(
                ScanProgress(
                    scanned = scanned.get(),
                    total = totalHosts,
                    alive = alive.get(),
                    phase = "Escaneando puertos",
                    running = true
                )
            )
            val portList = Fingerprint.portsForPreset(config.portPreset, config.customPorts)
            val tasks = Channel<Pair<String, Int>>(capacity = 8192)
            val job = launch(Dispatchers.IO) {
                for (host in aliveList) {
                    for (port in portList) tasks.send(host.ip to port)
                }
                tasks.close()
            }
            val portWorkers = (1..config.concurrency.coerceIn(1, 128)).map {
                launch(Dispatchers.IO) {
                    for ((ip, port) in tasks) {
                        if (cancelled.get()) break
                        awaitResume()
                        if (cancelled.get()) break
                        val finding = probePort(ip, port)
                        if (finding != null) {
                            portsByHost.getOrPut(ip) { Collections.synchronizedList(mutableListOf()) } += finding
                            portsFound.incrementAndGet()
                        }
                    }
                }
            }
            portWorkers.forEach { it.join() }
            job.join()

            // -------------------------------------------- fase 2b: puertos UDP
            if (config.udpChecks && !cancelled.get()) {
                onProgress(
                    ScanProgress(
                        scanned = scanned.get(),
                        total = totalHosts,
                        alive = alive.get(),
                        portsFound = portsFound.get(),
                        phase = "Probando servicios UDP",
                        running = true
                    )
                )
                val udpTasks = Channel<Pair<String, Int>>(capacity = 4096)
                val udpJob = launch(Dispatchers.IO) {
                    for (host in aliveList) {
                        for (port in UdpChecks.ports) udpTasks.send(host.ip to port)
                    }
                    udpTasks.close()
                }
                val udpWorkers = (1..config.concurrency.coerceIn(1, 64)).map {
                    launch(Dispatchers.IO) {
                        for ((ip, port) in udpTasks) {
                            if (cancelled.get()) break
                            awaitResume()
                            if (cancelled.get()) break
                            val result = UdpChecks.probe(ip, port, config.timeoutMs.coerceAtLeast(700)) ?: continue
                            portsByHost.getOrPut(ip) { Collections.synchronizedList(mutableListOf()) } += result.port
                            portsFound.incrementAndGet()
                            if (result.findings.isNotEmpty()) {
                                findingsByHost.getOrPut(ip) { Collections.synchronizedList(mutableListOf()) } += result.findings
                                findingCount.addAndGet(result.findings.size)
                            }
                        }
                    }
                }
                udpWorkers.forEach { it.join() }
                udpJob.join()
            }

            // ---------------------------------------------- fase 3: auditoría activa
            if (config.activeChecks && !cancelled.get()) {
                onProgress(
                    ScanProgress(
                        scanned = scanned.get(),
                        total = totalHosts,
                        alive = alive.get(),
                        portsFound = portsFound.get(),
                        phase = "Auditando servicios",
                        running = true
                    )
                )
                runActiveChecks(aliveList, findingCount) { progress ->
                    onProgress(
                        ScanProgress(
                            scanned = scanned.get(),
                            total = totalHosts,
                            alive = alive.get(),
                            portsFound = portsFound.get(),
                            findings = findingCount.get(),
                            currentIp = progress,
                            phase = "Auditando servicios",
                            running = true
                        )
                    )
                }
            }
        }

        // ------------------------------------------------------------- resultado
        val finals = aliveList.map { host ->
            val ports = portsByHost[host.ip]?.toList() ?: emptyList()
            val findings = findingsByHost[host.ip]?.toList()?.distinctBy { "${it.id}:${it.port}" } ?: emptyList()
            val services = ports.map { it.service }.distinct()
            val host2 = host.copy(
                openPorts = ports.sortedBy { it.port },
                findings = findings.sortedByDescending { it.severityEnum.peso },
                os = Fingerprint.guessOs(Fingerprint.HostOs(services + listOfNotNull(host.os)))
            )
            findingCount.addAndGet(0)
            onHost(host2)
            host2
        }.sortedBy { it.ip }

        onProgress(
            ScanProgress(
                scanned = scanned.get(),
                total = totalHosts,
                alive = alive.get(),
                portsFound = portsFound.get(),
                findings = findingCount.get(),
                phase = "Finalizado",
                running = false
            )
        )

        ScanSummary(
            hostsScanned = scanned.get(),
            hostsAlive = finals.size,
            portsFound = portsFound.get(),
            findings = finals.sumOf { it.findings.size },
            durationMs = System.currentTimeMillis() - started,
            hosts = finals
        )
    }

    // ------------------------------------------------------------------ helpers

    private fun probeHost(ip: String, arp: Map<String, com.huevoman77.cazahosts.core.Neighbour>): HostResult? {
        var latency = 0L
        var alive = false
        for (port in config.probePorts) {
            val start = System.currentTimeMillis()
            if (Net.portOpen(ip, port, config.timeoutMs)) {
                alive = true
                latency = System.currentTimeMillis() - start
                break
            }
        }
        if (!alive && arp.containsKey(ip)) {
            alive = true
            latency = 1
        }
        if (!alive) return null

        val neighbour = arp[ip]
        val hostname = if (config.reverseDns) Net.reverseDns(ip) ?: "" else ""
        return HostResult(
            ip = ip,
            alive = true,
            hostname = hostname,
            mac = neighbour?.mac ?: "",
            vendor = neighbour?.mac?.let { Vendors.lookup(it) } ?: "",
            latencyMs = latency,
            lastSeen = System.currentTimeMillis()
        )
    }

    private fun probePort(ip: String, port: Int): PortFinding? {
        val socket = Net.connect(ip, port, config.timeoutMs) ?: return null
        var banner = ""
        var tls = false
        var tlsVersion = ""
        var certExpiry = ""
        var certDays = -1
        var certSubject = ""
        try {
            if (config.grabBanner) {
                val tlsPorts = setOf(443, 8443, 465, 993, 995, 636, 6697, 5223, 9443, 4433)
                if (tlsPorts.contains(port)) {
                    val tlsSocket = Checks.tlsSocket(ip, port, config.timeoutMs)
                    if (tlsSocket != null) {
                        val info = Checks.tlsInfo(tlsSocket)
                        tls = true
                        tlsVersion = info?.version ?: ""
                        certExpiry = info?.expiry ?: ""
                        certDays = info?.daysLeft ?: -1
                        certSubject = info?.subject ?: ""
                        banner = readBanner(tlsSocket, port)
                        tlsSocket.close()
                    } else {
                        banner = readBanner(socket, port)
                    }
                } else {
                    banner = readBanner(socket, port)
                }
            }
        } catch (e: Exception) {
            // el banner es opcional
        } finally {
            try {
                socket.close()
            } catch (ignored: Exception) {
            }
        }

        val info = Fingerprint.identify(port, banner)
        val httpStatus = Checks.parseHttp(banner)?.status ?: 0
        val title = Checks.parseHttp(banner)?.title ?: ""
        val finding = PortFinding(
            port = port,
            service = info.service,
            product = info.product,
            version = info.version,
            banner = banner.take(500),
            tls = tls,
            tlsVersion = tlsVersion,
            certExpiry = certExpiry,
            certDaysLeft = certDays,
            certSubject = certSubject,
            httpStatus = httpStatus,
            httpTitle = title
        )
        val passive = VulnDb.evaluate(finding)
        if (passive.isNotEmpty()) {
            findingsByHost.getOrPut(ip) { Collections.synchronizedList(mutableListOf()) } += passive
        }
        if (tls && certDays >= -1) {
            val tlsInfo = Checks.TlsInfo(tlsVersion, certExpiry, certDays, certSubject)
            val tlsFindings = Checks.tlsFindings(tlsInfo, port)
            if (tlsFindings.isNotEmpty()) {
                findingsByHost.getOrPut(ip) { Collections.synchronizedList(mutableListOf()) } += tlsFindings
            }
        }
        return finding
    }

    private fun readBanner(socket: java.net.Socket, port: Int): String {
        return try {
            socket.soTimeout = 1200
            val httpPorts = setOf(80, 81, 443, 591, 8000, 8008, 8080, 8081, 8088, 8888, 9000, 3000, 5000, 9090, 9200, 2375, 6443, 15672, 10000)
            if (httpPorts.contains(port)) {
                val host = socket.inetAddress?.hostAddress ?: ""
                socket.getOutputStream().write(
                    ("GET / HTTP/1.0\r\nHost: $host\r\nUser-Agent: Mozilla/5.0\r\nConnection: close\r\n\r\n").toByteArray()
                )
                socket.getOutputStream().flush()
            }
            Checks.readAll(socket.getInputStream(), 2048)
        } catch (e: Exception) {
            ""
        }
    }

    private suspend fun runActiveChecks(
        hosts: List<HostResult>,
        findingCount: AtomicInteger,
        onTick: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val queue = Channel<HostResult>(capacity = 256)
        val job = launch(Dispatchers.IO) {
            hosts.forEach { queue.send(it) }
            queue.close()
        }
        val workers = (1..minOf(config.concurrency.coerceIn(1, 32), 16)).map {
            launch(Dispatchers.IO) {
                for (host in queue) {
                    if (cancelled.get()) break
                    val ports = portsByHost[host.ip]?.toList() ?: emptyList()
                    if (ports.isEmpty()) continue
                    val httpPorts = ports.filter { it.service.startsWith("http") }.map { it.port }
                    val firstHttp = httpPorts.minOrNull()
                    for (port in ports) {
                        if (cancelled.get()) break
                        val useTls = port.tls
                        val isPrimaryHttp = port.port == firstHttp
                        val findings = mutableListOf<VulnFinding>()
                        if (port.service.startsWith("http")) {
                            val pathFindings = if (isPrimaryHttp) {
                                Checks.httpFindings(host.ip, port.port, config.timeoutMs.coerceAtLeast(1500), useTls)
                            } else {
                                emptyList()
                            }
                            findings += pathFindings
                            findings += Checks.headerFindings(port.banner.substringBefore("\r\n\r\n"), port.port)
                        } else {
                            findings += Checks.activeChecks(host.ip, port, config.timeoutMs.coerceAtLeast(1200))
                        }
                        if (findings.isNotEmpty()) {
                            findingsByHost.getOrPut(host.ip) { Collections.synchronizedList(mutableListOf()) } += findings
                            findingCount.addAndGet(findings.size)
                        }
                        onTick(host.ip)
                    }
                }
            }
        }
        workers.forEach { it.join() }
        job.join()
    }
}
