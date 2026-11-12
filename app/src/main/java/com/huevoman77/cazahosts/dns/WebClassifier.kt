package com.huevoman77.cazahosts.dns

import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Clasifica que servicio web responde en un subdominio: si contesta por HTTP
 * directo, si el TLS funciona con su propio nombre (SNI) y si acepta un
 * payload con cuerpo. Ademas lee el certificado para saber quien lo emitio y
 * si cubre ese nombre.
 */
object WebClassifier {

    private const val USER_AGENT = "Mozilla/5.0 (CazaHosts)"

    data class CertInfo(
        val subject: String = "",
        val issuer: String = "",
        val commonName: String = "",
        val sans: List<String> = emptyList(),
        val notBefore: String = "",
        val notAfter: String = "",
        val daysLeft: Int = -1,
        val matchesHost: Boolean = false
    )

    data class Report(
        val directStatus: Int = 0,
        val directTitle: String = "",
        val directOk: Boolean = false,
        val sniStatus: Int = 0,
        val sniTitle: String = "",
        val sniOk: Boolean = false,
        val sniRequired: Boolean = false,
        val payloadStatus: Int = 0,
        val cert: CertInfo? = null
    ) {
        /** Estado HTTP mas representativo del host. */
        val httpStatus: Int
            get() = when {
                sniStatus > 0 -> sniStatus
                directStatus > 0 -> directStatus
                else -> payloadStatus
            }

        val httpTitle: String
            get() = if (sniStatus > 0) sniTitle else directTitle

        /** El servidor respondio al POST: acepta el payload aunque lo redirija. */
        val payloadOk: Boolean get() = payloadStatus in 200..399

        val anyOk: Boolean get() = directOk || sniOk || payloadOk
    }

    private class Probe(val status: Int, val title: String)

    /**
     * Acepta cualquier certificado y guarda el que envia el servidor: aqui
     * interesa saber que dice el certificado, no si la cadena es valida.
     */
    private class CapturingTrustManager : X509TrustManager {
        @Volatile
        var chain: List<X509Certificate> = emptyList()

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            this.chain = chain?.mapNotNull { it as? X509Certificate } ?: emptyList()
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    fun classify(host: String, ip: String, timeoutMs: Int): Report {
        val client = client(timeoutMs, ip)

        val direct = request(client, "http://$host/", "GET")
        val tls = request(client, "https://$host/", "GET")

        val postHttp = request(client, "http://$host/", "POST")
        val postHttps = request(client, "https://$host/", "POST")
        val payload = best(postHttp, postHttps)

        val withSni = peerCertificate(host, ip, timeoutMs)
        val withoutSni = peerCertificate(null, ip, timeoutMs)
        val info = withSni?.let { certInfo(it, host) }
        val sniRequired = withSni != null &&
            (withoutSni == null || withoutSni.subjectX500Principal.name != withSni.subjectX500Principal.name)

        return Report(
            directStatus = direct.status,
            directTitle = direct.title,
            directOk = direct.status > 0,
            sniStatus = tls.status,
            sniTitle = tls.title,
            sniOk = tls.status > 0 && info?.matchesHost == true,
            sniRequired = sniRequired,
            payloadStatus = payload.status,
            cert = info
        )
    }

    /** El mejor resultado entre dos sondeos: gana el 2xx y luego el 3xx. */
    private fun best(a: Probe, b: Probe): Probe = when {
        a.status in 200..299 -> a
        b.status in 200..299 -> b
        a.status in 300..399 -> a
        b.status in 300..399 -> b
        a.status > 0 -> a
        else -> b
    }

    private fun client(timeoutMs: Int, ip: String): OkHttpClient {
        val manager = CapturingTrustManager()
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf<TrustManager>(manager), SecureRandom())
        return OkHttpClient.Builder()
            .sslSocketFactory(ctx.socketFactory, manager)
            .hostnameVerifier { _, _ -> true }
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> = try {
                    listOf(InetAddress.getByName(ip))
                } catch (e: Exception) {
                    emptyList()
                }
            })
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .callTimeout((timeoutMs * 2).toLong(), TimeUnit.MILLISECONDS)
            .build()
    }

    private fun request(client: OkHttpClient, url: String, method: String): Probe {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
        if (method == "POST") {
            builder.post("{\"probe\":\"cazahosts\"}".toRequestBody("application/json".toMediaType()))
        } else {
            builder.get()
        }
        return try {
            client.newCall(builder.build()).execute().use { response ->
                val text = try {
                    response.peekBody(65536).string()
                } catch (e: Exception) {
                    ""
                }
                Probe(response.code, title(text))
            }
        } catch (e: Exception) {
            Probe(0, "")
        }
    }

    /** Titulo de la pagina, para reconocer la aplicacion que responde. */
    internal fun title(body: String): String =
        Regex("(?is)<title[^>]*>(.*?)</title>")
            .find(body)?.groupValues?.get(1)?.trim()?.take(120) ?: ""

    private fun certInfo(cert: X509Certificate, host: String): CertInfo {
        val sans = cert.subjectAlternativeNames
            ?.filter { it != null && it.size > 1 && it[0] == 2 }
            ?.mapNotNull { it[1] as? String }
            ?: emptyList()
        return CertInfo(
            subject = cert.subjectX500Principal.name,
            issuer = cert.issuerX500Principal.name,
            commonName = commonName(cert.subjectX500Principal.name),
            sans = sans,
            notBefore = cert.notBefore.toString(),
            notAfter = cert.notAfter.toString(),
            daysLeft = ((cert.notAfter.time - System.currentTimeMillis()) / 86400000L).toInt(),
            matchesHost = matchesHost(host, cert, sans)
        )
    }

    /** El certificado debe cubrir el nombre del host: SAN primero, CN de respaldo. */
    internal fun matchesHost(host: String, cert: X509Certificate, sans: List<String>): Boolean {
        if (sans.isNotEmpty()) return sans.any { matchesName(host, it) }
        val cn = commonName(cert.subjectX500Principal.name)
        return cn.isNotEmpty() && matchesName(host, cn)
    }

    internal fun commonName(subject: String): String {
        val match = Regex("(?:^|,)\\s*CN=([^,]+)").find(subject) ?: return ""
        return match.groupValues[1].trim()
    }

    /** Compara un nombre con un patron de certificado, respetando el comodin de un nivel. */
    internal fun matchesName(host: String, pattern: String): Boolean {
        val h = host.trim().lowercase().trimEnd('.')
        val p = pattern.trim().lowercase().trimEnd('.')
        if (h.isEmpty() || p.isEmpty()) return false
        if (h == p) return true
        if (!p.startsWith("*.")) return false
        val suffix = p.substring(1)
        if (!h.endsWith(suffix)) return false
        val label = h.dropLast(suffix.length)
        return label.isNotEmpty() && !label.contains('.')
    }

    /**
     * Certificado que entrega el servidor. Con `host` se envia SNI: asi se ve
     * que certificado elige para ese nombre. Sin host se ve que pasa cuando
     * el cliente no dice que nombre busca.
     */
    private fun peerCertificate(host: String?, ip: String, timeoutMs: Int): X509Certificate? {
        val manager = CapturingTrustManager()
        return try {
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf<TrustManager>(manager), SecureRandom())
            val socket = ctx.socketFactory.createSocket() as SSLSocket
            socket.use {
                it.connect(InetSocketAddress(ip, 443), timeoutMs)
                it.soTimeout = timeoutMs
                it.useClientMode = true
                if (host != null) {
                    val params = it.sslParameters
                    params.serverNames = listOf(SNIHostName(host.trim().trimEnd('.')))
                    it.sslParameters = params
                }
                it.startHandshake()
                val fromSession = try {
                    it.session.peerCertificates.firstOrNull() as? X509Certificate
                } catch (e: Exception) {
                    null
                }
                fromSession ?: manager.chain.firstOrNull()
            }
        } catch (e: Exception) {
            manager.chain.firstOrNull()
        }
    }
}
