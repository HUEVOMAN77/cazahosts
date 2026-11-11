package com.huevoman77.cazahosts.data

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ResultStore(context: Context) {

    private val dir = File(context.applicationContext.filesDir, "sessions").apply { mkdirs() }
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    fun save(session: ScanSession) {
        try {
            val file = File(dir, "${session.id}.json")
            file.writeText(json.encodeToString(ScanSession.serializer(), session))
        } catch (e: Exception) {
            // el guardado es best-effort
        }
    }

    fun loadAll(): List<ScanSession> {
        val files = dir.listFiles { f -> f.name.endsWith(".json") } ?: return emptyList()
        return files.mapNotNull { file ->
            try {
                json.decodeFromString(ScanSession.serializer(), file.readText())
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.startedAt }
    }

    fun delete(id: String) {
        try {
            File(dir, "$id.json").delete()
        } catch (e: Exception) {
        }
    }

    fun exportJson(session: ScanSession): String = json.encodeToString(ScanSession.serializer(), session)

    fun exportCsv(session: ScanSession): String {
        val sb = StringBuilder()
        sb.appendLine("ip;hostname;vendor;os;alive;puertos;hallazgos;severidad_maxima;cvss_maximo")
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        for (host in session.hosts) {
            val maxSeverity = host.findings.maxByOrNull { it.severityEnum.peso }?.severityEnum?.label ?: ""
            val maxCvss = host.findings.maxOfOrNull { it.cvss } ?: 0.0
            sb.appendLine(
                listOf(
                    host.ip,
                    host.hostname,
                    host.vendor,
                    host.os,
                    if (host.alive) "si" else "no",
                    host.openPorts.joinToString(",") { "${it.port}/${it.service}" },
                    host.findings.joinToString(" | ") { it.title },
                    maxSeverity,
                    maxCvss.toString()
                ).joinToString(";") { it.replace(';', ',').replace("\n", " ") }
            )
        }
        sb.appendLine()
        sb.appendLine("# Escaneo ${session.target} - ${fmt.format(Date(session.startedAt))}")
        sb.appendLine("# Hosts: ${session.hostsScanned} | Vivos: ${session.hostsAlive} | Puertos: ${session.portsFound} | Hallazgos: ${session.findingsCount}")
        return sb.toString()
    }
}
