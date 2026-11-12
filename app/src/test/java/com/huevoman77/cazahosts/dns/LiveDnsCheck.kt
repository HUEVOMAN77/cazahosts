package com.huevoman77.cazahosts.dns

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/**
 * Comprobacion en vivo contra resolvers reales. No corre en la CI porque
 * depende de la red; se activa a mano para verificar el motor de punta a
 * punta: consultas A, MX, NS, TXT, AAAA, PTR y una fuerza bruta completa.
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
}
