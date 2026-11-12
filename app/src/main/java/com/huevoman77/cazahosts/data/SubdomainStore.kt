package com.huevoman77.cazahosts.data

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SubdomainStore(context: Context) {

    private val dir = File(context.applicationContext.filesDir, "subdomains").apply { mkdirs() }
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    fun save(session: SubdomainSession) {
        try {
            File(dir, "${session.id}.json").writeText(json.encodeToString(SubdomainSession.serializer(), session))
        } catch (e: Exception) {
        }
    }

    fun loadAll(): List<SubdomainSession> {
        val files = dir.listFiles { f -> f.name.endsWith(".json") } ?: return emptyList()
        return files.mapNotNull { file ->
            try {
                json.decodeFromString(SubdomainSession.serializer(), file.readText())
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

    fun exportCsv(session: SubdomainSession): String {
        val sb = StringBuilder()
        sb.appendLine("subdominio;direcciones;cname;http;titulo;registros")
        for (item in session.results) {
            sb.appendLine(
                listOf(
                    item.host,
                    item.addresses.joinToString(",") + if (item.ipv6.isNotEmpty()) " " + item.ipv6.joinToString(",") else "",
                    item.cname,
                    if (item.httpStatus > 0) item.httpStatus.toString() else "",
                    item.httpTitle,
                    item.records.joinToString(" | ") { "${it.type}=${it.value}" }
                ).joinToString(";") { it.replace(';', ',').replace("\n", " ") }
            )
        }
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        sb.appendLine()
        sb.appendLine("# ${session.domain} - ${fmt.format(Date(session.startedAt))}")
        sb.appendLine("# Probados: ${session.tested} | Encontrados: ${session.found} | Descartados por comodin: ${session.discardedWildcard}")
        return sb.toString()
    }
}
