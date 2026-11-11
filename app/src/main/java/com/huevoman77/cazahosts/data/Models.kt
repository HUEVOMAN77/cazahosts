package com.huevoman77.cazahosts.data

import kotlinx.serialization.Serializable

enum class Severity(val label: String, val peso: Int) {
    CRITICA("Crítica", 5),
    ALTA("Alta", 4),
    MEDIA("Media", 3),
    BAJA("Baja", 2),
    INFO("Info", 1);

    companion object {
        fun from(text: String): Severity = entries.firstOrNull { it.name == text } ?: INFO
    }
}

@Serializable
data class VulnFinding(
    val id: String,
    val severity: String = Severity.INFO.name,
    val title: String,
    val detail: String = "",
    val fix: String = "",
    val cve: String = "",
    val cvss: Double = 0.0,
    val port: Int = 0
) {
    val severityEnum: Severity get() = Severity.from(severity)
}

@Serializable
data class PortFinding(
    val port: Int,
    val service: String = "",
    val product: String = "",
    val version: String = "",
    val banner: String = "",
    val state: String = "open",
    val tls: Boolean = false,
    val tlsVersion: String = "",
    val certExpiry: String = "",
    val certDaysLeft: Int = -1,
    val httpStatus: Int = 0,
    val httpTitle: String = "",
    val findings: List<VulnFinding> = emptyList()
)

@Serializable
data class HostResult(
    val ip: String,
    val alive: Boolean = false,
    val hostname: String = "",
    val mac: String = "",
    val vendor: String = "",
    val os: String = "",
    val latencyMs: Long = 0,
    val lastSeen: Long = 0,
    val openPorts: List<PortFinding> = emptyList(),
    val findings: List<VulnFinding> = emptyList()
) {
    val riskScore: Int
        get() = findings.sumOf { it.severityEnum.peso }
}

@Serializable
data class ScanSession(
    val id: String,
    val target: String,
    val startedAt: Long,
    val finishedAt: Long = 0,
    val hostsScanned: Int = 0,
    val hostsAlive: Int = 0,
    val portsFound: Int = 0,
    val findingsCount: Int = 0,
    val mode: String = "",
    val hosts: List<HostResult> = emptyList()
)

@Serializable
data class WatchedHost(
    val ip: String,
    val label: String = "",
    val alive: Boolean = false,
    val lastStateChange: Long = 0,
    val history: List<Long> = emptyList(),
    val portsOpen: Int = 0,
    val mac: String = ""
)
