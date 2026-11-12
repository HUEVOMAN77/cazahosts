package com.huevoman77.cazahosts.data

import android.content.Context
import android.util.Log
import com.huevoman77.cazahosts.dns.SubdomainConfig
import com.huevoman77.cazahosts.dns.SubdomainEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class SubdomainProgress(
    val running: Boolean = false,
    val domain: String = "",
    val phase: String = "",
    val tested: Int = 0,
    val total: Int = 0,
    val found: Int = 0,
    val startedAt: Long = 0,
    val discarded: Int = 0,
    val wildcard: Boolean = false,
    val error: String = ""
) {
    val elapsedMs: Long get() = if (startedAt == 0L) 0L else System.currentTimeMillis() - startedAt

    val etaMs: Long
        get() {
            if (tested < 10 || total <= tested) return 0L
            val perItem = elapsedMs.toDouble() / tested
            return ((total - tested) * perItem).toLong()
        }
}

object SubdomainRepository {

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
            _progress.value = _progress.value.copy(
                running = false,
                phase = "Error: ${error.message ?: error.javaClass.simpleName}"
            )
        }
    )

    private val _progress = MutableStateFlow(SubdomainProgress())
    val progress: StateFlow<SubdomainProgress> = _progress

    private val _results = MutableStateFlow<List<SubdomainResult>>(emptyList())
    val results: StateFlow<List<SubdomainResult>> = _results

    private val _sessions = MutableStateFlow<List<SubdomainSession>>(emptyList())
    val sessions: StateFlow<List<SubdomainSession>> = _sessions

    private var engine: SubdomainEngine? = null
    private var job: Job? = null
    private var store: SubdomainStore? = null

    fun init(context: Context) {
        store = SubdomainStore(context.applicationContext)
        _sessions.value = store?.loadAll().orEmpty()
    }

    fun reloadSessions() {
        _sessions.value = store?.loadAll().orEmpty()
    }

    fun deleteSession(id: String) {
        store?.delete(id)
        reloadSessions()
    }

    val isRunning: Boolean get() = job?.isActive == true

    fun start(config: SubdomainConfig) {
        if (job?.isActive == true) return
        val domain = config.domain.trim()
        if (domain.isEmpty()) {
            _progress.value = SubdomainProgress(phase = "Falta el dominio objetivo", error = "Escribe el dominio a fuerza bruta, por ejemplo ejemplo.com")
            return
        }
        if (config.words.isEmpty()) {
            _progress.value = SubdomainProgress(phase = "No hay palabras que probar", error = "Agrega palabras al diccionario o usa una lista propia")
            return
        }

        _results.value = emptyList()
        _progress.value = SubdomainProgress(
            running = true,
            domain = domain,
            phase = "Preparando consultas",
            total = config.words.size,
            startedAt = System.currentTimeMillis()
        )

        val e = SubdomainEngine(config)
        engine = e
        job = scope.launch {
            try {
                val run = e.run(
                    onResult = { result -> upsert(result) },
                    onProgress = { tested, found ->
                        _progress.value = _progress.value.copy(
                            tested = tested,
                            found = found,
                            phase = "Probando nombres"
                        )
                    }
                )
                _progress.value = _progress.value.copy(
                    running = false,
                    phase = "Finalizado",
                    tested = run.tested,
                    found = run.results.size,
                    total = run.candidates,
                    discarded = run.discardedWildcard,
                    wildcard = run.wildcardIps.isNotEmpty()
                )
                val session = SubdomainSession(
                    id = UUID.randomUUID().toString(),
                    domain = domain,
                    startedAt = System.currentTimeMillis() - run.elapsedMs,
                    finishedAt = System.currentTimeMillis(),
                    tested = run.tested,
                    found = run.results.size,
                    discardedWildcard = run.discardedWildcard,
                    resolvers = config.resolvers.joinToString(", "),
                    results = run.results
                )
                store?.save(session)
                reloadSessions()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                val message = error.message ?: error.javaClass.simpleName
                Log.w("CazaHosts", "Fallo la fuerza bruta de subdominios", error)
                _progress.value = _progress.value.copy(running = false, phase = "Error: $message", error = message)
            }
        }
    }

    fun stop() {
        engine?.cancelled = true
        job?.cancel()
        job = null
        _progress.value = _progress.value.copy(running = false, phase = "Cancelado")
    }

    fun clear() {
        _results.value = emptyList()
        _progress.value = SubdomainProgress()
    }

    private fun upsert(result: SubdomainResult) {
        val current = _results.value
        val index = current.indexOfFirst { it.host == result.host }
        _results.value = if (index >= 0) {
            current.toMutableList().also { it[index] = result }
        } else {
            current + result
        }
    }

    fun exportText(): String {
        val sb = StringBuilder()
        sb.appendLine("CazaHosts - Subdominios encontrados")
        sb.appendLine("Dominio: ${_progress.value.domain}")
        sb.appendLine("Total: ${_results.value.size}")
        sb.appendLine()
        for (item in _results.value) {
            sb.appendLine(item.host)
            if (item.addresses.isNotEmpty()) sb.appendLine("   IPs: ${item.addresses.joinToString(", ")}")
            if (item.ipv6.isNotEmpty()) sb.appendLine("   IPv6: ${item.ipv6.joinToString(", ")}")
            if (item.cname.isNotEmpty()) sb.appendLine("   CNAME: ${item.cname}")
            if (item.httpStatus > 0) sb.appendLine("   HTTP ${item.httpStatus} ${item.httpTitle}")
            item.records.forEach { sb.appendLine("   ${it.type}: ${it.value}") }
        }
        return sb.toString()
    }
}
