package com.huevoman77.cazahosts.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Estado compartido de la pantalla de escaneo. Vive fuera de la pantalla para
 * que el objetivo no se pierda al cambiar de pestana y para que otras
 * pantallas (por ejemplo la de subdominios) puedan dejarlo preparado.
 */
object ScannerState {

    private val _target = MutableStateFlow("")
    val target: StateFlow<String> = _target

    private val _scanRequest = MutableStateFlow(0L)
    val scanRequest: StateFlow<Long> = _scanRequest

    private var handled = 0L

    fun setTarget(text: String) {
        _target.value = text
    }

    /** Pide un escaneo inmediato del objetivo actual. */
    fun requestScan() {
        _scanRequest.value = System.currentTimeMillis()
    }

    /**
     * Devuelve la peticion de escaneo pendiente y la marca como atendida, para
     * que solo la primera pantalla que aparece la ejecute.
     */
    fun claimScanRequest(): Long {
        val request = _scanRequest.value
        if (request != handled) {
            handled = request
            return request
        }
        return 0L
    }
}
