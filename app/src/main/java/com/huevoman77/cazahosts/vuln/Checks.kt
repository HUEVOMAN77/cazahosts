package com.huevoman77.cazahosts.vuln

import com.huevoman77.cazahosts.core.Net
import com.huevoman77.cazahosts.data.PortFinding
import com.huevoman77.cazahosts.data.Severity
import com.huevoman77.cazahosts.data.VulnFinding
import java.io.InputStream
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Comprobaciones activas no destructivas: sólo leen información del servicio
 * (GET, PING, USER anonymous) para confirmar exposición real.
 */
object Checks {

    data class HttpResponse(val status: Int, val body: String, val headers: String) {
        val ok: Boolean get() = status in 200..399
        val title: String
            get() = Regex("(?is)<title[^>]*>(.*?)</title>").find(body)?.groupValues?.get(1)?.trim() ?: ""
    }

    data class TlsInfo(val version: String, val expiry: String, val daysLeft: Int, val subject: String)

    private val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    fun tlsSocket(ip: String, port: Int, timeoutMs: Int): Socket? = try {
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
        val raw = Net.connect(ip, port, timeoutMs) ?: return null
        raw.soTimeout = timeoutMs
        val ssl = ctx.socketFactory.createSocket(raw, ip, port, true) as SSLSocket
        ssl.soTimeout = timeoutMs
        ssl.startHandshake()
        ssl
    } catch (e: Exception) {
        null
    }

    fun tlsInfo(socket: Socket): TlsInfo? {
        if (socket !is SSLSocket) return null
        return try {
            val session = socket.session
            val cert = try {
                session.peerCertificates.firstOrNull() as? X509Certificate
            } catch (e: Exception) {
                null
            }
            var expiry = ""
            var days = -1
            var subject = ""
            if (cert != null) {
                expiry = cert.notAfter.toString()
                days = ((cert.notAfter.time - System.currentTimeMillis()) / 86400000L).toInt()
                subject = cert.subjectX500Principal.name
            }
            TlsInfo(session.protocol ?: "", expiry, days, subject)
        } catch (e: Exception) {
            null
        }
    }

    fun httpGet(ip: String, port: Int, path: String, timeoutMs: Int, useTls: Boolean): HttpResponse? {
        var socket: Socket? = null
        return try {
            socket = if (useTls) tlsSocket(ip, port, timeoutMs) else Net.connect(ip, port, timeoutMs)
            if (socket == null) return null
            socket.soTimeout = timeoutMs
            val request = buildString {
                append("GET ").append(path).append(" HTTP/1.0\r\n")
                append("Host: ").append(ip).append(':').append(port).append("\r\n")
                append("User-Agent: Mozilla/5.0 (CazaHosts)\r\n")
                append("Accept: */*\r\n")
                append("Connection: close\r\n\r\n")
            }
            socket.getOutputStream().write(request.toByteArray())
            socket.getOutputStream().flush()
            val raw = readAll(socket.getInputStream(), 16384)
            parseHttp(raw)
        } catch (e: Exception) {
            null
        } finally {
            try {
                socket?.close()
            } catch (ignored: Exception) {
            }
        }
    }

    fun parseHttp(raw: String): HttpResponse? {
        if (raw.isEmpty()) return null
        val idx = raw.indexOf("\r\n\r\n")
        val head = if (idx >= 0) raw.substring(0, idx) else raw
        val body = if (idx >= 0) raw.substring(idx + 4) else ""
        val status = Regex("^HTTP/[0-9.]+\\s+(\\d{3})").find(head)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        return HttpResponse(status, body, head)
    }

    fun readAll(stream: InputStream, limit: Int): String {
        val buffer = ByteArray(4096)
        val out = StringBuilder()
        val start = System.currentTimeMillis()
        try {
            while (out.length < limit && System.currentTimeMillis() - start < 4000) {
                val n = stream.read(buffer)
                if (n <= 0) break
                out.append(String(buffer, 0, n, Charsets.ISO_8859_1))
            }
        } catch (e: Exception) {
            // timeout o cierre: se devuelve lo leído
        }
        return out.toString()
    }

    /** FTP: intenta login anónimo (solo lectura de la respuesta). */
    fun ftpAnonymous(ip: String, port: Int, timeoutMs: Int): Boolean {
        val socket = Net.connect(ip, port, timeoutMs) ?: return false
        return try {
            socket.soTimeout = timeoutMs
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            val greeting = readLine(input) ?: return false
            if (!greeting.startsWith("220")) return false
            output.write("USER anonymous\r\n".toByteArray()); output.flush()
            val user = readLine(input) ?: return false
            if (user.startsWith("230")) return true
            if (!user.startsWith("331")) return false
            output.write("PASS cazahosts@example.com\r\n".toByteArray()); output.flush()
            val pass = readLine(input) ?: return false
            pass.startsWith("230")
        } catch (e: Exception) {
            false
        } finally {
            try {
                socket.close()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        var c: Int
        while (true) {
            c = input.read()
            if (c == -1) return if (sb.isEmpty()) null else sb.toString().trim()
            if (c == '\n'.code) return sb.toString().trim()
            if (c != '\r'.code) sb.append(c.toChar())
            if (sb.length > 512) return sb.toString().trim()
        }
    }

    /** Redis: PING sin credenciales. */
    fun redisOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        val socket = Net.connect(ip, port, timeoutMs) ?: return false
        return try {
            socket.soTimeout = timeoutMs
            socket.getOutputStream().write("PING\r\n".toByteArray())
            socket.getOutputStream().flush()
            val res = readLine(socket.getInputStream()) ?: ""
            res.startsWith("+PONG")
        } catch (e: Exception) {
            false
        } finally {
            try {
                socket.close()
            } catch (ignored: Exception) {
            }
        }
    }

    /** Memcached: versión sin autenticación. */
    fun memcachedOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        val socket = Net.connect(ip, port, timeoutMs) ?: return false
        return try {
            socket.soTimeout = timeoutMs
            socket.getOutputStream().write("version\r\n".toByteArray())
            socket.getOutputStream().flush()
            val res = readLine(socket.getInputStream()) ?: ""
            res.startsWith("VERSION")
        } catch (e: Exception) {
            false
        } finally {
            try {
                socket.close()
            } catch (ignored: Exception) {
            }
        }
    }

    /** MongoDB: isMaster no autenticado (confirmación de servicio). */
    fun mongoOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        val socket = Net.connect(ip, port, timeoutMs) ?: return false
        return try {
            socket.soTimeout = timeoutMs
            val doc = byteArrayOf(0x10) + "isMaster".toByteArray() + byteArrayOf(0x00) +
                byteArrayOf(0x01, 0x00, 0x00, 0x00)
            val body = doc
            val collection = "admin.\$cmd".toByteArray() + byteArrayOf(0x00)
            val length = 16 + 4 + collection.size + 8 + body.size
            val out = java.io.ByteArrayOutputStream()
            out.write(intBytes(length))
            out.write(intBytes(1))
            out.write(intBytes(0))
            out.write(intBytes(2004))
            out.write(intBytes(0))
            out.write(collection)
            out.write(intBytes(0))
            out.write(intBytes(1))
            out.write(body)
            socket.getOutputStream().write(out.toByteArray())
            socket.getOutputStream().flush()
            val header = ByteArray(4)
            val read = socket.getInputStream().read(header)
            read == 4 && header[0] != 0.toByte()
        } catch (e: Exception) {
            false
        } finally {
            try {
                socket.close()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun intBytes(v: Int): ByteArray = byteArrayOf(
        ((v shr 24) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        (v and 0xFF).toByte()
    )

    // ---------------------------------------------------------------- checks

    private val sensitivePaths = listOf(
        "/.env" to "Archivo .env expuesto",
        "/.git/config" to "Repositorio Git expuesto",
        "/.git/HEAD" to "Repositorio Git expuesto",
        "/.aws/credentials" to "Credenciales de AWS expuestas",
        "/wp-config.php.bak" to "Copia de configuración de WordPress",
        "/config.php.bak" to "Copia de configuración PHP",
        "/backup.zip" to "Copia de seguridad expuesta",
        "/site.zip" to "Copia de seguridad expuesta",
        "/db.sql" to "Volcado de base de datos expuesto",
        "/dump.sql" to "Volcado de base de datos expuesto",
        "/server-status" to "Módulo server-status de Apache habilitado"
    )

    private val adminPaths = listOf(
        "/phpmyadmin/" to "phpMyAdmin accesible",
        "/wp-login.php" to "WordPress instalado",
        "/administrator/" to "Panel de administración accesible",
        "/manager/html" to "Consola de Tomcat accesible",
        "/jmx-console/" to "Consola JMX de JBoss accesible",
        "/actuator/env" to "Endpoint de Spring Boot expuesto",
        "/actuator/health" to "Endpoint de Spring Boot expuesto",
        "/swagger-ui.html" to "Documentación Swagger expuesta",
        "/solr/admin" to "Consola de Solr accesible",
        "/admin/config.php" to "Consola de Jenkins accesible",
        "/cgi-bin/" to "Directorio cgi-bin accesible",
        "/.htaccess" to "Archivo .htaccess expuesto",
        "/.DS_Store" to "Metadatos .DS_Store expuestos"
    )

    fun httpFindings(ip: String, port: Int, timeoutMs: Int, useTls: Boolean): List<VulnFinding> {
        val out = mutableListOf<VulnFinding>()
        val root = httpGet(ip, port, "/", timeoutMs, useTls) ?: return out

        if (root.body.contains("Index of /")) {
            out += VulnFinding(
                id = "dir-listing",
                severity = Severity.MEDIA.name,
                title = "Listado de directorios habilitado",
                detail = "El servidor web muestra el índice del contenido de las carpetas.",
                fix = "Deshabilitar el autoindex o proteger con un archivo .htaccess/Index.",
                port = port
            )
        }
        if (root.body.contains("wp-content") || root.body.contains("wp-includes")) {
            out += VulnFinding(
                id = "wordpress-detected",
                severity = Severity.INFO.name,
                title = "WordPress detectado",
                detail = "Se identificaron rastros de una instalación de WordPress en la raíz del sitio.",
                fix = "Mantener WordPress y sus plugins actualizados y ocultar la versión.",
                port = port
            )
        }
        if (root.body.contains("grafanaBootData")) {
            out += VulnFinding(
                id = "grafana-detected",
                severity = Severity.MEDIA.name,
                title = "Grafana accesible",
                detail = "La consola de Grafana está publicada y puede exponer paneles y credenciales guardadas.",
                fix = "Proteger con autenticación y restringir por red.",
                port = port
            )
        }

        out += headerFindings(root.headers, port)

        for ((path, title) in sensitivePaths) {
            val res = httpGet(ip, port, path, timeoutMs, useTls) ?: continue
            if (res.status == 200 && res.body.length > 5) {
                out += VulnFinding(
                    id = "sensitive-file-$path",
                    severity = Severity.ALTA.name,
                    title = title,
                    detail = "La ruta $path respondió 200 y expone contenido del servidor.",
                    fix = "Eliminar el archivo del servidor y bloquear el acceso a archivos ocultos.",
                    port = port
                )
            }
        }
        for ((path, title) in adminPaths) {
            val res = httpGet(ip, port, path, timeoutMs, useTls) ?: continue
            if (res.status in 200..399) {
                out += VulnFinding(
                    id = "admin-path-$path",
                    severity = Severity.INFO.name,
                    title = title,
                    detail = "La ruta $path respondió ${res.status}.",
                    fix = "Restringir el acceso a paneles administrativos por IP o VPN.",
                    port = port
                )
            }
        }
        return out
    }

    fun headerFindings(headers: String, port: Int): List<VulnFinding> {
        val out = mutableListOf<VulnFinding>()
        val h = headers.lowercase()
        if (h.isEmpty()) return out
        val missing = mutableListOf<String>()
        if (!h.contains("x-content-type-options")) missing += "X-Content-Type-Options"
        if (!h.contains("x-frame-options") && !h.contains("content-security-policy")) missing += "X-Frame-Options"
        if (!h.contains("strict-transport-security") && h.contains("https")) missing += "Strict-Transport-Security"
        if (missing.isNotEmpty()) {
            out += VulnFinding(
                id = "missing-security-headers",
                severity = Severity.BAJA.name,
                title = "Faltan cabeceras de seguridad HTTP",
                detail = "No se encontraron: ${missing.joinToString(", ")}.",
                fix = "Agregar las cabeceras en el servidor web o en el proxy inverso.",
                port = port
            )
        }
        val cookie = Regex("(?im)^set-cookie:\\s*(.+)$").find(headers)
        if (cookie != null && !cookie.groupValues[1].contains("secure", true) &&
            !cookie.groupValues[1].contains("httponly", true)
        ) {
            out += VulnFinding(
                id = "insecure-cookie",
                severity = Severity.BAJA.name,
                title = "Cookie de sesión sin atributos de seguridad",
                detail = "La cookie no marca Secure ni HttpOnly.",
                fix = "Agregar los atributos Secure, HttpOnly y SameSite.",
                port = port
            )
        }
        return out
    }

    fun tlsFindings(info: TlsInfo, port: Int): List<VulnFinding> {
        val out = mutableListOf<VulnFinding>()
        if (info.daysLeft in 0..30) {
            out += VulnFinding(
                id = "cert-expiring",
                severity = Severity.BAJA.name,
                title = "El certificado TLS vence pronto",
                detail = "El certificado expira en ${info.daysLeft} días (${info.expiry}).",
                fix = "Renovar y desplegar el certificado antes de la fecha límite.",
                port = port
            )
        } else if (info.daysLeft < 0) {
            out += VulnFinding(
                id = "cert-expired",
                severity = Severity.MEDIA.name,
                title = "El certificado TLS está vencido",
                detail = "El certificado expiró el ${info.expiry}.",
                fix = "Renovar el certificado inmediatamente.",
                port = port
            )
        }
        if (info.version == "TLSv1" || info.version == "TLSv1.1" || info.version == "SSLv3") {
            out += VulnFinding(
                id = "weak-tls",
                severity = Severity.MEDIA.name,
                title = "Protocolo TLS obsoleto en uso (${info.version})",
                detail = "El servidor acepta versiones de TLS retiradas por RFC 8996.",
                fix = "Habilitar únicamente TLS 1.2 y 1.3.",
                port = port
            )
        }
        return out
    }

    /** Ejecuta las comprobaciones activas que apliquen al servicio detectado. */
    suspend fun activeChecks(ip: String, port: PortFinding, timeoutMs: Int): List<VulnFinding> {
        val out = mutableListOf<VulnFinding>()
        when (port.service) {
            "http", "http-proxy", "http-alt", "https", "https-alt" -> {
                out += httpFindings(ip, port.port, timeoutMs, port.tls)
            }
            "ftp" -> {
                if (ftpAnonymous(ip, port.port, timeoutMs)) {
                    out += VulnFinding(
                        id = "ftp-anonymous",
                        severity = Severity.MEDIA.name,
                        title = "Acceso anónimo a FTP habilitado",
                        detail = "El servidor acepta el usuario 'anonymous' sin contraseña.",
                        fix = "Deshabilitar el acceso anónimo y exigir credenciales.",
                        port = port.port
                    )
                }
            }
            "redis" -> {
                if (redisOpen(ip, port.port, timeoutMs)) {
                    out += VulnFinding(
                        id = "redis-open",
                        severity = Severity.MEDIA.name,
                        title = "Redis acepta comandos sin contraseña",
                        detail = "El servidor respondió +PONG a una consulta sin credenciales.",
                        fix = "Configurar requirepass y limitar la escucha a localhost.",
                        port = port.port
                    )
                }
            }
            "memcached" -> {
                if (memcachedOpen(ip, port.port, timeoutMs)) {
                    out += VulnFinding(
                        id = "memcached-open",
                        severity = Severity.MEDIA.name,
                        title = "Memcached responde sin autenticación",
                        detail = "El servidor devolvió su versión sin credenciales.",
                        fix = "Limitar la escucha a 127.0.0.1 y filtrar por firewall.",
                        port = port.port
                    )
                }
            }
            "mongodb" -> {
                if (mongoOpen(ip, port.port, timeoutMs)) {
                    out += VulnFinding(
                        id = "mongodb-open",
                        severity = Severity.MEDIA.name,
                        title = "MongoDB accesible sin autenticación",
                        detail = "El servidor responde al comando isMaster sin credenciales.",
                        fix = "Habilitar autenticación y restringir las conexiones.",
                        port = port.port
                    )
                }
            }
            "docker-api" -> {
                val res = httpGet(ip, port.port, "/version", timeoutMs, false)
                if (res != null && res.ok) {
                    out += VulnFinding(
                        id = "docker-api-open",
                        severity = Severity.CRITICA.name,
                        title = "API de Docker abierta sin autenticación",
                        detail = "El endpoint /version respondió ${res.status}: control total del host.",
                        fix = "Cerrar el puerto 2375, habilitar TLS y filtrar por firewall.",
                        port = port.port
                    )
                }
            }
            "elasticsearch" -> {
                val res = httpGet(ip, port.port, "/", timeoutMs, false)
                if (res != null && res.ok && res.body.contains("cluster_name")) {
                    out += VulnFinding(
                        id = "elasticsearch-open",
                        severity = Severity.MEDIA.name,
                        title = "Elasticsearch responde sin autenticación",
                        detail = "El clúster devuelve su información sin credenciales.",
                        fix = "Activar X-Pack Security o proteger con proxy de autenticación.",
                        port = port.port
                    )
                }
            }
        }
        return out
    }
}
