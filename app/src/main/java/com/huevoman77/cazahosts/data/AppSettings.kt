package com.huevoman77.cazahosts.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AppPrefs(
    val concurrency: Int = 96,
    val timeoutMs: Int = 700,
    val grabBanner: Boolean = true,
    val activeChecks: Boolean = true,
    val useArp: Boolean = true,
    val reverseDns: Boolean = true,
    val portPreset: String = "top",
    val attestation: Boolean = false,
    val credRatePerSec: Int = 3,
    val credMaxPerHost: Int = 20,
    val checkIntervalSec: Int = 60,
    val themeDark: Boolean = true
)

class AppSettings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("cazahosts_prefs", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppPrefs> = _state

    private fun load(): AppPrefs = AppPrefs(
        concurrency = prefs.getInt("concurrency", 96),
        timeoutMs = prefs.getInt("timeout_ms", 700),
        grabBanner = prefs.getBoolean("grab_banner", true),
        activeChecks = prefs.getBoolean("active_checks", true),
        useArp = prefs.getBoolean("use_arp", true),
        reverseDns = prefs.getBoolean("reverse_dns", true),
        portPreset = prefs.getString("port_preset", "top") ?: "top",
        attestation = prefs.getBoolean("attestation", false),
        credRatePerSec = prefs.getInt("cred_rate", 3),
        credMaxPerHost = prefs.getInt("cred_max_host", 20),
        checkIntervalSec = prefs.getInt("check_interval", 60),
        themeDark = prefs.getBoolean("theme_dark", true)
    )

    private fun persist(state: AppPrefs) {
        prefs.edit().apply {
            putInt("concurrency", state.concurrency)
            putInt("timeout_ms", state.timeoutMs)
            putBoolean("grab_banner", state.grabBanner)
            putBoolean("active_checks", state.activeChecks)
            putBoolean("use_arp", state.useArp)
            putBoolean("reverse_dns", state.reverseDns)
            putString("port_preset", state.portPreset)
            putBoolean("attestation", state.attestation)
            putInt("cred_rate", state.credRatePerSec)
            putInt("cred_max_host", state.credMaxPerHost)
            putInt("check_interval", state.checkIntervalSec)
            putBoolean("theme_dark", state.themeDark)
        }.apply()
        _state.value = state
    }

    fun update(transform: (AppPrefs) -> AppPrefs) {
        persist(transform(_state.value))
    }

    fun reset() {
        persist(AppPrefs())
    }
}
