package com.huevoman77.cazahosts.dns

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/**
 * Comprobacion en vivo contra la red real. No corre en la CI porque depende
 * de Internet; se activa a mano (quitando el @Ignore) para verificar el motor
 * de punta a punta: consultas DNS, fuerza bruta, zona, certificados, web y
 * puertos.
 */
@Ignore("Requiere salida a Internet; se ejecuta a mano")
class LiveDnsCheck {

    @Test
    fun consultaReal() {
        runBlocking {
            val client = DnsClient(listOf("1.1.1.1", "8.8.8.8"), timeoutMs = 4000, retries = 1)

            val a = client.query("example.com", DnsType.A)
            println("A example.com -> ${a?.answers?.size} respuestas, rcode=${a?.rcode}")
            a?.answers?.forEach { println("   ${it.name} ${DnsType.name(it.type)} ${it.data}") }
            assertTrue(a != null && a.success && a.answers.isNotEmpty())

            val mx = client.query("gmail.com", DnsType.MX)
            println("MX gmail.com -> ${mx?.answers?.size} respuestas")
            mx?.answers?.forEach { println("   ${it.name} ${DnsType.name(it.type)} ${it.data}") }
            assertTrue(mx != null && mx.answers.any { it.type == DnsType.MX })

            val ns = client.query("com", DnsType.NS)
            println("NS com -> ${ns?.answers?.size} respuestas")
            assertTrue(ns != null && ns.answers.isNotEmpty())

            val txt = client.query("google.com", DnsType.TXT)
            println("TXT google.com -> ${txt?.answers?.size} respuestas")
            txt?.answers?.take(2)?.forEach { println("   ${it.name} ${DnsType.name(it.type)} ${it.data}") }

            val v6 = client.query("google.com", DnsType.AAAA)
            println("AAAA google.com -> ${v6?.answers?.size} respuestas")
            v6?.answers?.forEach { println("   ${it.name} ${DnsType.name(it.type)} ${it.data}") }

            val ptr = client.query("8.8.8.8.in-addr.arpa", DnsType.PTR)
            println("PTR 8.8.8.8 -> ${ptr?.answers?.size} respuestas")
            ptr?.answers?.forEach { println("   ${it.name} ${DnsType.name(it.type)} ${it.data}") }
            assertTrue(ptr != null && ptr.answers.any { it.type == DnsType.PTR })

            val cname = client.query("www.github.com", DnsType.A)
            println("A www.github.com -> ${cname?.answers?.size} respuestas")
            cname?.answers?.forEach { println("   ${it.name} ${DnsType.name(it.type)} ${it.data}") }
        }
    }

    @Test
    fun fuerzaBrutaReal() {
        runBlocking {
            val config = SubdomainConfig(
                domain = "github.com",
                words = listOf("www", "api", "mail", "noexisteeste12345", "gist", "blog"),
                recordTypes = setOf(DnsType.A, DnsType.CNAME),
                concurrency = 8,
                resolvers = listOf("1.1.1.1", "8.8.8.8"),
                timeoutMs = 4000,
                probeHttp = false,
                reverseDns = false
            )
            val run = SubdomainEngine(config).run(onResult = {}, onProgress = { _, _ -> })
            println("fuerza bruta -> probados=${run.tested} encontrados=${run.results.size} comodin=${run.wildcardIps}")
            run.results.forEach {
                println("   ${it.host} -> ${it.addresses} cname=${it.cname}")
            }
            assertTrue(run.results.any { it.host == "www.github.com" })
        }
    }

    /** Corrida completa: fuerza bruta, zona, certificados, web y puertos. */
    @Test
    fun corridaCompletaReal() {
        runBlocking {
            val config = SubdomainConfig(
                domain = "github.com",
                words = listOf("www", "api", "gist", "blog", "noexisteeste12345"),
                recordTypes = setOf(DnsType.A, DnsType.CNAME),
                concurrency = 8,
                resolvers = listOf("1.1.1.1", "8.8.8.8"),
                timeoutMs = 4000,
                probeHttp = true,
                reverseDns = true,
                useZoneTransfer = true,
                useCertificateTransparency = true,
                scanPorts = true
            )
            val run = SubdomainEngine(config).run(
                onResult = {},
                onProgress = { _, _ -> },
                onPhase = { println("FASE: $it") }
            )
            println("completa -> probados=${run.tested} encontrados=${run.results.size}")
            println("   fuerza bruta=${run.fromBruteForce} zona=${run.fromZone} certificados=${run.fromCertificate}")
            println("   servidor de zona=${run.zoneServer} comodin=${run.wildcardIps} descartados=${run.discardedWildcard}")
            run.results.forEach {
                println("   ${it.host} [${it.origin}] http=${it.httpStatus} directo=${it.directStatus} sni=${it.sniStatus}/${it.sniOk} payload=${it.payloadStatus}/${it.payloadOk}")
                println("      puertos=${it.openPorts.map { p -> p.port }} cert=${it.certIssuer} dias=${it.certDaysLeft} reverse=${it.reverseName}")
            }
            assertTrue(run.results.any { it.host == "www.github.com" })
            assertTrue(run.results.any { it.httpStatus == 200 })
            assertTrue(run.fromCertificate > 0)
        }
    }

    /** Transferencia de zona: muchos servidores la niegan, pero no debe romper. */
    @Test
    fun transferenciaDeZonaReal() {
        runBlocking {
            val client = DnsClient(listOf("1.1.1.1", "8.8.8.8"), timeoutMs = 4000)
            for (domain in listOf("nic.cl", "example.com", "github.com")) {
                val ns = client.query(domain, DnsType.NS)
                val servers = ns?.answers.orEmpty().mapNotNull { rec ->
                    val data = rec.data
                    if (data is RData.Name && rec.type == DnsType.NS) data.target else null
                }.distinct()
                println("NS $domain -> $servers")
                for (server in servers) {
                    val zone = ZoneTransfer.transfer(domain, listOf(server), 6000)
                    println("   AXFR en $server -> ${zone?.entries?.size ?: 0} registros, ${zone?.hosts?.size ?: 0} hosts")
                    if (zone != null && zone.entries.isNotEmpty()) {
                        assertTrue(zone.hosts.any { it.endsWith(".$domain") })
                        return@runBlocking
                    }
                }
            }
        }
    }

    /** Registros publicos de certificados: deben devolver nombres del dominio. */
    @Test
    fun certificadosReales() {
        runBlocking {
            val entries = CertificateTransparency.search("github.com", 15000)
            println("CT github.com -> ${entries.size} entradas")
            entries.take(10).forEach { println("   ${it.name} (${it.issuer})") }
            assertTrue(entries.isNotEmpty())
            assertTrue(entries.any { it.name.endsWith("github.com") })
        }
    }

    /** Clasificador web: estado real, TLS con SNI, certificado y payload. */
    @Test
    fun clasificadorWebReal() {
        runBlocking {
            val client = DnsClient(listOf("1.1.1.1"), timeoutMs = 4000)
            for (host in listOf("github.com", "example.com", "www.google.com")) {
                val response = client.query(host, DnsType.A)
                val ip = response?.answers.orEmpty().mapNotNull { rec ->
                    val data = rec.data
                    if (data is RData.Address) data.ip else null
                }.firstOrNull()
                if (ip == null) {
                    println("$host -> sin IP")
                    continue
                }
                val report = WebClassifier.classify(host, ip, 6000)
                println("$host ($ip)")
                println("   directo=${report.directStatus} sni=${report.sniStatus} payload=${report.payloadStatus}")
                println("   sniOk=${report.sniOk} sniRequired=${report.sniRequired} payloadOk=${report.payloadOk} httpStatus=${report.httpStatus}")
                println("   titulo=${report.httpTitle} emisor=${report.cert?.issuer} dias=${report.cert?.daysLeft}")
            }
            assertTrue(true)
        }
    }

    /** Diagnostico: leer el certificado con un gestor de confianza que lo captura. */
    @Test
    fun diagnosticoCertificado() {
        runBlocking {
            for (host in listOf("example.com", "github.com")) {
                val manager = object : javax.net.ssl.X509TrustManager {
                    @Volatile
                    var cadena: List<java.security.cert.X509Certificate> = emptyList()
                    override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {
                        cadena = chain?.map { it as java.security.cert.X509Certificate } ?: emptyList()
                    }
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                }
                val ctx = javax.net.ssl.SSLContext.getInstance("TLS")
                ctx.init(null, arrayOf<javax.net.ssl.TrustManager>(manager), java.security.SecureRandom())
                val socket = ctx.socketFactory.createSocket() as javax.net.ssl.SSLSocket
                socket.use {
                    it.connect(java.net.InetSocketAddress(host, 443), 6000)
                    it.soTimeout = 6000
                    it.useClientMode = true
                    val params = it.sslParameters
                    params.serverNames = listOf(javax.net.ssl.SNIHostName(host))
                    it.sslParameters = params
                    it.startHandshake()
                    println("CERT $host protocolo=${it.session.protocol} cipher=${it.session.cipherSuite}")
                    println("CERT $host sesion=${it.session.peerCertificates.size} capturados=${manager.cadena.size}")
                    manager.cadena.firstOrNull()?.let { cert ->
                        println("CERT $host sujeto=${cert.subjectX500Principal.name}")
                        println("CERT $host emisor=${cert.issuerX500Principal.name} valido=${cert.notAfter} san=${cert.subjectAlternativeNames}")
                    }
                }
            }
        }
    }

    /** Dominios que aceptan AXFR: hay que confirmar que el codigo los lee. */
    @Test
    fun diagnosticoAxfr() {
        runBlocking {
            for (domain in listOf("forensec.ru", "axfr.orbvpn.com", "iis.net", "cim.se", "dns-tools.org")) {
                val zone = absoluteDnsName(domain) ?: continue
                val client = org.xbill.DNS.SimpleResolver("1.1.1.1")
                client.setTimeout(java.time.Duration.ofSeconds(5))
                val ns = client.send(
                    org.xbill.DNS.Message.newQuery(
                        org.xbill.DNS.Record.newRecord(zone, org.xbill.DNS.Type.NS, org.xbill.DNS.DClass.IN)
                    )
                )
                val servers = ns.getSection(org.xbill.DNS.Section.ANSWER).mapNotNull {
                    (it as? org.xbill.DNS.NSRecord)?.target?.toString(true)
                }
                if (servers.isEmpty()) {
                    println("AXFR $domain sin NS")
                    continue
                }
                for (server in servers.take(2)) {
                    try {
                        val xfr = org.xbill.DNS.ZoneTransferIn.newAXFR(zone, server, 53, null)
                        xfr.setTimeout(java.time.Duration.ofSeconds(8))
                        xfr.run()
                        val records = xfr.getAXFR()
                        println("AXFR $domain en $server -> ${records.size} registros")
                        records.take(3).forEach { println("   ${it.name} ${it.type} ${it.toString().take(90)}") }
                    } catch (e: Throwable) {
                        println("AXFR $domain en $server -> EXCEPCION ${e::class.java.simpleName}: ${e.message}")
                    }
                }
            }
        }
    }

    /** Puertos abiertos de una direccion conocida. */
    @Test
    fun puertosReales() {
        runBlocking {
            val open = PortProbe.scan("1.1.1.1", listOf(53, 80, 443, 8443, 22), 2000)
            println("1.1.1.1 -> ${open.map { it.port to it.service }}")
            assertTrue(open.any { it.port == 443 })
        }
    }
}
