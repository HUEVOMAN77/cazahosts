package com.huevoman77.cazahosts.data

import android.content.Context
import com.huevoman77.cazahosts.check.AliveChecker
import com.huevoman77.cazahosts.util.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Lista de equipos vigilados. Se guarda en disco para que no se pierda al
 * cerrar la app, y la revision periodica vive en un ambito de aplicacion para
 * que siga corriendo aunque se cambie de pestana.
 */
object CheckerStore {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var prefs: android.content.SharedPreferences? = null
    private var appContext: Context? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var autoJob: Job? = null
    private val checker = AliveChecker()

    private val _watched = MutableStateFlow<List<WatchedHost>>(emptyList())
    val watched: StateFlow<List<WatchedHost>> = _watched

    private val _autoCheck = MutableStateFlow(false)
    val autoCheck: StateFlow<Boolean> = _autoCheck

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.applicationContext.getSharedPreferences("cazahosts_checker", Context.MODE_PRIVATE)
        load()
        if (prefs?.getBoolean("auto", false) == true) {
            setAutoCheck(true, prefs?.getInt("interval", 60) ?: 60)
        }
    }

    private fun load() {
        val raw = prefs?.getString("watched", "") ?: ""
        if (raw.isEmpty()) {
            _watched.value = emptyList()
            return
        }
        _watched.value = try {
            json.decodeFromString<List<WatchedHost>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun persist() {
        val encoded = json.encodeToString(ListSerializer(WatchedHost.serializer()), _watched.value)
        prefs?.edit()?.putString("watched", encoded)?.apply()
    }

    fun add(ip: String, label: String = "") {
        val clean = ip.trim()
        if (clean.isEmpty()) return
        if (_watched.value.any { it.ip == clean }) return
        _watched.value = _watched.value + WatchedHost(ip = clean, label = label)
        persist()
    }

    fun addAll(ips: List<String>) {
        val known = _watched.value.map { it.ip }.toSet()
        val fresh = ips.map { it.trim() }.filter { it.isNotEmpty() && it !in known }
        if (fresh.isEmpty()) return
        _watched.value = _watched.value + fresh.map { WatchedHost(ip = it) }
        persist()
    }

    fun remove(ip: String) {
        _watched.value = _watched.value.filterNot { it.ip == ip }
        persist()
    }

    fun update(host: WatchedHost) {
        _watched.value = _watched.value.map { if (it.ip == host.ip) host else it }
        persist()
    }

    fun clear() {
        _watched.value = emptyList()
        persist()
    }

    /**
     * Activa o desactiva la revision periodica. El ciclo corre en el ambito de
     * la aplicacion, no en la pantalla, para que cambiar de pestana no lo corte.
     */
    fun setAutoCheck(enabled: Boolean, intervalSec: Int) {
        _autoCheck.value = enabled
        prefs?.edit()?.putBoolean("auto", enabled)?.putInt("interval", intervalSec)?.apply()
        autoJob?.cancel()
        autoJob = null
        if (!enabled) return
        autoJob = scope.launch {
            while (isActive) {
                delay((intervalSec.coerceAtLeast(15) * 1000L))
                runOnce()
            }
        }
    }

    /** Una pasada de comprobacion, avisando cuando un equipo cambia de estado. */
    suspend fun runOnce(): List<WatchedHost> {
        val current = _watched.value
        if (current.isEmpty()) return emptyList()
        val results = checker.check(current) { updated -> update(updated) }
        val ctx = appContext
        results.forEach { updated ->
            val before = current.firstOrNull { it.ip == updated.ip }
            if (ctx != null && before != null && before.alive != updated.alive) {
                Notifier.stateChanged(ctx, updated.ip, updated.alive)
            }
        }
        return results
    }
}
