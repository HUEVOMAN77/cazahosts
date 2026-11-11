package com.huevoman77.cazahosts.data

import android.content.Context
import android.content.Intent
import android.os.Build
import com.huevoman77.cazahosts.scan.ScanConfig
import com.huevoman77.cazahosts.scan.ScanEngine
import com.huevoman77.cazahosts.scan.ScanProgress
import com.huevoman77.cazahosts.scan.ScanSummary
import com.huevoman77.cazahosts.service.ScanService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.TreeMap
import java.util.UUID

object ScanRepository {

    private const val PUBLISH_EVERY = 8

    private val hostLock = Any()
    private val hostMap = TreeMap<String, HostResult>()
    private var pending = 0

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
            _progress.value = _progress.value.copy(
                running = false,
                phase = "Error: ${error.message ?: error.javaClass.simpleName}"
            )
        }
    )
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
        synchronized(hostLock) {
            hostMap.clear()
            pending = 0
        }
        _hosts.value = emptyList()
        _summary.value = null
        _progress.value = ScanProgress(phase = "Iniciando", running = true)
        val e = ScanEngine(config)
        engine = e
        startService()
        job = scope.launch {
            try {
                val result = e.run(
                    onHost = { host -> upsertHost(host) },
                    onProgress = { p -> _progress.value = p }
                )
                _summary.value = result
                publishHosts()
                if (result.error.isNotEmpty()) {
                    _progress.value = _progress.value.copy(running = false, phase = result.error)
                } else {
                    _progress.value = _progress.value.copy(running = false, phase = "Finalizado")
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
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                val message = error.message ?: error.javaClass.simpleName
                _summary.value = ScanSummary(error = message)
                _progress.value = _progress.value.copy(running = false, phase = "Error: $message")
            } finally {
                stopService()
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

    /**
     * El motor reporta el host al descubrirlo y otra vez al cerrar el resumen,
     * asi que la lista se actualiza por IP en vez de concatenar: si se repite
     * la clave, Compose aborta la pantalla.
     */
    private fun upsertHost(host: HostResult) {
        val publish = synchronized(hostLock) {
            hostMap[host.ip] = host
            pending++
            pending >= PUBLISH_EVERY
        }
        if (publish) publishHosts()
    }

    private fun publishHosts() {
        val snapshot = synchronized(hostLock) {
            pending = 0
            hostMap.values.toList()
        }
        _hosts.value = snapshot
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
