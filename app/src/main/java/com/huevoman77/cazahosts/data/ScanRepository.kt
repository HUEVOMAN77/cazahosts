package com.huevoman77.cazahosts.data

import android.content.Context
import android.content.Intent
import android.os.Build
import com.huevoman77.cazahosts.scan.ScanConfig
import com.huevoman77.cazahosts.scan.ScanEngine
import com.huevoman77.cazahosts.scan.ScanProgress
import com.huevoman77.cazahosts.scan.ScanSummary
import com.huevoman77.cazahosts.service.ScanService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

object ScanRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _progress = MutableStateFlow(ScanProgress())
    val progress: StateFlow<ScanProgress> = _progress

    private val _hosts = MutableStateFlow<List<HostResult>>(emptyList())
    val hosts: StateFlow<List<HostResult>> = _hosts

    private val _sessions = MutableStateFlow<List<ScanSession>>(emptyList())
    val sessions: StateFlow<List<ScanSession>> = _sessions

    private val _summary = MutableStateFlow<ScanSummary?>(null)
    val summary: StateFlow<ScanSummary?> = _summary

    private var engine: ScanEngine? = null
    private var job: Job? = null
    private var store: ResultStore? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        store = ResultStore(context.applicationContext)
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

    fun start(config: ScanConfig) {
        if (job?.isActive == true) return
        _hosts.value = emptyList()
        _summary.value = null
        _progress.value = ScanProgress(phase = "Iniciando", running = true)
        val e = ScanEngine(config)
        engine = e
        startService()
        job = scope.launch {
            val result = e.run(
                onHost = { host -> _hosts.value = (_hosts.value + host).sortedBy { it.ip } },
                onProgress = { p -> _progress.value = p }
            )
            _summary.value = result
            _progress.value = _progress.value.copy(running = false, phase = "Finalizado")
            stopService()
            if (result.error.isEmpty()) {
                val session = ScanSession(
                    id = UUID.randomUUID().toString(),
                    target = config.targets,
                    startedAt = System.currentTimeMillis() - result.durationMs,
                    finishedAt = System.currentTimeMillis(),
                    hostsScanned = result.hostsScanned.toInt(),
                    hostsAlive = result.hostsAlive,
                    portsFound = result.portsFound,
                    findingsCount = result.findings,
                    mode = config.mode.label,
                    hosts = result.hosts
                )
                store?.save(session)
                reloadSessions()
            }
        }
    }

    fun stop() {
        engine?.cancel()
        job?.cancel()
        job = null
        _progress.value = _progress.value.copy(running = false, phase = "Cancelado")
        stopService()
    }

    private fun startService() {
        val ctx = appContext ?: return
        try {
            val intent = Intent(ctx, ScanService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        } catch (e: Exception) {
            // el escaneo sigue en el repositorio aunque el servicio no arranque
        }
    }

    private fun stopService() {
        val ctx = appContext ?: return
        try {
            ctx.stopService(Intent(ctx, ScanService::class.java))
        } catch (e: Exception) {
        }
    }
}
