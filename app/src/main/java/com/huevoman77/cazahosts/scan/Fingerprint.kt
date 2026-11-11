package com.huevoman77.cazahosts.scan

import com.huevoman77.cazahosts.core.IpUtils

/** Resultado de identificar un servicio a partir del puerto y el banner. */
data class ServiceInfo(
    val service: String,
    val product: String = "",
    val version: String = "",
    val os: String = "",
    val extra: String = ""
)

object Fingerprint {

    private val ports = mapOf(
        7 to "echo", 20 to "ftp-data", 21 to "ftp", 22 to "ssh", 23 to "telnet",
        25 to "smtp", 37 to "time", 53 to "dns", 67 to "dhcp", 69 to "tftp",
        79 to "finger", 80 to "http", 81 to "http", 88 to "kerberos",
        102 to "s7comm", 110 to "pop3", 111 to "sunrpc", 113 to "ident",
        119 to "nntp", 123 to "ntp", 135 to "msrpc", 137 to "netbios-ns",
        138 to "netbios-dgm", 139 to "netbios-ssn", 143 to "imap", 161 to "snmp",
        179 to "bgp", 389 to "ldap", 427 to "svrloc", 443 to "https",
        445 to "smb", 465 to "smtps", 512 to "exec", 513 to "login",
        514 to "syslog", 515 to "printer", 548 to "afp", 554 to "rtsp",
        587 to "submission", 623 to "ipmi", 636 to "ldaps", 873 to "rsync",
        993 to "imaps", 995 to "pop3s", 1080 to "socks", 1433 to "mssql",
        1521 to "oracle", 1883 to "mqtt", 2049 to "nfs", 2181 to "zookeeper",
        2375 to "docker-api", 2376 to "docker-tls", 2379 to "etcd",
        3000 to "http-alt", 3306 to "mysql", 3389 to "rdp", 3690 to "svn",
        4369 to "epmd", 5000 to "http-alt", 5001 to "http-alt", 5222 to "xmpp",
        5432 to "postgresql", 5672 to "amqp", 5900 to "vnc", 5985 to "winrm",
        6000 to "x11", 6379 to "redis", 6443 to "kubernetes", 6667 to "irc",
        7001 to "weblogic", 8000 to "http-alt", 8008 to "http-alt", 8080 to "http-proxy",
        8081 to "http-alt", 8088 to "http-alt", 8089 to "splunkd", 8443 to "https-alt",
        8888 to "http-alt", 9000 to "php-fpm", 9090 to "prometheus", 9200 to "elasticsearch",
        9300 to "elasticsearch", 9418 to "git", 9999 to "http-alt", 10000 to "webmin",
        11211 to "memcached", 15672 to "rabbitmq", 27017 to "mongodb",
        28017 to "mongodb-web", 50000 to "db2", 50070 to "hadoop", 61616 to "activemq",
        9100 to "jetdirect", 11211 to "memcached", 19200 to "sas", 50000 to "db2"
    )

    private val topPorts = listOf(
        21, 22, 23, 25, 53, 80, 81, 88, 110, 111, 113, 135, 139, 143, 161, 179,
        389, 443, 445, 464, 465, 514, 587, 623, 636, 873, 993, 995, 1080, 1099,
        1433, 1521, 1883, 2049, 2181, 2375, 2376, 2379, 3000, 3306, 3389, 3690,
        4444, 5000, 5001, 5222, 5432, 5672, 5900, 5985, 6000, 6379, 6443, 6667,
        7001, 7077, 8000, 8008, 8009, 8080, 8081, 8088, 8089, 8091, 8161, 8181,
        8443, 8888, 9000, 9001, 9042, 9090, 9092, 9200, 9300, 9418, 9999, 10000,
        10250, 11211, 15672, 27017, 28017, 50000, 61616
    )

    fun topPortsList(): List<Int> = topPorts.distinct().sorted()

    /** Lista de puertos según el perfil elegido por el usuario. */
    fun portsForPreset(preset: String, custom: String): List<Int> = when (preset) {
        "expandido" -> {
            val set = sortedSetOf<Int>()
            set += topPorts
            set += (1..1024)
            set += (2000..2020)
            set += (3000..3010)
            set += (3300..3400)
            set += (4000..4010)
            set += (5000..5010)
            set += (5432..5433)
            set += (5900..5910)
            set += (6379..6380)
            set += (7000..7010)
            set += (8000..8100)
            set += (8443..8444)
            set += (8880..8890)
            set += (9000..9010)
            set += (9090..9100)
            set += (9200..9201)
            set += (11211..11211)
            set += (15672..15672)
            set += (27017..27018)
            set.toList()
        }
        "todos" -> (1..65535).toList()
        "personalizado" -> parsePorts(custom)
        else -> topPortsList()
    }

    fun parsePorts(text: String): List<Int> {
        if (text.isBlank()) return topPortsList()
        val out = sortedSetOf<Int>()
        text.split(',', ';', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }.forEach { token ->
            if (token.contains('-')) {
                val parts = token.split('-')
                val from = parts.getOrNull(0)?.trim()?.toIntOrNull()
                val to = parts.getOrNull(1)?.trim()?.toIntOrNull()
                if (from != null && to != null && to >= from) {
                    for (p in from..minOf(to, 65535)) if (p in 1..65535) out += p
                }
            } else {
                val p = token.toIntOrNull()
                if (p != null && p in 1..65535) out += p
            }
        }
        return out.toList()
    }

    fun serviceName(port: Int): String = ports[port] ?: "desconocido"

    fun identify(port: Int, banner: String): ServiceInfo {
        val b = banner.trim()
        val default = ServiceInfo(serviceName(port))
        if (b.isEmpty()) return default

        // SSH
        val ssh = Regex("^SSH-([\\d.]+)-([^\\s]+)").find(b)
        if (ssh != null) {
            val software = ssh.groupValues[2]
            val os = when {
                software.contains("Ubuntu") -> "Ubuntu"
                software.contains("Debian") -> "Debian"
                software.contains("CentOS") -> "CentOS"
                software.contains("Rocky") -> "Rocky Linux"
                software.contains("Alpine") -> "Alpine"
                software.contains("openEuler") -> "openEuler"
                software.contains("Amazon") -> "Amazon Linux"
                software.contains("FreeBSD") -> "FreeBSD"
                software.contains("OpenBSD") -> "OpenBSD"
                else -> ""
            }
            return ServiceInfo("ssh", software.substringBefore('_'), software.substringAfter('_', ""), os, b)
        }

        // FTP
        val ftp = Regex("^220[- ]([^\\r\\n]+)").find(b)
        if (ftp != null) {
            val line = ftp.groupValues[1].trim()
            val product = when {
                line.contains("vsftpd", true) -> "vsftpd"
                line.contains("ProFTPD", true) -> "ProFTPD"
                line.contains("Pure-FTPd", true) -> "Pure-FTPd"
                line.contains("FileZilla", true) -> "FileZilla Server"
                line.contains("Microsoft FTP Service", true) -> "Microsoft FTP Service"
                line.contains("Serv-U FTP", true) -> "Serv-U"
                else -> "FTP"
            }
            val version = Regex("([A-Za-z0-9_.-]+\\s?[0-9]+\\.[0-9]+(?:[._-]\\w+)?)").find(line)?.groupValues?.get(1)?.trim() ?: ""
            return ServiceInfo("ftp", product, version, "", b)
        }

        // HTTP
        if (b.startsWith("HTTP/", ignoreCase = true) || b.contains("\r\nServer:") || b.contains("\nServer:")) {
            val server = Regex("(?im)^Server:\\s*(.+)$").find(b)?.groupValues?.get(1)?.trim() ?: ""
            val powered = Regex("(?im)^X-Powered-By:\\s*(.+)$").find(b)?.groupValues?.get(1)?.trim() ?: ""
            val title = Regex("(?is)<title[^>]*>(.*?)</title>").find(b)?.groupValues?.get(1)?.trim() ?: ""
            val location = Regex("(?im)^Location:\\s*(.+)$").find(b)?.groupValues?.get(1)?.trim() ?: ""
            var product = server
            var version = ""
            val sv = Regex("([A-Za-z0-9_.+-]+)/([0-9][0-9A-Za-z._-]*)").find(server)
            if (sv != null) {
                product = sv.groupValues[1]
                version = sv.groupValues[2]
            }
            val os = when {
                server.contains("Microsoft-IIS") -> "Windows ${server.substringAfter("Microsoft-IIS/").takeWhile { it.isDigit() }.let { if (it.isNotEmpty()) it else "" }}".trim()
                server.contains("Apache") -> "Linux/Unix"
                server.contains("nginx") -> "Linux/Unix"
                server.contains("LiteSpeed") -> "Linux/Unix"
                server.contains("openresty") -> "Linux/Unix"
                server.contains("Cowboy") -> "Linux/Unix"
                else -> ""
            }
            return ServiceInfo("http", product, version, os, listOf(server, powered, title, location).filter { it.isNotEmpty() }.joinToString(" | "))
        }

        // SMTP
        val smtp = Regex("^220[- ]([^\\r\\n]+)").find(b)
        if (smtp != null) {
            val line = smtp.groupValues[1]
            val product = when {
                line.contains("Postfix") -> "Postfix"
                line.contains("Exim") -> "Exim"
                line.contains("Sendmail") -> "Sendmail"
                line.contains("Microsoft ESMTP") -> "Microsoft ESMTP"
                line.contains("qmail") -> "qmail"
                else -> "SMTP"
            }
            return ServiceInfo("smtp", product, Regex("([0-9][0-9A-Za-z._-]*)").find(line)?.groupValues?.get(1) ?: "", "", b)
        }

        // IMAP / POP3
        if (b.startsWith("* OK", ignoreCase = true) || b.startsWith("+OK")) {
            val isImap = b.contains("IMAP", ignoreCase = true)
            val product = Regex("(Dovecot|Courier|Exchange|maildome|cyrus|Zimbra|Outlook)", RegexOption.IGNORE_CASE).find(b)?.groupValues?.get(1) ?: if (isImap) "IMAP" else "POP3"
            return ServiceInfo(if (isImap) "imap" else "pop3", product, "", "", b)
        }

        // Redis
        if (b.startsWith("-NOAUTH") || b.startsWith("-ERR") || b.startsWith("+PONG") || b.startsWith("$") || b.startsWith("*")) {
            if (b.contains("NOAUTH") || b.contains("ERR unknown command")) {
                return ServiceInfo("redis", "Redis", "requiere autenticación", "", b)
            }
            return ServiceInfo("redis", "Redis", "", "", b)
        }

        // Memcached
        val mem = Regex("^VERSION (\\S+)").find(b)
        if (mem != null) return ServiceInfo("memcached", "Memcached", mem.groupValues[1], "", b)

        // VNC
        val rfb = Regex("^RFB (\\d{3}\\.\\d{3})").find(b)
        if (rfb != null) return ServiceInfo("vnc", "VNC", rfb.groupValues[1], "", b)

        // MySQL
        if (b.length > 5 && b[4].code == 0) {
            val ver = Regex("^\\d+\\.\\d+\\.\\d+").find(b)?.groupValues?.get(1) ?: ""
            return ServiceInfo("mysql", "MySQL/MariaDB", ver, "", b)
        }

        // PostgreSQL
        if (b.startsWith("E") && b.contains("FATAL")) {
            return ServiceInfo("postgresql", "PostgreSQL", Regex("(\\d+\\.\\d+)").find(b)?.groupValues?.get(1) ?: "", "", b)
        }

        // RDP
        if (b.startsWith("TPKT") || b.length > 4 && b[0].code == 0x03 && b[1].code == 0x00) {
            return ServiceInfo("rdp", "RDP", "", "Windows", b)
        }

        // Telnet / varios
        if (b.contains("login:") || b.contains("Username:") || b.contains("assword:")) {
            return ServiceInfo(serviceName(port), "", "", "", b)
        }

        // Docker API
        if (b.contains("\"Version\"") && b.contains("\"ApiVersion\"")) {
            return ServiceInfo("docker-api", "Docker Engine API", "", "", b)
        }

        // Elasticsearch
        if (b.contains("\"cluster_name\"") || b.contains("You Know, for Search")) {
            return ServiceInfo("elasticsearch", "Elasticsearch", "", "", b)
        }

        // Mongo
        if (b.contains("MongoDB")) return ServiceInfo("mongodb", "MongoDB", "", "", b)

        // NetBIOS / SMB
        if (b.contains("NT LM") || b.contains("Samba")) return ServiceInfo("smb", "Samba/Windows", "", "", b)

        // Generic: primera palabra
        val word = b.take(40).trim().lineSequence().firstOrNull()?.trim() ?: ""
        return default.copy(product = word.take(40))
    }

    /** Adivina el sistema operativo a partir de los servicios detectados. */
    fun guessOs(host: HostOs): String {
        val s = host.services
        return when {
            s.any { it.contains("Windows", true) || it.contains("Microsoft", true) } -> "Windows"
            s.any { it.contains("Android", true) } -> "Android"
            s.any { it.contains("Ubuntu", true) || it.contains("Debian", true) } -> "Linux (Debian/Ubuntu)"
            s.any { it.contains("CentOS", true) || it.contains("Rocky", true) } -> "Linux (RHEL)"
            s.any { it.contains("FreeBSD", true) || it.contains("OpenBSD", true) } -> "BSD"
            s.any { it.contains("OpenWrt", true) } -> "OpenWrt"
            s.any { it.contains("macOS", true) } -> "macOS/iOS"
            else -> if (host.services.isEmpty()) "Desconocido" else "Desconocido (Linux/Unix probable)"
        }
    }

    data class HostOs(val services: List<String>)
}
