package com.huevoman77.cazahosts.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object CheckerStore {
    private val _watched = MutableStateFlow<List<WatchedHost>>(emptyList())
    val watched: StateFlow<List<WatchedHost>> = _watched

    fun add(ip: String, label: String = "") {
        val clean = ip.trim()
        if (clean.isEmpty()) return
        if (_watched.value.any { it.ip == clean }) return
        _watched.value = _watched.value + WatchedHost(ip = clean, label = label)
    }

    fun addAll(ips: List<String>) {
        ips.forEach { add(it) }
    }

    fun remove(ip: String) {
        _watched.value = _watched.value.filterNot { it.ip == ip }
    }

    fun update(host: WatchedHost) {
        _watched.value = _watched.value.map { if (it.ip == host.ip) host else it }
    }

    fun clear() {
        _watched.value = emptyList()
    }
}
