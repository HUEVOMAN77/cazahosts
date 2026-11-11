package com.huevoman77.cazahosts.creds

import com.huevoman77.cazahosts.core.Net
import kotlinx.coroutines.delay
import java.io.InputStream
import java.net.Socket
import java.util.Base64

data class CredTarget(val host: String, val port: Int, val protocol: String)

data class CredResult(
    val host: String,
    val port: Int,
    val protocol: String,
    val user: String,
    val password: String,
    val success: Boolean,
    val detail: String
)

/**
 * Auditoría de credenciales para equipos de tu propia red.
 * Limitada por diseño: tope de intentos por host, ritmo bajo y sin evasión.
 */
class CredentialAuditor(
    private val ratePerSecond: Int = 3,
    private val maxPerHost: Int = 20
) {

    private var lastAttempt = 0L

    companion object {
        val DEFAULTS: List<Pair<String, String>> = listOf(
            "admin" to "admin",
            "admin" to "password",
            "admin" to "1234",
            "admin" to "12345",
            "admin" to "admin1234",
            "root" to "root",
            "root" to "toor",
            "root" to "1234",
            "user" to "user",
            "guest" to "guest",
            "test" to "test",
            "support" to "support",
            "administrator" to "administrator",
            "ubnt" to "ubnt",
            "pi" to "pi",
            "default" to "default",
            "operator" to "operator"
        )
    }

    private suspend fun throttle() {
        val interval = if (ratePerSecond <= 0) 1000L else 1000L / ratePerSecond
        val now = System.currentTimeMillis()
        val wait = lastAttempt + interval - now
        if (wait > 0) delay(wait)
        lastAttempt = System.currentTimeMillis()
    }

    suspend fun audit(
        targets: List<CredTarget>,
        users: List<String>,
        passwords: List<String>,
        onResult: (CredResult) -> Unit
    ): List<CredResult> {
        val results = mutableListOf<CredResult>()
        val combos = buildCombos(users, passwords)
        for (target in targets) {
            var attempts = 0
            for ((user, pass) in combos) {
                if (attempts >= maxPerHost) break
                throttle()
                val result = try {
                    when (target.protocol) {
                        "ftp" -> tryFtp(target, user, pass)
                        "http" -> tryHttpBasic(target, user, pass)
                        "telnet" -> tryTelnet(target, user, pass)
                        else -> null
                    }
                } catch (e: Exception) {
                    null
                }
                attempts++
                if (result != null) {
                    results += result
                    onResult(result)
                    if (result.success) break
                }
            }
        }
        return results
    }

    private fun buildCombos(users: List<String>, passwords: List<String>): List<Pair<String, String>> {
        val out = LinkedHashSet<Pair<String, String>>()
        val cleanUsers = users.map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { DEFAULTS.map { it.first } }
        val cleanPass = passwords.map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { DEFAULTS.map { it.second } }
        for (u in cleanUsers) {
            for (p in cleanPass) {
                out += u to p
            }
        }
        for (pair in DEFAULTS) {
            if (cleanUsers.contains(pair.first) || cleanUsers.isEmpty()) out += pair
        }
        return out.toList()
    }

    private fun tryFtp(target: CredTarget, user: String, pass: String): CredResult? {
        val socket = Net.connect(target.host, target.port, 4000) ?: return null
        return try {
            socket.soTimeout = 4000
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            val greeting = readLine(input) ?: return null
            if (!greeting.startsWith("220")) return null
            output.write("USER $user\r\n".toByteArray()); output.flush()
            val userResp = readLine(input) ?: return null
            if (userResp.startsWith("230")) {
                return CredResult(target.host, target.port, "ftp", user, "", true, "Login aceptado sin contraseña")
            }
            if (!userResp.startsWith("331")) {
                return CredResult(target.host, target.port, "ftp", user, pass, false, userResp.take(80))
            }
            output.write("PASS $pass\r\n".toByteArray()); output.flush()
            val passResp = readLine(input) ?: return null
            val ok = passResp.startsWith("230")
            CredResult(target.host, target.port, "ftp", user, pass, ok, passResp.take(80))
        } catch (e: Exception) {
            null
        } finally {
            close(socket)
        }
    }

    private fun tryHttpBasic(target: CredTarget, user: String, pass: String): CredResult? {
        val socket = Net.connect(target.host, target.port, 4000) ?: return null
        return try {
            socket.soTimeout = 4000
            val token = Base64.getEncoder().encodeToString("$user:$pass".toByteArray())
            val request = buildString {
                append("GET / HTTP/1.0\r\n")
                append("Host: ").append(target.host).append("\r\n")
                append("Authorization: Basic ").append(token).append("\r\n")
                append("User-Agent: Mozilla/5.0\r\nConnection: close\r\n\r\n")
            }
            socket.getOutputStream().write(request.toByteArray())
            socket.getOutputStream().flush()
            val raw = readAll(socket.getInputStream())
            val status = Regex("^HTTP/[0-9.]+\\s+(\\d{3})").find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: return null
            val ok = status in 200..399
            val detail = if (ok) "HTTP $status - acceso concedido" else "HTTP $status - rechazado"
            CredResult(target.host, target.port, "http", user, pass, ok, detail)
        } catch (e: Exception) {
            null
        } finally {
            close(socket)
        }
    }

    private fun tryTelnet(target: CredTarget, user: String, pass: String): CredResult? {
        val socket = Net.connect(target.host, target.port, 5000) ?: return null
        return try {
            socket.soTimeout = 5000
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            val banner = readUntil(input, listOf("login:", "username:", "Login:"), 3000)
            if (banner.isEmpty()) return null
            output.write("$user\r\n".toByteArray()); output.flush()
            val afterUser = readUntil(input, listOf("Password:", "password:", "Passphrase"), 3000)
            if (afterUser.isEmpty()) {
                val maybe = readUntil(input, listOf("Login incorrect", "incorrect", "BusyBox", "#", "$", ">"), 2000)
                val ok = maybe.contains("BusyBox") || maybe.trim().endsWith("#") || maybe.trim().endsWith("$")
                return CredResult(target.host, target.port, "telnet", user, "", ok, maybe.take(80))
            }
            output.write("$pass\r\n".toByteArray()); output.flush()
            val result = readUntil(input, listOf("Login incorrect", "incorrect", "Authentication failed", "BusyBox", "#", "$", ">"), 3000)
            val failed = result.contains("incorrect", true) || result.contains("failed", true) || result.contains("Invalid", true)
            val ok = !failed && (result.contains("BusyBox") || result.contains("#") || result.contains("$") || result.contains(">"))
            CredResult(target.host, target.port, "telnet", user, pass, ok, result.take(80))
        } catch (e: Exception) {
            null
        } finally {
            close(socket)
        }
    }

    private fun close(socket: Socket) {
        try {
            socket.close()
        } catch (ignored: Exception) {
        }
    }

    private fun readAll(stream: InputStream, limit: Int = 8192): String {
        val buffer = ByteArray(2048)
        val out = StringBuilder()
        val start = System.currentTimeMillis()
        try {
            while (out.length < limit && System.currentTimeMillis() - start < 4000) {
                val n = stream.read(buffer)
                if (n <= 0) break
                out.append(String(buffer, 0, n, Charsets.ISO_8859_1))
            }
        } catch (e: Exception) {
        }
        return out.toString()
    }

    private fun readLine(stream: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = try {
                stream.read()
            } catch (e: Exception) {
                return if (sb.isEmpty()) null else sb.toString().trim()
            }
            if (c == -1) return if (sb.isEmpty()) null else sb.toString().trim()
            if (c == '\n'.code) return sb.toString().trim()
            if (c != '\r'.code) sb.append(c.toChar())
            if (sb.length > 1024) return sb.toString().trim()
        }
    }

    private fun readUntil(stream: InputStream, markers: List<String>, timeoutMs: Int): String {
        val sb = StringBuilder()
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val c = try {
                stream.read()
            } catch (e: Exception) {
                break
            }
            if (c == -1) break
            sb.append(c.toChar())
            val text = sb.toString()
            if (markers.any { text.contains(it, ignoreCase = true) }) break
            if (sb.length > 2048) break
        }
        return sb.toString()
    }
}
