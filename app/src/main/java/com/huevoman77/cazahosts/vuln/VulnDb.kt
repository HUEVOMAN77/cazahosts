package com.huevoman77.cazahosts.vuln

import com.huevoman77.cazahosts.data.PortFinding
import com.huevoman77.cazahosts.data.Severity
import com.huevoman77.cazahosts.data.VulnFinding

/**
 * Base de conocimiento local (offline) que correlaciona banners y versiones
 * con vulnerabilidades conocidas y malas prácticas de exposición.
 */
object VulnDb {

    data class Rule(
        val id: String,
        val severity: Severity,
        val title: String,
        val detail: String,
        val fix: String,
        val cve: String = "",
        val cvss: Double = 0.0,
        val ports: Set<Int> = emptySet(),
        val services: Set<String> = emptySet(),
        val bannerContains: String = "",
        val versionRegex: String = "",
        val affectedBelow: String = "",
        val affectedAbove: String = ""
    )

    val rules: List<Rule> = listOf(
        Rule(
            id = "vsftpd-2.3.4",
            severity = Severity.CRITICA,
            title = "vsftpd 2.3.4 con puerta trasera",
            detail = "La versión 2.3.4 incluye una puerta trasera que abre un shell en el puerto 6200 con una contraseña mágica.",
            fix = "Actualizar vsftpd a 3.x o eliminar el servicio. Verificar si el puerto 6200 está abierto.",
            cve = "CVE-2011-2523",
            cvss = 9.8,
            services = setOf("ftp"),
            bannerContains = "vsftpd 2.3.4"
        ),
        Rule(
            id = "proftpd-modcopy",
            severity = Severity.CRITICA,
            title = "ProFTPD con módulo mod_copy vulnerable",
            detail = "El módulo mod_copy de ProFTPD 1.3.5 y anteriores permite copiar archivos arbitrarios y ejecutar código.",
            fix = "Actualizar ProFTPD a 1.3.8 o superior y deshabilitar mod_copy.",
            cve = "CVE-2015-3306",
            cvss = 9.8,
            services = setOf("ftp"),
            versionRegex = "([0-9]+\\.[0-9]+\\.[0-9]+)",
            affectedBelow = "1.3.8"
        ),
        Rule(
            id = "openssh-regsshion",
            severity = Severity.ALTA,
            title = "OpenSSH vulnerable a regreSSHion",
            detail = "OpenSSH anterior a 9.6 es susceptible a exploitación de señal remota (RCE) en el servidor sshd.",
            fix = "Actualizar a OpenSSH 9.6 o posterior y reiniciar el servicio sshd.",
            cve = "CVE-2024-6387",
            cvss = 8.1,
            services = setOf("ssh"),
            versionRegex = "OpenSSH[_ ]?([0-9]+\\.[0-9]+)",
            affectedBelow = "9.6"
        ),
        Rule(
            id = "openssh-info-leak",
            severity = Severity.MEDIA,
            title = "OpenSSH con fuga de información de claves",
            detail = "OpenSSH anterior a 7.2 puede filtrar información parcial de claves privadas al negociar la sesión.",
            fix = "Actualizar OpenSSH a 7.2 o posterior.",
            cve = "CVE-2016-0777",
            cvss = 5.9,
            services = setOf("ssh"),
            versionRegex = "OpenSSH[_ ]?([0-9]+\\.[0-9]+)",
            affectedBelow = "7.2"
        ),
        Rule(
            id = "apache-traversal",
            severity = Severity.CRITICA,
            title = "Apache httpd con recorrido de rutas",
            detail = "Las versiones 2.4.49 y 2.4.50 permiten leer archivos fuera del directorio raíz y, con CGI habilitado, ejecutar código.",
            fix = "Actualizar httpd a 2.4.51 o posterior.",
            cve = "CVE-2021-42013",
            cvss = 9.8,
            services = setOf("http"),
            bannerContains = "Apache/2.4.4",
            versionRegex = "Apache/([0-9]+\\.[0-9]+\\.[0-9]+)",
            affectedBelow = "2.4.51"
        ),
        Rule(
            id = "nginx-resolver",
            severity = Severity.ALTA,
            title = "nginx con error off-by-one en el resolver",
            detail = "Una resolución de nombre malformada puede revelar memoria del proceso nginx.",
            fix = "Actualizar nginx a 1.20.1 / 1.21.0 o posterior.",
            cve = "CVE-2021-23017",
            cvss = 7.5,
            services = setOf("http"),
            bannerContains = "nginx/",
            versionRegex = "nginx/([0-9]+\\.[0-9]+\\.[0-9]+)",
            affectedBelow = "1.20.1"
        ),
        Rule(
            id = "redis-heap",
            severity = Severity.ALTA,
            title = "Redis con corrupción de memoria",
            detail = "Redis anterior a 6.2 puede sufir desbordamiento de enteros y corrupción de memoria al procesar comandos.",
            fix = "Actualizar Redis a 6.2 o posterior y restringir el acceso por red.",
            cve = "CVE-2021-32761",
            cvss = 8.8,
            services = setOf("redis"),
            versionRegex = "Redis server v?([0-9]+\\.[0-9]+\\.[0-9]+)",
            affectedBelow = "6.2"
        ),
        Rule(
            id = "jenkins-arbitrary-read",
            severity = Severity.ALTA,
            title = "Jenkins con lectura arbitraria de archivos",
            detail = "Jenkins anterior a 2.426.2 / 2.441.3 / 2.492.3 permite leer archivos arbitrarios en el servidor.",
            fix = "Actualizar Jenkins a una versión parcheada y restringir el acceso a la consola.",
            cve = "CVE-2024-23897",
            cvss = 9.8,
            services = setOf("http"),
            bannerContains = "X-Jenkins:",
            versionRegex = "X-Jenkins:\\s*([0-9]+\\.[0-9]+)",
            affectedBelow = "2.426.2"
        ),
        Rule(
            id = "grafana-path-traversal",
            severity = Severity.ALTA,
            title = "Grafana con recorrido de rutas",
            detail = "Grafana anterior a 8.5.7 / 9.3.6 permite leer archivos arbitrarios en el host.",
            fix = "Actualizar Grafana y limitar el acceso a la interfaz.",
            cve = "CVE-2021-43798",
            cvss = 8.6,
            services = setOf("http"),
            bannerContains = "grafana",
            versionRegex = "\"version\"\\s*:\\s*\"([0-9]+\\.[0-9]+\\.[0-9]+)\"",
            affectedBelow = "8.5.7"
        ),
        Rule(
            id = "docker-api-exposed",
            severity = Severity.ALTA,
            title = "API de Docker expuesta sin autenticación",
            detail = "El puerto 2375 permite tomar control total del host (crear contenedores con raíz) sin credenciales.",
            fix = "Cerrar el puerto 2375, habilitar TLS y filtrar por firewall.",
            ports = setOf(2375)
        ),
        Rule(
            id = "kubernetes-api-exposed",
            severity = Severity.ALTA,
            title = "API de Kubernetes expuesta",
            detail = "El servidor de la API en 6443 accesible desde la red permite enumerar y modificar recursos del clúster.",
            fix = "Restringir el acceso por red y habilitar RBAC correcto.",
            ports = setOf(6443)
        ),
        Rule(
            id = "redis-unauth",
            severity = Severity.MEDIA,
            title = "Redis expuesto sin autenticación",
            detail = "El servicio acepta comandos de anybody en la red: permite leer datos, escribir archivos o usar el host para pivoteo.",
            fix = "Configurar requirepass, limitar la escucha a localhost y filtrar por firewall.",
            services = setOf("redis"),
            ports = setOf(6379)
        ),
        Rule(
            id = "mongo-unauth",
            severity = Severity.MEDIA,
            title = "MongoDB expuesto sin autenticación",
            detail = "MongoDB responde sin credenciales, permitiendo leer y modificar bases de datos.",
            fix = "Habilitar autenticación y limitar las conexiones a redes confiables.",
            ports = setOf(27017)
        ),
        Rule(
            id = "elastic-unauth",
            severity = Severity.MEDIA,
            title = "Elasticsearch expuesto sin autenticación",
            detail = "El clúster responde sin credenciales: se pueden listar, leer y borrar índices.",
            fix = "Activar X-Pack Security o proteger el servicio con un proxy de autenticación.",
            ports = setOf(9200, 9300)
        ),
        Rule(
            id = "memcached-exposed",
            severity = Severity.MEDIA,
            title = "Memcached expuesto",
            detail = "Memcached no tiene autenticación: sirve y permite almacenar datos en memoria y usarla para amplificación.",
            fix = "Limitar la escucha a 127.0.0.1 y filtrar por firewall.",
            ports = setOf(11211)
        ),
        Rule(
            id = "telnet-exposed",
            severity = Severity.MEDIA,
            title = "Telnet expuesto con credenciales en claro",
            detail = "Telnet transmite usuario y contraseña sin cifrar.",
            fix = "Migrar a SSH y cerrar el puerto 23.",
            ports = setOf(23)
        ),
        Rule(
            id = "vnc-exposed",
            severity = Severity.MEDIA,
            title = "VNC expuesto",
            detail = "El escritorio remoto es alcanzable desde la red y suele usar contraseñas débiles.",
            fix = "Usar VNC con cifrado o RDP y restringir el origen.",
            ports = setOf(5900)
        ),
        Rule(
            id = "smb-exposed",
            severity = Severity.MEDIA,
            title = "SMB expuesto en la red",
            detail = "El servicio de archivos de Windows está disponible y es el vector típico de movimiento lateral.",
            fix = "Restringir SMB a las redes necesarias y exigir cifrado/firma.",
            ports = setOf(445, 139)
        ),
        Rule(
            id = "rdp-exposed",
            severity = Severity.MEDIA,
            title = "RDP expuesto",
            detail = "Escritorio remoto accesible desde la red, común en ataques de fuerza bruta.",
            fix = "Usar VPN, restringir por IP y activar MFA/NLA.",
            ports = setOf(3389)
        ),
        Rule(
            id = "rsync-exposed",
            severity = Severity.BAJA,
            title = "rsync expuesto",
            detail = "rsync sin autenticación puede listar y sincronizar contenido del servidor.",
            fix = "Restringir con reglas de firewall y usar módulo secreto.",
            ports = setOf(873)
        ),
        Rule(
            id = "php-fpm-exposed",
            severity = Severity.MEDIA,
            title = "PHP-FPM expuesto",
            detail = "El protocolo de FastCGI accesible permite ejecutar código PHP en el servidor.",
            fix = "Cerrar el puerto 9000 y dejarlo sólo para el servidor web local.",
            ports = setOf(9000)
        ),
        Rule(
            id = "etcd-exposed",
            severity = Severity.ALTA,
            title = "etcd expuesto",
            detail = "etcd guarda el estado y las credenciales de Kubernetes en claro.",
            fix = "Habilitar TLS y autenticación; restringir por firewall.",
            ports = setOf(2379)
        ),
        Rule(
            id = "winrm-exposed",
            severity = Severity.MEDIA,
            title = "WinRM expuesto",
            detail = "La administración remota de Windows está disponible en la red.",
            fix = "Restringir por firewall y usar HTTPS con certificados válidos.",
            ports = setOf(5985, 5986)
        ),
        Rule(
            id = "version-disclosure",
            severity = Severity.INFO,
            title = "El banner expone la versión exacta del servicio",
            detail = "Publicar la versión facilita la explotación de vulnerabilidades conocidas.",
            fix = "Ocultar banners o usar proxy inverso que no los exponga.",
            services = setOf("http", "ftp", "smtp")
        )
    )

    fun versionParts(version: String): List<Int> =
        Regex("\\d+").findAll(version).map { it.value.toInt() }.toList()

    fun lessThan(version: String, limit: String): Boolean {
        val a = versionParts(version)
        val b = versionParts(limit)
        if (a.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x < y
        }
        return false
    }

    /** Evalúa las reglas pasivas contra un puerto detectado. */
    fun evaluate(port: PortFinding): List<VulnFinding> {
        val out = mutableListOf<VulnFinding>()
        val haystack = buildString {
            append(port.banner).append('\n').append(port.product).append(' ')
            append(port.version).append(' ').append(port.service)
        }

        for (rule in rules) {
            if (rule.services.isNotEmpty()) {
                if (port.service !in rule.services) continue
            } else if (rule.ports.isNotEmpty()) {
                if (port.port !in rule.ports) continue
            }

            var matched = false
            if (rule.bannerContains.isNotEmpty()) {
                matched = haystack.contains(rule.bannerContains, ignoreCase = true)
            }
            if (!matched && rule.versionRegex.isNotEmpty() && rule.affectedBelow.isNotEmpty()) {
                val regex = Regex(rule.versionRegex, RegexOption.IGNORE_CASE)
                val m = regex.find(haystack)
                if (m != null) {
                    val version = m.groupValues.getOrNull(1) ?: m.value
                    matched = lessThan(version, rule.affectedBelow)
                }
            }
            if (!matched) continue

            if (rule.id == "version-disclosure") {
                val exposes = Regex("[0-9]+\\.[0-9]+").containsMatchIn(port.banner) ||
                    Regex("(?i)^server:\\s*[A-Za-z0-9._+-]+/[0-9]").containsMatchIn(port.banner)
                if (!exposes) continue
            }

            out += VulnFinding(
                id = rule.id,
                severity = rule.severity.name,
                title = rule.title,
                detail = rule.detail,
                fix = rule.fix,
                cve = rule.cve,
                cvss = rule.cvss,
                port = port.port
            )
        }
        return out
    }
}
